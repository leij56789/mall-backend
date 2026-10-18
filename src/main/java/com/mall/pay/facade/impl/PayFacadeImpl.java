package com.mall.pay.facade.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.common.BusinessException;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.entity.PaymentOrder;
import com.mall.pay.entity.PaymentRefundNotifyLog;
import com.mall.pay.entity.PaymentRefundRecord;
import com.mall.enums.ResultCode;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.pay.mapper.PaymentRefundNotifyLogMapper;
import com.mall.pay.mapper.PaymentRefundRecordMapper;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.dto.ThirdPartyPayRequest;
import com.mall.pay.dto.ThirdPartyPayResponse;
import com.mall.pay.facade.PayFacade;
import com.mall.pay.facade.dto.PayOrderDTO;
import com.mall.pay.facade.dto.PayQueryResult;
import com.mall.pay.facade.dto.PayResult;
import com.mall.pay.facade.dto.RefundDTO;
import com.mall.pay.facade.dto.RefundResult;
import com.mall.pay.job.RefundNotifyScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PayFacade 实现。
 * <p>
 * 与系统 A 的 PaymentOrderServiceImpl 并存，供系统 B 使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayFacadeImpl implements PayFacade {

    private final PayClientFactory payClientFactory;
    private final PaymentOrderMapper paymentOrderMapper;
    private final RedissonClient redissonClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final PaymentRefundRecordMapper paymentRefundRecordMapper;   // ★ 新增
    private final PaymentRefundNotifyLogMapper refundNotifyLogMapper;   // 新增
    private final RefundNotifyScheduler refundNotifyScheduler;          // 新增

    // 支付单默认过期时间（分钟）
    private static final int DEFAULT_EXPIRE_MINUTES = 15;

    // ==================== 支付 ====================

    @Override
    public PayResult pay(PayOrderDTO dto) {
        // 1. 参数校验
        if (dto == null || dto.getBizType() == null || dto.getBizId() == null
                || dto.getUserId() == null                          // ★ 新增
                || dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || dto.getPaymentMethod() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }

        // 2. 加锁（防重复点击）
        String lockKey = "payment:create:" + dto.getBizType() + ":" + dto.getBizId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, 10, TimeUnit.SECONDS)) {
                log.warn("获取支付锁失败，重复请求: bizType={}, bizId={}",
                        dto.getBizType(), dto.getBizId());
                throw new BusinessException(ResultCode.REPEAT_CLICK);
            }

            // 3. 查"未完成"的支付单（INIT / WAITING）
            PaymentOrder existing = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getBizType, dto.getBizType())
                            .eq(PaymentOrder::getBizId, dto.getBizId())
                            .in(PaymentOrder::getStatus, List.of("INIT", "WAITING"))
                            .orderByDesc(PaymentOrder::getId)
                            .last("LIMIT 1")
            );

            PaymentOrder po;
            if (existing != null) {
                log.info("复用已有支付单: paymentId={}, status={}, bizType={}, bizId={}",
                        existing.getPaymentId(), existing.getStatus(),
                        dto.getBizType(), dto.getBizId());
                po = existing;
            } else {
                // 4. 创建新支付单
                po = createNewPaymentOrder(dto);
                log.info("创建新支付单: paymentId={}, bizType={}, bizId={}",
                        po.getPaymentId(), dto.getBizType(), dto.getBizId());
            }

            // 5. 调第三方下单
            PayClient payClient = payClientFactory.getPayClient(dto.getPaymentMethod());

            ThirdPartyPayRequest thirdReq = ThirdPartyPayRequest.builder()
                    .outTradeNo(po.getPaymentId())
                    .userId(po.getUserId())
                    .totalAmount(po.getAmount().toString())
                    .subject(dto.getSubject() != null ? dto.getSubject() : "支付")
                    .timeExpire(po.getExpiredAt())
                    .returnUrl(dto.getReturnUrl())
                    .build();

            ThirdPartyPayResponse thirdResp;
            try {
                thirdResp = payClient.unifiedOrder(thirdReq);
            } catch (BusinessException e) {
                // 第三方下单失败：转 FAILED
                paymentOrderMapper.update(null,
                        new LambdaUpdateWrapper<PaymentOrder>()
                                .set(PaymentOrder::getStatus, "FAILED")
                                .eq(PaymentOrder::getId, po.getId())
                                .eq(PaymentOrder::getStatus, po.getStatus()));
                throw e;
            }

            if (thirdResp == null || !thirdResp.isSuccess()) {
                paymentOrderMapper.update(null,
                        new LambdaUpdateWrapper<PaymentOrder>()
                                .set(PaymentOrder::getStatus, "FAILED")
                                .eq(PaymentOrder::getId, po.getId())
                                .eq(PaymentOrder::getStatus, po.getStatus()));
                throw new BusinessException(ResultCode.PAYMENT_RESPONSE_ERROR);
            }

            // 6. 更新状态为 WAITING
            paymentOrderMapper.update(null,
                    new LambdaUpdateWrapper<PaymentOrder>()
                            .set(PaymentOrder::getStatus, "WAITING")
                            .set(PaymentOrder::getPrepayId, thirdResp.getPrepayId())
                            .set(PaymentOrder::getExtInfo, thirdResp.getExtInfo())
                            .eq(PaymentOrder::getId, po.getId())
                            .eq(PaymentOrder::getStatus, "INIT"));

            // 7. 返回
            return PayResult.builder()
                    .paymentId(po.getPaymentId())
                    .status("WAITING")
                    .payUrl(thirdResp.getPayParams())
                    .prepayId(thirdResp.getPrepayId())
                    .expireAt(po.getExpiredAt())
                    .extInfo(thirdResp.getExtInfo())
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public PayQueryResult query(String paymentId) {
        if (paymentId == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }

        PaymentOrder po = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getPaymentId, paymentId)
        );
        if (po == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        return PayQueryResult.builder()
                             .paymentId(po.getPaymentId())
                             .bizType(po.getBizType())
                             .bizId(po.getBizId())
                             .amount(po.getAmount())
                             .paymentMethod(po.getPaymentMethod())
                             .status(po.getStatus())
                             .statusDesc(com.mall.enums.PaymentStatus.getDescByCode(po.getStatus()))
                             .thirdPartyTradeNo(po.getThirdPartyTradeNo())
                             .paidAt(po.getCallbackTime())
                             .expireAt(po.getExpiredAt())
                             .notifyStatus(po.getNotifyStatus())              // ★ 新增
                             .notifyRetryCount(po.getNotifyRetryCount())      // ★ 新增
                             .notifyNextTime(po.getNotifyNextTime())          // ★ 新增
                             .build();
    }

    @Override
    public void close(String paymentId) {
        if (paymentId == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }

        PaymentOrder po = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getPaymentId, paymentId)
        );
        if (po == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 只有 WAITING 可以关闭
        if (!"WAITING".equals(po.getStatus())) {
            log.warn("支付单状态不是 WAITING，跳过关单: paymentId={}, status={}",
                    paymentId, po.getStatus());
            return;
        }

        // 调第三方关单（可选，失败不阻塞）
        try {
            PayClient payClient = payClientFactory.getPayClient(po.getPaymentMethod());
            payClient.closeOrder(paymentId);
        } catch (Exception e) {
            log.warn("第三方关单失败（不影响本地状态）: paymentId={}", paymentId, e);
        }

        // 更新本地状态
        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .set(PaymentOrder::getStatus, "CLOSED")
                        .eq(PaymentOrder::getPaymentId, paymentId)
                        .eq(PaymentOrder::getStatus, "WAITING"));
    }

    // ==================== 退款（暂未实现，先抛异常） ====================

    // ==================== 退款 ====================

    @Override
    public RefundResult refund(RefundDTO dto) {
        // 1. 参数校验
        if (dto == null || dto.getPaymentId() == null || dto.getRefundAmount() == null
                || dto.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0
                || dto.getOutRequestNo() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }

        // 2. 加锁（防并发退款）
        String lockKey = "payment:refund:" + dto.getOutRequestNo();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.REPEAT_CLICK, "退款请求正在处理中");
            }

            // 3. 幂等：查已有退款单
            PaymentRefundRecord existing = paymentRefundRecordMapper.selectOne(
                    new LambdaQueryWrapper<PaymentRefundRecord>()
                            .eq(PaymentRefundRecord::getOutRequestNo, dto.getOutRequestNo())
            );
            if (existing != null) {
                log.info("退款单已存在，幂等返回: outRequestNo={}, status={}",
                        dto.getOutRequestNo(), existing.getStatus());
                return buildRefundResult(existing);
            }

            // 4. 查支付单
            PaymentOrder po = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getPaymentId, dto.getPaymentId())
            );
            if (po == null) {
                throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
            }
            if (!"SUCCESS".equals(po.getStatus()) && !"PARTIAL_REFUNDED".equals(po.getStatus())) {
                throw new BusinessException(ResultCode.PAYMENT_STATUS_INVALID, "支付单状态不支持退款: " + po.getStatus());
            }

            // 5. 校验累计退款金额
            BigDecimal refundedSoFar = po.getRefundedAmount() != null ? po.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal totalAfter = refundedSoFar.add(dto.getRefundAmount());
            if (totalAfter.compareTo(po.getAmount()) > 0) {
                throw new BusinessException(ResultCode.PAYMENT_AMOUNT_MISMATCH,
                        String.format("退款金额超过可退: 已退=%s, 本次=%s, 总额=%s",
                                refundedSoFar, dto.getRefundAmount(), po.getAmount()));
            }

            // 6. 【事务外】先写 PROCESSING 退款记录（立即提交，兜底）
            PaymentRefundRecord record = PaymentRefundRecord.builder()
                                                            .paymentId(po.getPaymentId())
                                                            .outTradeNo(po.getPaymentId())
                                                            .tradeNo(po.getThirdPartyTradeNo())
                                                            .refundAmount(dto.getRefundAmount())
                                                            .refundReason(dto.getRefundReason())
                                                            .outRequestNo(dto.getOutRequestNo())
                                                            .status("PROCESSING")   // ← 先 PROCESSING
                                                            .retryCount(0)
                                                            .nextQueryTime(LocalDateTime.now().plusSeconds(10))  // ← 补偿查的时间
                                                            .operatorId(dto.getOperatorId())
                                                            .clientIp(dto.getClientIp())
                                                            .build();
            paymentRefundRecordMapper.insert(record);

            // 7. 【事务外】调支付宝退款
            PayClient payClient = payClientFactory.getPayClient(po.getPaymentMethod());
            PayClient.RefundRequest refundReq = new PayClient.RefundRequest();
            refundReq.setOutTradeNo(po.getPaymentId());
            refundReq.setRefundAmount(dto.getRefundAmount().toString());
            refundReq.setRefundReason(dto.getRefundReason());
            refundReq.setOutRequestNo(dto.getOutRequestNo());

            PayClient.RefundResponse refundResp;
            try {
                refundResp = payClient.refundOrder(refundReq);
            } catch (Exception e) {
                // 支付宝调用异常：标记 FAILED（但记录还在，补偿会查）
                log.error("支付宝退款调用异常: outRequestNo={}", dto.getOutRequestNo(), e);
                paymentRefundRecordMapper.update(null,
                        new LambdaUpdateWrapper<PaymentRefundRecord>()
                                .set(PaymentRefundRecord::getStatus, "FAILED")
                                .set(PaymentRefundRecord::getFailReason, truncate(e.getMessage(), 500))
                                .eq(PaymentRefundRecord::getId, record.getId()));
                throw e;
            }

            // 8. 【事务外】根据支付宝返回更新
            String finalStatus = mapRefundStatus(refundResp.getResult());

            if ("SUCCESS".equals(finalStatus)) {
                // 8a. 更新退款记录为 SUCCESS
                paymentRefundRecordMapper.update(null,
                        new LambdaUpdateWrapper<PaymentRefundRecord>()
                                .set(PaymentRefundRecord::getStatus, "SUCCESS")
                                .set(PaymentRefundRecord::getThirdPartyRefundNo, refundResp.getTradeNo())
                                .eq(PaymentRefundRecord::getId, record.getId()));

                // 8b. 更新支付单（CAS）
                boolean fullRefund = totalAfter.compareTo(po.getAmount()) == 0;
                int updated = paymentOrderMapper.update(null,
                        new LambdaUpdateWrapper<PaymentOrder>()
                                .set(PaymentOrder::getRefundedAmount, totalAfter)
                                .set(PaymentOrder::getStatus, fullRefund ? "REFUND" : "PARTIAL_REFUNDED")
                                .set(PaymentOrder::getRefundTime, LocalDateTime.now())
                                .eq(PaymentOrder::getId, po.getId())
                                .eq(PaymentOrder::getRefundedAmount, refundedSoFar));   // CAS

                if (updated != 1) {
                    log.error("并发退款冲突（支付单更新失败）: paymentId={}, expectedRefunded={}",
                            dto.getPaymentId(), refundedSoFar);
                    // 退款记录已 SUCCESS，但支付单更新失败 —— 补偿任务会查
                    throw new BusinessException(ResultCode.PAYMENT_REFUND_FAIL, "并发退款冲突，请重试");
                }

            } else if ("FAILED".equals(finalStatus)) {
                paymentRefundRecordMapper.update(null,
                        new LambdaUpdateWrapper<PaymentRefundRecord>()
                                .set(PaymentRefundRecord::getStatus, "FAILED")
                                .set(PaymentRefundRecord::getFailReason, refundResp.getFailReason())
                                .eq(PaymentRefundRecord::getId, record.getId()));
            }
            // PROCESSING 状态：保持，补偿任务会查

            // 9. 【事务外】写通知日志
            String notifyType = "SUCCESS".equals(finalStatus) ? "SUCCESS" : ("FAILED".equals(finalStatus) ? "FAILED" : null);
            if (notifyType != null) {
                try {
                    PaymentRefundNotifyLog notifyLog = PaymentRefundNotifyLog.builder()
                                                                             .refundId(record.getId())
                                                                             .outRequestNo(record.getOutRequestNo())
                                                                             .paymentId(record.getPaymentId())
                                                                             .notifyType(notifyType)
                                                                             .status("PENDING")
                                                                             .retryCount(0)
                                                                             .nextNotifyTime(LocalDateTime.now())
                                                                             .build();
                    refundNotifyLogMapper.insert(notifyLog);

                    // 立即触发通知
                    refundNotifyScheduler.notifyImmediately(notifyLog.getId());
                } catch (Exception e) {
                    log.warn("写通知日志失败，等待补偿: outRequestNo={}", dto.getOutRequestNo(), e);
                }
            }

            // 10. 返回
            record.setStatus(finalStatus != null ? finalStatus : "PROCESSING");
            record.setThirdPartyRefundNo(refundResp.getTradeNo());
            record.setFailReason(refundResp.getFailReason());
            return buildRefundResult(record);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    @Override
    public RefundResult queryRefund(String outRequestNo) {
        if (outRequestNo == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        PaymentRefundRecord record = paymentRefundRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRefundRecord>()
                        .eq(PaymentRefundRecord::getOutRequestNo, outRequestNo)
        );
        if (record == null) {
            throw new BusinessException(ResultCode.PAYMENT_REFUND_NOT_FOUND);
        }
        return buildRefundResult(record);
    }

    @Override
    public List<RefundResult> listRefunds(String paymentId) {
        if (paymentId == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        List<PaymentRefundRecord> records = paymentRefundRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRefundRecord>()
                        .eq(PaymentRefundRecord::getPaymentId, paymentId)
                        .orderByDesc(PaymentRefundRecord::getId)
        );
        return records.stream().map(this::buildRefundResult).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    private PaymentOrder createNewPaymentOrder(PayOrderDTO dto) {
        String paymentId = String.valueOf(snowflakeIdGenerator.nextId());
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(DEFAULT_EXPIRE_MINUTES);

        PaymentOrder po = PaymentOrder.builder()
                                      .paymentId(paymentId)
                                      .bizType(dto.getBizType())
                                      .bizId(dto.getBizId())
                                      .userId(dto.getUserId())           // ★ 新增
                                      .amount(dto.getAmount())
                                      .paymentMethod(dto.getPaymentMethod())
                                      .subject(dto.getSubject())
                                      .description(dto.getDescription())
                                      .status("INIT")
                                      .expiredAt(expireAt)
                                      .retryCount(0)
                                      .build();

        paymentOrderMapper.insert(po);
        return po;
    }

    private String mapRefundStatus(PayClient.RefundResult result) {
        if (result == null) return "PROCESSING";
        return switch (result) {
            case SUCCESS -> "SUCCESS";
            case FAILED -> "FAILED";
            case PROCESSING -> "PROCESSING";
        };
    }

    private RefundResult buildRefundResult(PaymentRefundRecord record) {
        return RefundResult.builder()
                           .refundId(record.getId())
                           .paymentId(record.getPaymentId())
                           .outRequestNo(record.getOutRequestNo())
                           .refundAmount(record.getRefundAmount())
                           .status(record.getStatus())
                           .failReason(record.getFailReason())
                           .thirdPartyRefundNo(record.getThirdPartyRefundNo())
                           .build();
    }
}
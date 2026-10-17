package com.mall.pay.facade.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.common.BusinessException;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.entity.PaymentOrder;
import com.mall.enums.ResultCode;
import com.mall.mapper.PaymentOrderMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Override
    public RefundResult refund(RefundDTO dto) {
        throw new UnsupportedOperationException("退款功能待实现（Step 3c）");
    }

    @Override
    public RefundResult queryRefund(String outRequestNo) {
        throw new UnsupportedOperationException("退款查询功能待实现（Step 3c）");
    }

    @Override
    public List<RefundResult> listRefunds(String paymentId) {
        throw new UnsupportedOperationException("退款列表功能待实现（Step 3c）");
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
}
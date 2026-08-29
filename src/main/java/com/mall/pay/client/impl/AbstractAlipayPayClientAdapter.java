package com.mall.pay.client.impl;

import cn.hutool.core.util.StrUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.enums.ResultCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.PayProperties;
import com.mall.pay.dto.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAlipayPayClientAdapter implements PayClient {
    protected final AlipayClient alipayClient;
//    protected final AlipayProperties properties;
    protected final PayProperties properties;
    // 子类可以通过 payProperties.getAlipay() 获取支付宝配置
    protected PayProperties.AlipayProperties getAlipayConfig() {
        return properties.getAlipay();
    }

    // 所有支付宝适配器共享的方法
    @Override
    public QueryOrderResponse queryOrder(QueryOrderRequest request) {
        // 通用的 alipay.trade.query 实现
        try {
            // 1. 构建支付宝查询请求
            AlipayTradeQueryRequest alipayRequest = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(request.getPaymentId()); // 商户订单号
            alipayRequest.setBizModel(model);

            // 2. 执行查询
            AlipayTradeQueryResponse response = alipayClient.execute(alipayRequest);

            // 3. 处理业务成功
            if (response.isSuccess()) {
                return QueryOrderResponse.builder()
                                         .success(true)
                                         .tradeState(response.getTradeStatus())          // 支付宝交易状态
                                         .transactionId(response.getTradeNo())           // 支付宝交易号
                                         .totalAmount(response.getTotalAmount())
                                         .extInfo(null)
                                         .build();
            }

            // 4. 业务失败：根据 subCode 映射到不同的 ResultCode
            String subCode = response.getSubCode();
            String subMsg = response.getSubMsg();
            ResultCode resultCode = mapAlipaySubCodeToResultCode(subCode);

            log.error("支付宝查询业务失败: paymentId={}, subCode={}, subMsg={}",
                    request.getPaymentId(), subCode, subMsg);

            // 抛出携带详细信息的业务异常
            throw new BusinessException(resultCode,
                    String.format("支付宝查询失败: subCode=%s, subMsg=%s", subCode, subMsg));

        } catch (AlipayApiException e) {
            // 5. 网络层异常：区分超时/连接异常（可重试）和其他系统异常
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                log.warn("支付宝查询网络超时/连接异常: paymentId={}", request.getPaymentId(), e);
                throw new BusinessException(ResultCode.THIRD_PARTY_TIMEOUT,
                        "支付宝查询超时: " + e.getMessage());
            }

            // 其他 AlipayApiException（例如签名错误、参数校验失败等）
            log.error("支付宝查询系统异常: paymentId={}", request.getPaymentId(), e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR,
                    "支付宝查询异常: " + e.getMessage());

        } catch (Exception e) {
            // 6. 兜底异常（理论上不会进入，但保留以防 SDK 改变）
            if(e instanceof BusinessException){
                throw (BusinessException)e;
            }
            log.error("支付宝查询未知异常: paymentId={}", request.getPaymentId(), e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR,
                    "支付宝查询未知异常: " + e.getMessage());
        }
    }

    @Override
    public boolean closeOrder(String paymentId) {
        // 通用的 alipay.trade.close 实现
        try {
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(paymentId);
            // 可选：操作员 ID，如有配置可设置
//            if (StringUtils.hasText(properties.getOperatorId())) {
//                model.setOperatorId(properties.getOperatorId());
//            }
            request.setBizModel(model);

            AlipayTradeCloseResponse response = alipayClient.execute(request);

            // ========== 成功 ==========
            if (response.isSuccess()) {
                log.info("支付宝关单成功: paymentId={}, tradeNo={}",
                        paymentId, response.getTradeNo());
                return true;
            }

            // ========== 业务失败 ==========
            String subCode = response.getSubCode();
            String subMsg = response.getSubMsg();
            log.warn("支付宝关单业务失败: paymentId={}, subCode={}, subMsg={}",
                    paymentId, subCode, subMsg);

            // -------- 幂等成功（视为关单已完成） --------
            if ("ACQ.TRADE_NOT_EXIST".equals(subCode)) {
                log.info("关单幂等：交易不存在，视为成功: paymentId={}", paymentId);
                return true;
            }

            // -------- 交易状态异常（需查单确认） --------
            if ("ACQ.REASON_ILLEGAL_STATUS".equals(subCode)
                    || "ACQ.REASON_TRADE_STATUS_INVALID".equals(subCode)
                    || "ACQ.TRADE_STATUS_ERROR".equals(subCode)) {
                log.error("关单异常：交易状态非法，需查单确认。paymentId={}, subCode={}",
                        paymentId, subCode);
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR,
                        "关单失败：交易状态异常，需查单确认。subCode=" + subCode);
            }

            // -------- 系统异常（可重试） --------
            if ("ACQ.SYSTEM_ERROR".equals(subCode)) {
                log.warn("关单系统异常，可重试: paymentId={}", paymentId);
                throw new BusinessException(ResultCode.THIRD_PARTY_TIMEOUT,
                        "关单系统异常: " + subMsg);
            }

            // -------- 参数错误（不可重试） --------
            if ("ACQ.INVALID_PARAMETER".equals(subCode)) {
                log.error("关单参数错误: paymentId={}, subMsg={}", paymentId, subMsg);
                throw new BusinessException(ResultCode.THIRD_PARTY_FATAL_ERROR,
                        "关单参数错误: " + subMsg);
            }

            // -------- 其他未知错误 --------
            log.error("关单未知业务错误: paymentId={}, subCode={}, subMsg={}",
                    paymentId, subCode, subMsg);
            return false;

        } catch (AlipayApiException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                log.warn("支付宝关单网络超时: paymentId={}", paymentId, e);
                throw new BusinessException(ResultCode.THIRD_PARTY_TIMEOUT,
                        "关单超时: " + e.getMessage());
            }
            log.error("支付宝关单异常: paymentId={}", paymentId, e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR,
                    "关单异常: " + e.getMessage());
        }
    }

    @Override
    public abstract String mapTradeStatusAfterQueryOrderOnAsyncQueryService(String alipayStatus);

    @Override
    public String mapTradeStatusAfterQueryOrderOnPaymentCompensateJob(String alipayStatus) {
        // 通用的状态映射
        if (!StringUtils.hasText(alipayStatus)) return "UNKNOWN";
        switch (alipayStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return "SUCCESS";
            case "TRADE_CLOSED":
                return "FAILED";
            default:
                return alipayStatus;
        }
    }

    // 抽象方法：由子类实现各自的预下单逻辑
    @Override
    public abstract ThirdPartyPayResponse unifiedOrder(ThirdPartyPayRequest request);

    // 子类可重写
    @Override
    public abstract boolean canRecoverFromPendingConfirm();

    // 通用的错误码映射

    private ResultCode mapAlipaySubCodeToResultCode(String subCode){
        if (subCode == null) {
            return ResultCode.THIRD_PARTY_UNKNOWN_ERROR;
        }

        switch (subCode) {
            // 可重试错误（临时性）
            case "ACQ.TRADE_NOT_EXIST":
            case "ACQ.SYSTEM_ERROR":
                return ResultCode.THIRD_PARTY_RETRYABLE_ERROR;

            // 不可恢复错误（参数问题，无需重试）
            case "ACQ.INVALID_PARAMETER":
                return ResultCode.THIRD_PARTY_FATAL_ERROR;

            // 需人工介入（业务配置/限制问题）
            case "ACQ.ENTERPRISE_PAY_BIZ_ERROR":
                return ResultCode.THIRD_PARTY_MANUAL_INTERVENTION;

            // 其他未知错误：保守处理，视为不可重试（或按需调整）
            default:
                log.warn("未知的支付宝错误码: subCode={}, 按不可重试处理", subCode);
                return ResultCode.THIRD_PARTY_UNKNOWN_ERROR;
        }
    }
    @Log("退款调用第三方接口refundOrder")
    @Override
    public RefundResponse refundOrder(RefundRequest request) {
        log.info("支付宝退款请求开始: outTradeNo={}, tradeNo={}, refundAmount={}, outRequestNo={}",
                request.getOutTradeNo(), request.getTradeNo(),
                request.getRefundAmount(), request.getOutRequestNo());

        try {
            // ===== 1. 构建请求 =====
            AlipayTradeRefundRequest alipayRequest = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();

            // -------- 必填参数 --------
            if (StrUtil.isBlank(request.getRefundAmount())) {
                log.error("退款金额为空");
                throw new BusinessException(ResultCode.PARAM_MISSING, "退款金额不能为空");
            }
            model.setRefundAmount(request.getRefundAmount());

            if (StrUtil.isNotBlank(request.getTradeNo())) {
                model.setTradeNo(request.getTradeNo());
            } else if (StrUtil.isNotBlank(request.getOutTradeNo())) {
                model.setOutTradeNo(request.getOutTradeNo());
            } else {
                log.error("商户订单号和支付宝交易号都为空");
                throw new BusinessException(ResultCode.PARAM_MISSING, "商户订单号和支付宝交易号至少传入一个");
            }

            // -------- 可选参数 --------
            if (StrUtil.isNotBlank(request.getRefundReason())) {
                model.setRefundReason(request.getRefundReason());
            }
            if (StrUtil.isNotBlank(request.getOutRequestNo())) {
                model.setOutRequestNo(request.getOutRequestNo());
            }
            if (request.getRefundGoodsDetail() != null && !request.getRefundGoodsDetail().isEmpty()) {
                model.setRefundGoodsDetail(request.getRefundGoodsDetail());
            }
            if (request.getRefundRoyaltyParameters() != null && !request.getRefundRoyaltyParameters().isEmpty()) {
                model.setRefundRoyaltyParameters(request.getRefundRoyaltyParameters());
            }
            if (request.getQueryOptions() != null && !request.getQueryOptions().isEmpty()) {
                model.setQueryOptions(request.getQueryOptions());
            }

            alipayRequest.setBizModel(model);

            log.debug("支付宝退款请求参数: outTradeNo={}, refundAmount={}, outRequestNo={}",
                    request.getOutTradeNo(), request.getRefundAmount(), request.getOutRequestNo());

            // ===== 2. 执行退款请求 =====
            AlipayTradeRefundResponse response = alipayClient.execute(alipayRequest);

            // ===== 3. 处理响应 =====
            if (response.isSuccess()) {
                String fundChange = response.getFundChange();
                log.info("支付宝退款请求成功: outTradeNo={}, tradeNo={}, refundAmount={}, fundChange={}",
                        response.getOutTradeNo(), response.getTradeNo(),
                        request.getRefundAmount(), fundChange);

                // 转换资金渠道明细
                List<TradeFundBill> detailList = convertRefundDetailList(response.getRefundDetailItemList());

                RefundResult result;
                String failReason = null;

                if ("Y".equals(fundChange)) {
                    // ✅ 退款成功
                    result = RefundResult.SUCCESS;
                    log.info("退款成功: outTradeNo={}, refundAmount={}",
                            response.getOutTradeNo(), request.getRefundAmount());
                } else if ("N".equals(fundChange) || fundChange == null) {
                    // ⚠️ 未发生资金变化，需要查询确认
                    result = RefundResult.PROCESSING;
                    failReason = "未发生资金变化，需调用退款查询确认";
                    log.warn("退款状态不确定: outTradeNo={}, fundChange={}, 需调用退款查询确认",
                            response.getOutTradeNo(), fundChange);
                } else {
                    // 未知 fundChange 值（理论上不会出现）
                    result = RefundResult.PROCESSING;
                    failReason = "退款结果不确定，fundChange=" + fundChange;
                    log.warn("退款fundChange值未知: outTradeNo={}, fundChange={}",
                            response.getOutTradeNo(), fundChange);
                }

                return RefundResponse.builder()
                                     .outTradeNo(response.getOutTradeNo())
                                     .tradeNo(response.getTradeNo())
                                     .refundAmount(request.getRefundAmount())
                                     .result(result)
                                     .failReason(failReason)
                                     .refundDetailItemList(detailList)
                                     .build();
            }

            // ===== 4. 业务失败 =====
            String subCode = response.getSubCode();
            String subMsg = response.getSubMsg();
            log.error("支付宝退款业务失败: outTradeNo={}, tradeNo={}, subCode={}, subMsg={}",
                    request.getOutTradeNo(), request.getTradeNo(), subCode, subMsg);

            RefundResult result = mapRefundErrorToResult(subCode);
            String failReason = "退款失败: " + subMsg;

            return RefundResponse.builder()
                                 .outTradeNo(request.getOutTradeNo())
                                 .tradeNo(request.getTradeNo())
                                 .refundAmount(request.getRefundAmount())
                                 .result(result)
                                 .failReason(failReason)
                                 .build();

        } catch (AlipayApiException e) {
            // ===== 5. 支付宝 SDK 异常 =====
            // ⚠️ 异常信息不吞掉，完整记录堆栈
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                log.error("支付宝退款网络超时: outTradeNo={}, 异常详情: ",
                        request.getOutTradeNo(), e);
                return RefundResponse.builder()
                                     .outTradeNo(request.getOutTradeNo())
                                     .tradeNo(request.getTradeNo())
                                     .refundAmount(request.getRefundAmount())
                                     .result(RefundResult.PROCESSING)
                                     .failReason("退款请求超时，需调用退款查询确认")
                                     .build();
            }
            log.error("支付宝退款SDK异常: outTradeNo={}, 异常详情: ",
                    request.getOutTradeNo(), e);
            return RefundResponse.builder()
                                 .outTradeNo(request.getOutTradeNo())
                                 .tradeNo(request.getTradeNo())
                                 .refundAmount(request.getRefundAmount())
                                 .result(RefundResult.PROCESSING)
                                 .failReason("退款请求异常: " + e.getMessage())
                                 .build();

        } catch (BusinessException e) {
            // ===== 6. 业务异常 =====
            log.error("支付宝退款业务异常: outTradeNo={}, code={}, message={}",
                    request.getOutTradeNo(), e.getCode(), e.getMessage(), e);
            throw e;

        } catch (Exception e) {
            // ===== 7. 未知异常 =====
            log.error("支付宝退款未知异常: outTradeNo={}, 异常详情: ",
                    request.getOutTradeNo(), e);
            return RefundResponse.builder()
                                 .outTradeNo(request.getOutTradeNo())
                                 .tradeNo(request.getTradeNo())
                                 .refundAmount(request.getRefundAmount())
                                 .result(RefundResult.PROCESSING)
                                 .failReason("退款异常: " + e.getMessage())
                                 .build();
        }
    }

    /**
     * 将支付宝 SDK 的 TradeFundBill 转换为业务层 TradeFundBill
     */
    protected List<TradeFundBill> convertRefundDetailList(
            List<com.alipay.api.domain.TradeFundBill> alipayList) {
        if (alipayList == null || alipayList.isEmpty()) {
            return null;
        }

        List<TradeFundBill> resultList = new ArrayList<TradeFundBill>();
        for (com.alipay.api.domain.TradeFundBill bill : alipayList) {
            TradeFundBill result = TradeFundBill.builder()
                                                .fundChannel(bill.getFundChannel())
                                                .amount(bill.getAmount())
                                                .realAmount(bill.getRealAmount())
                                                .fundType(bill.getFundType())
                                                .build();
            resultList.add(result);
        }
        return resultList;
    }


    /**
     * 将支付宝错误码映射为统一退款结果
     */
    private RefundResult mapRefundErrorToResult(String subCode) {
        if (subCode == null) {
            return RefundResult.PROCESSING;
        }

        log.debug("退款错误码映射: subCode={}", subCode);

        switch (subCode) {
            // ===== 明确失败（参数错误、业务不允许等） =====
            case "ACQ.INVALID_PARAMETER":
            case "ACQ.REASON_TRADE_REFUND_FEE_ERR":
            case "ACQ.REFUND_FEE_ERROR":
            case "ACQ.REFUND_AMT_NOT_EQUAL_TOTAL":
            case "ACQ.NOT_ALLOW_PARTIAL_REFUND":
            case "ACQ.ONLINE_TRADE_VOUCHER_NOT_ALLOW_REFUND":
            case "ACQ.TRADE_HAS_FINISHED":
            case "ACQ.TRADE_HAS_CLOSE":
            case "ACQ.TRADE_STATUS_ERROR":
            case "ACQ.TRADE_NOT_EXIST":
            case "ACQ.SELLER_BALANCE_NOT_ENOUGH":
            case "ACQ.REFUNDALLOC_UNAUTH_LIMIT":
            case "ACQ.REFUND_ROYALTY_PAYEE_ACCOUNT_NOT_EXIST":
            case "ACQ.DISCORDANT_REPEAT_REQUEST":
                return RefundResult.FAILED;

            // ===== 需查询确认（系统异常、网络抖动） =====
            case "ACQ.SYSTEM_ERROR":
            case "ACQ.REFUND_CHARGE_ERROR":
            case "ACQ.TRADE_HAS_SUCCESS":
                return RefundResult.PROCESSING;

            // ===== 需人工介入（保守处理，也返回 PROCESSING） =====
            case "ACQ.BUYER_ENABLE_STATUS_FORBID":
            case "ACQ.BUYER_ERROR":
            case "ACQ.BUYER_NOT_EXIST":
            case "ACQ.CUSTOMER_VALIDATE_ERROR":
            case "ACQ.REASON_TRADE_BEEN_FREEZEN":
                return RefundResult.PROCESSING;

            default:
                // 未知错误码，保守处理
                log.warn("未知退款错误码: subCode={}", subCode);
                return RefundResult.PROCESSING;
        }
    }
}
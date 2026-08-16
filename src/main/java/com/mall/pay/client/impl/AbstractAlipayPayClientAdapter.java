package com.mall.pay.client.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.mall.common.BusinessException;
import com.mall.enums.ResultCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.AlipayProperties;
import com.mall.pay.dto.QueryOrderRequest;
import com.mall.pay.dto.QueryOrderResponse;
import com.mall.pay.dto.ThirdPartyPayRequest;
import com.mall.pay.dto.ThirdPartyPayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAlipayPayClientAdapter implements PayClient {
    protected final AlipayClient alipayClient;
    protected final AlipayProperties properties;

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
}
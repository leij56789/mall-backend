package com.mall.pay.client.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.enums.AlipayExtKey;
import com.mall.enums.ResultCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.PayProperties;
import com.mall.pay.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

@Slf4j
@RequiredArgsConstructor
public class AlipayF2FPayClientAdapter implements PayClient {
    // ...
    private final AlipayClient alipayClient;
    private final PayProperties payProperties;
    private static final DateTimeFormatter F2F_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");


    @Log("支付宝订单码支付unifiedOrder")
    @Override
    public ThirdPartyPayResponse unifiedOrder(ThirdPartyPayRequest request) {
        try {
            PayProperties.AlipayProperties properties = payProperties.getAlipay();
            AlipayTradePrecreateRequest alipayRequest = new AlipayTradePrecreateRequest();
            alipayRequest.setNotifyUrl(properties.getNotifyUrl());

            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(request.getOutTradeNo());
            model.setTotalAmount(request.getTotalAmount());
            model.setSubject(request.getSubject());
            model.setBody(request.getBody());
            // 使用 DateTimeFormatter 格式化，精确到分钟

            // ===== 关键修复：设置二维码有效期（而不是订单有效期） =====
// 支付宝文档：qr_code_timeout_express 单位为 "h"（小时）或 "m"（分钟），最长 2 小时
// 建议：根据业务需求设置，如 30 分钟 -> "30m"，2 小时 -> "2h"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
//            String qrTimeout = formatQrCodeTimeout(request.getTimeExpire()); // 传入你的业务超时时间
//            model.setQrCodeTimeoutExpress(qrTimeout);  // ✅ 这才是控制二维码过期时间的正确方法
//            String timeFormat = request.getTimeExpire()
//                                   .format(F2F_TIME_FORMATTER);
//            model.setTimeExpire(timeFormat);
            model.setSellerId(properties.getSellerId());
            model.setProductCode("QR_CODE_OFFLINE");
            model.setStoreId("STORE_001");
            model.setOperatorId("OP_001");
            // 超时时间（可选）
            // model.setTimeoutExpress("15m");
            alipayRequest.setBizModel(model);

            AlipayTradePrecreateResponse response = alipayClient.execute(alipayRequest);

            if (response.isSuccess()) {
                // 支付宝预下单成功，返回二维码链接（此处模拟返回支付参数，实际返回 qr_code）
                String qrCode = response.getQrCode();
                if(qrCode==null){
                    log.error("支付宝二维码丢失：paymentId={}",request.getOutTradeNo());
                    throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
                }
                // 构造前端调起支付参数（支付宝没有 prepay_id，直接用 qr_code 或 trade_no）
                String payParams = String.format("{\"qrCode\":\"%s\",\"outTradeNo\":\"%s\"}",
                        qrCode, request.getOutTradeNo());
                HashMap<String, Object> extInfo = new HashMap<>();
                extInfo.put(AlipayExtKey.QR_CODE.getKey(), qrCode);
                return ThirdPartyPayResponse.builder()
                                            .success(true)
                                            .extInfo(extInfo)
                                            .prepayId(null) // 支付宝交易号作为参考
                                            .payParams(payParams)
                                            .build();
            } else {
                log.error("支付宝预下单失败: code={}, msg={}, subCode={}, subMsg={}",
                        response.getCode(), response.getMsg(),
                        response.getSubCode(), response.getSubMsg());
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
            }

        } catch (AlipayApiException e) {
            // 【核心】判断是否为超时异常
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException ||
                    cause instanceof ConnectException) {
                log.warn("支付宝调用超时，结果未定，订单号: {}, 需进入待确认状态", request.getOutTradeNo(), e);
                // 返回一个特殊标识，让上层将订单置为 PENDING_CONFIRM
                throw new BusinessException(ResultCode.THIRD_PARTY_TIMEOUT);
            }
            log.error("支付宝API异常", e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
        }
    }
    /**
     * 将业务超时时间转换为支付宝要求的 qr_code_timeout_express 格式
     * @param expireAt 业务支付单过期时间（LocalDateTime）
     * @return 字符串，如 "30m"（30分钟）、"2h"（2小时），最长 2 小时（120分钟）
     */
    private String formatQrCodeTimeout(LocalDateTime expireAt) {
        if (expireAt == null) {
            return "30m"; // 默认 30 分钟
        }

        long minutes = Duration.between(LocalDateTime.now(), expireAt).toMinutes();
        if (minutes <= 0) {
            log.warn("过期时间已过或不足1分钟，使用默认 15 分钟");
            return "15m";
        }

        // 支付宝限制：最多 2 小时（120 分钟），超出则截断
        if (minutes > 120) {
            log.warn("二维码有效期超过 2 小时，自动截断为 2 小时");
            return "2h";
        }

        // 如果 >= 60 分钟，按小时显示（如 90 分钟 -> "1.5h" 但支付宝不支持小数，需向下取整）
        if (minutes >= 60) {
            long hours = minutes / 60;
            return hours + "h"; // 只取整数小时，多余分钟被忽略（支付宝自动取整）
        }

        return minutes + "m";
    }

    @Override
    public QueryOrderResponse queryOrder(QueryOrderRequest request) {
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

    /**
     * 映射支付宝业务错误码到内部 ResultCode
     */
    private ResultCode mapAlipaySubCodeToResultCode(String subCode) {
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

    /**
     * 将支付宝交易状态映射为内部统一状态
     */
    public String mapTradeStatusAfterQueryOrderOnAsyncQueryService(String alipayStatus) {
        if (!StringUtils.hasText(alipayStatus)) return "UNKNOWN";
        switch (alipayStatus) {
            case "WAIT_BUYER_PAY":
                return "WAITING";
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return "SUCCESS";
            case "TRADE_CLOSED":
                return "FAILED";
            default:
                log.error("支付宝支付状态异常: {}", alipayStatus);
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
        }
    }
    public String mapTradeStatusAfterQueryOrderOnPaymentCompensateJob(String alipayStatus) {
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
    //    public void updatePaymentStatusToWaitingFromStatusByProcessExtInfo(){
//        new LambdaUpdateWrapper<>()
//    }
    public String mapTradeStatusAfterUnifiedOrder(String alipayStatus) {
        if (!StringUtils.hasText(alipayStatus)) return "UNKNOWN";
        switch (alipayStatus) {
            case "WAIT_BUYER_PAY":
                return "FAILED";
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return "SUCCESS";
            case "TRADE_CLOSED":
                return "FAILED";
            default:
                log.error("支付宝支付状态异常: {}", alipayStatus);
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
        }
    }

    @Override
    public boolean canRecoverFromPendingConfirm() {
        return false;
    }

    @Override
    public boolean closeOrder(String paymentId) {
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
    public boolean canRecreatePaymentForm() {
        return PayClient.super.canRecreatePaymentForm();
    }

    @Override
    public RefundResponse refundOrder(RefundRequest request) {
        return null;
    }


}
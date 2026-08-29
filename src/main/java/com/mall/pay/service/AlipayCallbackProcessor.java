package com.mall.pay.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.mall.entity.PaymentOrder;
import com.mall.enums.PaymentChannel;
import com.mall.enums.PaymentStatus;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.pay.config.PayProperties;
import com.mall.pay.dto.PaymentCallbackRequest;
import com.mall.pay.dto.PaymentCallbackResponse;
import com.mall.service.AlertService;
import com.mall.service.PaymentOrderService;
import com.mall.service.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayCallbackProcessor implements PaymentCallbackProcessor {

    private final PaymentOrderService paymentOrderService;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PayProperties payProperties;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final AlertService alertService;

    private static final String CHANNEL = PaymentChannel.ALIPAY.getCode();

    @Override
    public String process(String rawBody) {
        return "";
    }

    @Override
    public boolean supports(String channel) {
        return CHANNEL.equalsIgnoreCase(channel);
    }

    @Override
    public String process(Map<String, String> params) {
//        Map<String, String> params = parseAlipayParams(rawBody);
        if (params.isEmpty()) {
            log.error("支付宝回调参数为空");
            return "fail";
        }

//        log.info("支付宝回调参数解析成功，out_trade_no={}, trade_status={}",
//                params.get("out_trade_no"), params.get("trade_status"));

        if (!verifyAlipaySign(params)) {
            log.error("支付宝回调验签失败，out_trade_no={}", params.get("out_trade_no"));
            return "fail";
        }

        if (!validateBusinessParams(params)) {
            log.error("支付宝回调业务校验失败，out_trade_no={}", params.get("out_trade_no"));
            return "fail";
        }

        PaymentCallbackRequest request = buildCallbackRequest(params);
        PaymentCallbackResponse response = paymentOrderService.handleCallback(request);

        return response.isSuccess() ? "success" : "fail";
    }

    private Map<String, String> parseAlipayParams(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return new HashMap<>();
        }

        Map<String, String> result = new HashMap<>();
        try {
            String[] pairs = rawBody.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
                    result.put(key, value);
                }
            }
        } catch (UnsupportedEncodingException e) {
            log.error("解析支付宝回调参数编码异常", e);
        }
        return result;
    }

    private boolean verifyAlipaySign(Map<String, String> params) {
        PayProperties.AlipayProperties alipayProperties = payProperties.getAlipay();
        try {
            return AlipaySignature.rsaCheckV1(
                    params,
                    alipayProperties.getAlipayPublicKey(),
                    StandardCharsets.UTF_8.name(),
                    alipayProperties.getSignType()
            );
        } catch (AlipayApiException e) {
            log.error("支付宝验签异常", e);
            return false;
        }
    }

    private boolean validateBusinessParams(Map<String, String> params) {
        PayProperties.AlipayProperties alipayProperties = payProperties.getAlipay();

        String outTradeNo = params.get("out_trade_no");
        String totalAmountStr = params.get("total_amount");
        String sellerId = params.get("seller_id");
        String appId = params.get("app_id");

        // 1. 校验 out_trade_no 是否存在
        PaymentOrder payment = paymentOrderMapper.selectByPaymentIdForUpdate(outTradeNo);
        if (payment == null) {
            log.error("业务校验失败：out_trade_no 不存在，outTradeNo={}", outTradeNo);
            return false;
        }

        // 2. 校验 total_amount
        if (StringUtils.hasText(totalAmountStr)) {
            BigDecimal callbackAmount = new BigDecimal(totalAmountStr);
            if (callbackAmount.compareTo(payment.getAmount()) != 0) {
                log.error("业务校验失败：金额不一致，callbackAmount={}, orderAmount={}",
                        callbackAmount, payment.getAmount());
                alertService.sendCriticalAlert(
                    "支付金额不一致，需人工核对",
                    String.format("orderId=%s, paymentId=%s, 本地金额=%s, 第三方金额=%s",
                            payment.getOrderId(), outTradeNo, payment.getAmount(), callbackAmount),
                    null
                );
                return false;
            }
        }

        // 3. 校验 seller_id（如果回调中存在）
        if (StringUtils.hasText(sellerId)) {
            // TODO: 根据项目实际配置校验 seller_id
             if (!sellerId.equals(alipayProperties.getSellerId())) { return false; }
        }

        // 4. 校验 app_id
        if (StringUtils.hasText(appId)) {
            if (!appId.equals(alipayProperties.getAppId())) {
                log.error("业务校验失败：app_id 不一致，callbackAppId={}, configAppId={}",
                        appId, alipayProperties.getAppId());
                return false;
            }
        }

        return true;
    }

    private PaymentCallbackRequest buildCallbackRequest(Map<String, String> params) {
        PaymentStatus mapped = mapTradeStatusToPaymentStatus(params.get("trade_status"));
        return PaymentCallbackRequest.builder()
                .paymentId(params.get("out_trade_no"))
                .thirdPartyTradeNo(params.get("trade_no"))
                .totalAmount(new BigDecimal(params.getOrDefault("total_amount", "0")))
                .sign(params.get("sign"))
                .tradeStatus(mapped)
                .notifyId(params.get("notify_id"))
                .channel(CHANNEL)
                .rawData(params)
                .build();
    }
    private PaymentStatus mapTradeStatusToPaymentStatus(String tradeStatus) {
        if (tradeStatus == null) {
            return PaymentStatus.FAILED;
        }

        switch (tradeStatus) {
            // ===== 支付成功 =====
            case "TRADE_SUCCESS":   // 交易支付成功
            case "TRADE_FINISHED":  // 交易完结（不可退款，等同于成功）
                return PaymentStatus.SUCCESS;

            // ===== 退款 =====
            case "REFUND":          // 交易退款（部分或全额）
                return PaymentStatus.REFUND;

            // ===== 关闭/失败 =====
            case "TRADE_CLOSED":    // 交易关闭（未支付，超时关闭或用户主动关闭）
                return PaymentStatus.FAILED;

            // ===== 进行中 =====
            case "WAIT_BUYER_PAY":  // 等待买家付款
                return PaymentStatus.WAITING;

            // ===== 未知状态 =====
            default:
                log.warn("未知支付宝交易状态: {}", tradeStatus);
                return PaymentStatus.FAILED;
        }
    }
}
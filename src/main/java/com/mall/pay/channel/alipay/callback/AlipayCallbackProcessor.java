package com.mall.pay.channel.alipay.callback;

import com.mall.common.BusinessException;
import com.mall.pay.enums.PaymentChannel;
import com.mall.pay.enums.PaymentStatus;
import com.mall.pay.dto.PaymentCallbackRequest;
import com.mall.pay.dto.PaymentCallbackResponse;
import com.mall.pay.service.PaymentCallbackProcessor;
import com.mall.service.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayCallbackProcessor implements PaymentCallbackProcessor {

    private final PaymentOrderService paymentOrderService;
    private final AlipaySignatureVerifier verifier;

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
        if (params.isEmpty()) {
            log.error("支付宝回调参数为空");
            return "fail";
        }

        try {
            verifier.verifySign(params);
            verifier.validateBusinessParams(params);
        } catch (BusinessException e) {
            log.error("支付宝回调校验失败：{}", e.getMessage());
            return "fail";
        }

        PaymentCallbackRequest request = buildCallbackRequest(params);
        PaymentCallbackResponse response = paymentOrderService.handleCallback(request);

        return response.isSuccess() ? "success" : "fail";
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
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return PaymentStatus.SUCCESS;
            case "REFUND":
                return PaymentStatus.REFUND;
            case "TRADE_CLOSED":
                return PaymentStatus.FAILED;
            case "WAIT_BUYER_PAY":
                return PaymentStatus.WAITING;
            default:
                log.warn("未知支付宝交易状态: {}", tradeStatus);
                return PaymentStatus.FAILED;
        }
    }

    @Override
    public CallbackType callbackType() {
        return CallbackType.FORM;
    }
}
package com.mall.pay.channel.alipay.callback;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.mall.common.BusinessException;
import com.mall.enums.ResultCode;
import com.mall.pay.channel.alipay.config.AlipayProperties;
import com.mall.pay.config.PayProperties;
import com.mall.pay.port.AlertPort;
import com.mall.pay.port.PaymentOrderRepository;
import com.mall.pay.port.model.PaymentOrderSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 支付宝签名 + 业务校验。
 * <p>
 * 从 {@link AlipayCallbackProcessor} 抽出，供回调处理器和 PayFacade 共用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipaySignatureVerifier {

    private final PayProperties payProperties;
    private final PaymentOrderRepository paymentOrderRepository;
    private final AlertPort alertPort;

    /**
     * 验签。
     *
     * @throws BusinessException 验签失败
     */
    public void verifySign(Map<String, String> params) {
        log.info("支付宝回调验签开始，out_trade_no={}", params.get("out_trade_no"));
        AlipayProperties alipayProperties = payProperties.getAlipay();
        try {
            boolean ok = AlipaySignature.rsaCheckV1(
                    params,
                    alipayProperties.getAlipayPublicKey(),
                    StandardCharsets.UTF_8.name(),
                    alipayProperties.getSignType()
            );
            if (!ok) {
                log.error("支付宝回调验签失败，out_trade_no={}", params.get("out_trade_no"));
                throw new BusinessException(ResultCode.PAYMENT_SIGN_INVALID);
            }
        } catch (AlipayApiException e) {
            log.error("支付宝验签异常", e);
            throw new BusinessException(ResultCode.PAYMENT_SIGN_INVALID);
        }
    }

    /**
     * 业务校验：查支付单 + 校验金额 / seller_id / app_id。
     *
     * @return 支付单快照（供上层用）
     * @throws BusinessException 校验失败
     */
    public PaymentOrderSnapshot validateBusinessParams(Map<String, String> params) {
        AlipayProperties alipayProperties = payProperties.getAlipay();

        String outTradeNo = params.get("out_trade_no");
        String totalAmountStr = params.get("total_amount");
        String sellerId = params.get("seller_id");
        String appId = params.get("app_id");

        // 1. 查支付单
        PaymentOrderSnapshot payment = paymentOrderRepository.findByPaymentIdForUpdate(outTradeNo);
        if (payment == null) {
            log.error("业务校验失败：out_trade_no 不存在，outTradeNo={}", outTradeNo);
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 2. 校验金额
        if (StringUtils.hasText(totalAmountStr)) {
            BigDecimal callbackAmount = new BigDecimal(totalAmountStr);
            if (callbackAmount.compareTo(payment.getAmount()) != 0) {
                log.error("业务校验失败：金额不一致，callbackAmount={}, orderAmount={}",
                        callbackAmount, payment.getAmount());
                alertPort.critical(
                        "支付金额不一致，需人工核对",
                        String.format("bizType=%s, bizId=%s, paymentId=%s, 本地金额=%s, 第三方金额=%s",
                                payment.getBizType(), payment.getBizId(),
                                outTradeNo, payment.getAmount(), callbackAmount),
                        null
                );
                throw new BusinessException(ResultCode.PAYMENT_AMOUNT_MISMATCH);
            }
        }

        // 3. 校验 seller_id
        if (StringUtils.hasText(sellerId)) {
            if (!sellerId.equals(alipayProperties.getSellerId())) {
                log.error("业务校验失败：seller_id 不一致，callbackSellerId={}, configSellerId={}",
                        sellerId, alipayProperties.getSellerId());
                throw new BusinessException(ResultCode.PAYMENT_SIGN_INVALID, "seller_id 不匹配");
            }
        }

        // 4. 校验 app_id
        if (StringUtils.hasText(appId)) {
            if (!appId.equals(alipayProperties.getAppId())) {
                log.error("业务校验失败：app_id 不一致，callbackAppId={}, configAppId={}",
                        appId, alipayProperties.getAppId());
                throw new BusinessException(ResultCode.PAYMENT_SIGN_INVALID, "app_id 不匹配");
            }
        }

        return payment;
    }
}
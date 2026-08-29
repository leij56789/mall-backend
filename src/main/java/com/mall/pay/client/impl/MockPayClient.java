package com.mall.pay.client.impl;

import com.mall.common.BusinessException;
import com.mall.enums.ResultCode;
import com.mall.enums.pay.WechatPayErrorCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class MockPayClient implements PayClient {

    @Override
    public ThirdPartyPayResponse unifiedOrder(ThirdPartyPayRequest request) {
        log.info("MockPayClient 收到统一下单请求: {}", request);

        // 模拟 10% 概率失败（用于测试异常场景）
        if (Math.random() < 0.1) {
            log.warn("MockPayClient 模拟失败");
            return ThirdPartyPayResponse.builder()
                    .success(false)
                    .code(WechatPayErrorCode.PAY_ERROR.getCode())
                    .msg("支付通道繁忙，请稍后重试")
                    .build();
        }

        // 模拟网络延迟 200~500ms
        try {
            Thread.sleep(200 + (long) (Math.random() * 300));
        } catch (InterruptedException ignored) {
            throw new BusinessException(ResultCode.THIRD_PARTY_TIMEOUT);
        }

        // 模拟成功返回 prepay_id
        String prepayId = "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // 构造前端调起支付所需的参数（模拟微信 JSSDK 参数）
        String payParams = String.format(
                "{\"appId\":\"wx123456\",\"timeStamp\":\"%s\",\"nonceStr\":\"%s\",\"package\":\"prepay_id=%s\",\"signType\":\"RSA\",\"paySign\":\"mock_sign\"}",
                System.currentTimeMillis() / 1000,
                UUID.randomUUID().toString().substring(0, 8),
                prepayId
        );

        return ThirdPartyPayResponse.builder()
                .success(true)
                .prepayId(prepayId)
                .payParams(payParams)
                .build();
    }

    @Override
    public QueryOrderResponse queryOrder(QueryOrderRequest request) {
        return null;
    }

    @Override
    public String mapTradeStatusAfterQueryOrderOnPaymentCompensateJob(String alipayStatus) {
        return "";
    }

    @Override
    public String mapTradeStatusAfterQueryOrderOnAsyncQueryService(String alipayStatus) {
        return "";
    }

//    @Override
//    public String mapTradeStatusAfterUnifiedOrder(String alipayStatus) {
//        return "";
//    }

    @Override
    public boolean canRecoverFromPendingConfirm() {
        return false;
    }

    @Override
    public boolean closeOrder(String paymentId) {
        return false;
    }

    @Override
    public RefundResponse refundOrder(RefundRequest request) {
        return null;
    }
}
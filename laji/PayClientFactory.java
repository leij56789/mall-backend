package com.mall.pay.config;

import com.mall.common.BusinessException;
import com.mall.enums.PaymentChannel;
import com.mall.enums.PaymentMethod;
import com.mall.enums.ResultCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.client.impl.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PayClientFactory {
    private final Map<String, PayClient> clientMap;

    public PayClientFactory(List<PayClient> payClients) {
        this.clientMap = payClients.stream()
                .collect(Collectors.toMap(
                        client -> {
                            // 根据 bean 的类名或自定义注解标识渠道
                            if (client instanceof WechatPayClientAdapter) return PaymentChannel.WECHAT.getCode();
                            if (client instanceof AlipayClientAdapter) return PaymentChannel.ALIPAY.getCode();
                            if (client instanceof AlipayF2FPayClientAdapter) return PaymentMethod.ALIPAY_F2F.getCode();
                            if (client instanceof AlipayWapPayClientAdapter) return PaymentMethod.ALIPAY_WAP.getCode();
                            if (client instanceof MockPayClient) return PaymentMethod.MOCK.getCode();
                            throw new IllegalArgumentException("未知支付渠道: " + client.getClass());
                        },
                        Function.identity()
                ));
    }

    public PayClient getClient(String paymentMethod) {
        PayClient client = clientMap.get(paymentMethod.toUpperCase());
        if (client == null) {
            throw new BusinessException(ResultCode.PAYMENT_METHOD_NOT_SUPPORT);
        }
        return client;
    }
}
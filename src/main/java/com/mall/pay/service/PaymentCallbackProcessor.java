package com.mall.pay.service;

import java.util.Map;

public interface PaymentCallbackProcessor {
    String process(String rawBody);
    boolean supports(String channel);

    String process(Map<String, String> params);
}
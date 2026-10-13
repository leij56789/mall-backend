package com.mall.pay.service;

import java.util.Map;

public interface PaymentCallbackProcessor {
    String process(String rawBody);
    boolean supports(String channel);

    String process(Map<String, String> params);

    /**
     * 回调形态：FORM 走 Map 参数，RAW 走原始报文。
     * 默认 FORM（兼容现有支付宝处理器），微信等可覆写为 RAW。
     */
    default CallbackType callbackType() {
        return CallbackType.FORM;
    }

    enum CallbackType {
        FORM,   // 表单/query 参数，已解析为 Map
        RAW     // 原始报文（XML/JSON）
    }
}
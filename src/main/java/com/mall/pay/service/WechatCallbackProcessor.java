package com.mall.pay.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WechatCallbackProcessor implements PaymentCallbackProcessor {

    @Override
    public String process(String rawBody) {
//        // 1. 验签（微信 XML 签名）
//        if (!verifyWechatSign(rawBody)) {
//            return "FAIL";
//        }
//        // 2. 解析 XML
//        Map<String, String> params = parseWechatXml(rawBody);
//        // 3. 检查业务状态
//        if ("SUCCESS".equals(params.get("result_code"))) {
//            paymentService.handleSuccess(params.get("out_trade_no"), params.get("transaction_id"));
//        } else {
//            paymentService.handleFailed(params.get("out_trade_no"), params.get("err_code_des"));
//        }
        return "SUCCESS"; // 微信要求返回大写
    }

    @Override
    public boolean supports(String channel) {
        return "wechat".equalsIgnoreCase(channel);
    }

    @Override
    public String process(Map<String, String> params) {
        return "";
    }
}
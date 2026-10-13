package com.mall.pay.service;

import com.mall.common.BusinessException;
import com.mall.enums.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PaymentCallbackDispatcher {

    private final List<PaymentCallbackProcessor> processors;

    public PaymentCallbackDispatcher(List<PaymentCallbackProcessor> processors) {
        this.processors = processors;
        log.info("PaymentCallbackDispatcher 初始化完成，已注册: {}",
                processors.stream().map(p -> p.getClass().getSimpleName()).toList());
    }

    /** 表单/query 类回调（支付宝） */
    public String dispatchForm(String channel, Map<String, String> params) {
        PaymentCallbackProcessor processor = find(channel);
        requireType(processor, PaymentCallbackProcessor.CallbackType.FORM);
        return processor.process(params);
    }

    /** 原始报文类回调（微信） */
    public String dispatchRaw(String channel, String rawBody) {
        PaymentCallbackProcessor processor = find(channel);
        requireType(processor, PaymentCallbackProcessor.CallbackType.RAW);
        return processor.process(rawBody);
    }
    private void requireType(PaymentCallbackProcessor processor,
                             PaymentCallbackProcessor.CallbackType expected) {
        PaymentCallbackProcessor.CallbackType actual = processor.callbackType();
        if (actual != expected) {
            log.error("回调形态不匹配：渠道处理器 {} 声明为 {}，但被当作 {} 调用",
                    processor.getClass().getSimpleName(), actual, expected);
            throw new BusinessException(ResultCode.PAYMENT_METHOD_NOT_SUPPORT,
                    "渠道处理器回调形态不匹配: 期望 " + expected + "，实际 " + actual);
        }
    }

    private PaymentCallbackProcessor find(String channel) {
        for (PaymentCallbackProcessor p : processors) {
            if (p.supports(channel)) {
                return p;
            }
        }
        log.error("未找到渠道 [{}] 的回调处理器", channel);
        throw new BusinessException(ResultCode.PAYMENT_METHOD_NOT_SUPPORT,
                "不支持的回调渠道: " + channel);
    }
}
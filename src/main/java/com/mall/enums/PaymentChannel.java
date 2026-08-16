package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentChannel {
    WECHAT("WECHAT", "微信支付"),
    ALIPAY("ALIPAY", "支付宝"),
    MOCK("MOCK", "MOCK");

    private final String code;
    private final String desc;

    /**
     * 根据 code 获取枚举实例
     */
    public static PaymentChannel fromCode(String code) {
        for (PaymentChannel channel : values()) {
            if (channel.code.equalsIgnoreCase(code)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("未知支付渠道: " + code);
    }

    /**
     * 判断是否支持该 code
     */
    public static boolean isValid(String code) {
        for (PaymentChannel channel : values()) {
            if (channel.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
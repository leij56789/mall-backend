package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentMethod {
    // ===== 支付宝 =====
    ALIPAY_F2F("ALIPAY_F2F", "支付宝面对面支付（订单码/扫码）", PaymentChannel.ALIPAY),
    ALIPAY_WAP("ALIPAY_WAP", "支付宝手机网站支付", PaymentChannel.ALIPAY),
    ALIPAY_APP("ALIPAY_APP", "支付宝APP支付", PaymentChannel.ALIPAY),
    ALIPAY_PC("ALIPAY_PC", "支付宝电脑网站支付", PaymentChannel.ALIPAY),

    // ===== 微信 =====
    WECHAT_NATIVE("WECHAT_NATIVE", "微信Native支付（扫码）", PaymentChannel.WECHAT),
    WECHAT_JSAPI("WECHAT_JSAPI", "微信JSAPI支付（公众号）", PaymentChannel.WECHAT),
    WECHAT_APP("WECHAT_APP", "微信APP支付", PaymentChannel.WECHAT),
    WECHAT_H5("WECHAT_H5", "微信H5支付", PaymentChannel.WECHAT),
    //MOCK
    MOCK("MOCK", "微信H5支付", PaymentChannel.WECHAT);

    private final String code;
    private final String desc;
    private final PaymentChannel channel;

    public static PaymentMethod fromCode(String code) {
        for (PaymentMethod method : values()) {
            if (method.code.equalsIgnoreCase(code)) {
                return method;
            }
        }
        throw new IllegalArgumentException("未知支付方式: " + code);
    }

    public static boolean isValid(String code) {
        for (PaymentMethod method : values()) {
            if (method.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
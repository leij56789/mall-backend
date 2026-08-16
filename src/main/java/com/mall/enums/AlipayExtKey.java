package com.mall.enums;

// 新建一个枚举类
public enum AlipayExtKey {
    QR_CODE("qr_code"),
    SHARE_CODE("share_code"),
    TRADE_NO("trade_no");

    private final String key;
    AlipayExtKey(String key) { this.key = key; }
    public String getKey() { return key; }
}
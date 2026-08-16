package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ThirdPartyPayResponse {
    private boolean success;
    private String prepayId;          // 预支付 ID（微信 prepay_id / 支付宝 trade_no）
    private String code;              // 错误码（失败时）
    private String msg;               // 错误信息
    private String payParams;         // 前端调起支付所需的参数（JSON 字符串）

    /**
     * 扩展信息（渠道特有数据）
     * 例如支付宝：{ "qr_code": "https://qr.alipay.com/..." }
     * 例如微信：{ "package": "Sign=WXPay", "nonce_str": "abc123" }
     */
    private Map<String, Object> extInfo;
}
package com.mall.pay.dto;

import lombok.Data;

@Data
public class WrapFormRequest {
    /**
     * 支付单号（用于展示）
     */
    private String paymentId;

    /**
     * 支付宝返回的 HTML 表单字符串
     */
    private String formHtml;
}
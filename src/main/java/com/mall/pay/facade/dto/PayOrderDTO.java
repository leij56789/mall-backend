package com.mall.pay.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建支付入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOrderDTO {

    /** 业务类型（如 CLIENT_ORDER / LANDLORD_ORDER / DEPOSIT 等，由业务侧定义） */
    private String bizType;

    /** 业务ID（与 bizType 联合唯一） */
    private String bizId;

    /** 支付金额（元） */
    private BigDecimal amount;

    /** 订单标题（显示给用户） */
    private String subject;

    /** 订单描述 */
    private String description;

    /** 支付方式（ALIPAY_WAP / ALIPAY_F2F / WECHAT） */
    private String paymentMethod;

    /** 同步跳转地址（可选） */
    private String returnUrl;

    /** 客户端IP（可选，用于风控） */
    private String clientIp;

    /** 付款用户ID（必填） */
    private Long userId;
}
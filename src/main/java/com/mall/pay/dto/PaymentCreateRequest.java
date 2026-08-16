package com.mall.pay.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentCreateRequest {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "支付方式不能为空")
    private String paymentMethod;  // MOCK, WECHAT, ALIPAY
}
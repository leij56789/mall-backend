package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentCallbackResponse {
    private boolean success;
    private String message;

    public static PaymentCallbackResponse success() {
        return PaymentCallbackResponse.builder().success(true).message("OK").build();
    }

    public static PaymentCallbackResponse fail(String message) {
        return PaymentCallbackResponse.builder().success(false).message(message).build();
    }
}
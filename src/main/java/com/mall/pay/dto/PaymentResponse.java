package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private String paymentId;
    private String status;        // WAITING / SUCCESS / FAILED / PROCESSING
    private String message;       // 提示信息
    private String payUrl;        // 支付链接（仅 WAITING 时有效）
    private LocalDateTime expireAt; // 超时时间（仅 WAITING 时有效）

    /**
     * 返回“处理中”状态响应（用于超时/解析异常等不确定场景）
     */
    public static PaymentResponse processing(String paymentId) {
        return PaymentResponse.builder()
                .paymentId(paymentId)
                .status("PROCESSING")
                .message("支付请求已提交，请稍后查询订单状态")
                .build();
    }

    /**
     * 返回“成功”状态响应（预下单成功，等待用户支付）
     */
    public static PaymentResponse waiting(String paymentId, String payUrl, LocalDateTime expireAt) {
        return PaymentResponse.builder()
                .paymentId(paymentId)
                .status("WAITING")
                .message("支付单创建成功，请完成支付")
                .payUrl(payUrl)
                .expireAt(expireAt)
                .build();
    }

    /**
     * 返回“失败”状态响应
     */
    public static PaymentResponse failed(String paymentId, String errorMsg) {
        return PaymentResponse.builder()
                .paymentId(paymentId)
                .status("FAILED")
                .message(errorMsg)
                .build();
    }
}
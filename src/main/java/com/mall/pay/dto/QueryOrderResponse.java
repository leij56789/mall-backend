package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class QueryOrderResponse {
    private boolean success;          // 是否成功调用第三方接口（技术层面）
    private String tradeState;        // 交易状态：WAITING / SUCCESS / FAILED / CLOSED / REFUND
    private String prepayId;          // 预支付 ID（如果存在）
    private String transactionId;     // 第三方交易号（支付成功时）
    private String totalAmount;       // 总金额（单位：元）
    private String errorCode;         // 错误码（失败时）
    private String errorMsg;          // 错误信息（失败时）
    private Map<String,Object> extInfo;
}
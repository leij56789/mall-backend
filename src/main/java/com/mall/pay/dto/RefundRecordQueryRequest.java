package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款记录查询请求
 */
@Data
@Builder
public class RefundRecordQueryRequest {
    /**
     * 支付单号
     */
    private String paymentId;

    /**
     * 商户订单号
     */
    private String outTradeNo;

    /**
     * 退款请求号
     */
    private String outRequestNo;

    /**
     * 退款状态列表（PROCESSING/SUCCESS/FAILED）
     */
    private List<String> statusList;

    /**
     * 退款金额最小值
     */
    private String minAmount;

    /**
     * 退款金额最大值
     */
    private String maxAmount;

    /**
     * 开始时间（创建时间）
     */
    private LocalDateTime startTime;

    /**
     * 结束时间（创建时间）
     */
    private LocalDateTime endTime;

    /**
     * 页码（从1开始）
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 20;
}
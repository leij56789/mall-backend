package com.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款记录实体
 */
@Data
@Builder
@TableName("payment_refund_record")
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRefundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付单号（关联 payment_order.payment_id） */
    private String paymentId;

    /** 商户订单号（冗余） */
    private String outTradeNo;

    /** 支付宝交易号（冗余） */
    private String tradeNo;

    /** 本次退款金额（元） */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String refundReason;

    /** 退款请求号（幂等键） */
    private String outRequestNo;

    /** 退款状态: PROCESSING/SUCCESS/FAILED */
    private String status;

    /** 失败原因 */
    private String failReason;

    /** 第三方退款交易号 */
    private String thirdPartyRefundNo;

    /** 资金渠道明细（JSON） */
    private String fundDetail;

    /** 查询重试次数 */
    private Integer retryCount;

    /** 下次查询时间（PROCESSING时使用） */
    private LocalDateTime nextQueryTime;

    /** 操作人ID */
    private Long operatorId;

    /** 客户端IP */
    private String clientIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
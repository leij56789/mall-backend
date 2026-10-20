package com.mall.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("payment_refund_notify_log")
public class PaymentRefundNotifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long refundId;
    private String outRequestNo;
    private String paymentId;
    private String notifyType;    // SUCCESS / FAILED
    private String status;        // PENDING / PROCESSING / SUCCESS / FAILED
    private Integer retryCount;
    private LocalDateTime nextNotifyTime;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 最后错误码 */
    private String lastErrorCode;

    /** 最后错误信息 */
    private String lastErrorMsg;
}
package com.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 
 * @TableName payment_order
 */
@TableName(value ="payment_order",autoResultMap = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentOrder implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 支付单号（业务主键）
     */
    private String paymentId;

    /**
     * 
     */
    private Long orderId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 
     */
    private BigDecimal amount;

    /**
     * 
     */
    private String paymentMethod;

    /**
     * WAITING/SUCCESS/FAILED/REFUND
     */
    private String status;

    /**
     * 第三方交易号
     */
    private String thirdPartyTradeNo;

    /**
     * 回调时间
     */
    private LocalDateTime callbackTime;

    /**
     * 支付超时时间（15分钟）
     */
    private LocalDateTime expiredAt;

    /**
     * 乐观锁
     */
    private Integer version;

    /**
     * 
     */
    private LocalDateTime createdAt;

    /**
     * 
     */
    private LocalDateTime updatedAt;

    /**
     * 补偿重试次数
     */
    private Integer retryCount;

    /**
     * 第三方预支付ID（仅WAITING状态有效）
     */
    private String prepayId;

    /**
     * 支付渠道扩展信息（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String,Object> extInfo;

    /**
     * ✅ 退款成功时间（新增）
     * 语义：退款确认成功的时间
     * 设置时机：updateRefundSuccess() 中
     */
    private LocalDateTime refundTime;

    // ===== 金额字段 =====

    /**
     * ✅ 已退款累计金额（新增）
     * 语义：该支付单所有已成功退款的金额总和
     * 用途：快速判断是否可退款、是否全额退款
     * 更新时机：每次退款成功时累加
     */
    private BigDecimal refundedAmount;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        PaymentOrder other = (PaymentOrder) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getPaymentId() == null ? other.getPaymentId() == null : this.getPaymentId().equals(other.getPaymentId()))
            && (this.getOrderId() == null ? other.getOrderId() == null : this.getOrderId().equals(other.getOrderId()))
            && (this.getUserId() == null ? other.getUserId() == null : this.getUserId().equals(other.getUserId()))
            && (this.getAmount() == null ? other.getAmount() == null : this.getAmount().equals(other.getAmount()))
            && (this.getPaymentMethod() == null ? other.getPaymentMethod() == null : this.getPaymentMethod().equals(other.getPaymentMethod()))
            && (this.getStatus() == null ? other.getStatus() == null : this.getStatus().equals(other.getStatus()))
            && (this.getThirdPartyTradeNo() == null ? other.getThirdPartyTradeNo() == null : this.getThirdPartyTradeNo().equals(other.getThirdPartyTradeNo()))
            && (this.getCallbackTime() == null ? other.getCallbackTime() == null : this.getCallbackTime().equals(other.getCallbackTime()))
            && (this.getExpiredAt() == null ? other.getExpiredAt() == null : this.getExpiredAt().equals(other.getExpiredAt()))
            && (this.getVersion() == null ? other.getVersion() == null : this.getVersion().equals(other.getVersion()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null : this.getUpdatedAt().equals(other.getUpdatedAt()))
            && (this.getRetryCount() == null ? other.getRetryCount() == null : this.getRetryCount().equals(other.getRetryCount()))
            && (this.getPrepayId() == null ? other.getPrepayId() == null : this.getPrepayId().equals(other.getPrepayId()))
            && (this.getExtInfo() == null ? other.getExtInfo() == null : this.getExtInfo().equals(other.getExtInfo()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getPaymentId() == null) ? 0 : getPaymentId().hashCode());
        result = prime * result + ((getOrderId() == null) ? 0 : getOrderId().hashCode());
        result = prime * result + ((getUserId() == null) ? 0 : getUserId().hashCode());
        result = prime * result + ((getAmount() == null) ? 0 : getAmount().hashCode());
        result = prime * result + ((getPaymentMethod() == null) ? 0 : getPaymentMethod().hashCode());
        result = prime * result + ((getStatus() == null) ? 0 : getStatus().hashCode());
        result = prime * result + ((getThirdPartyTradeNo() == null) ? 0 : getThirdPartyTradeNo().hashCode());
        result = prime * result + ((getCallbackTime() == null) ? 0 : getCallbackTime().hashCode());
        result = prime * result + ((getExpiredAt() == null) ? 0 : getExpiredAt().hashCode());
        result = prime * result + ((getVersion() == null) ? 0 : getVersion().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getUpdatedAt() == null) ? 0 : getUpdatedAt().hashCode());
        result = prime * result + ((getRetryCount() == null) ? 0 : getRetryCount().hashCode());
        result = prime * result + ((getPrepayId() == null) ? 0 : getPrepayId().hashCode());
        result = prime * result + ((getExtInfo() == null) ? 0 : getExtInfo().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", orderId=").append(orderId);
        sb.append(", userId=").append(userId);
        sb.append(", amount=").append(amount);
        sb.append(", paymentMethod=").append(paymentMethod);
        sb.append(", status=").append(status);
        sb.append(", thirdPartyTradeNo=").append(thirdPartyTradeNo);
        sb.append(", callbackTime=").append(callbackTime);
        sb.append(", expiredAt=").append(expiredAt);
        sb.append(", version=").append(version);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append(", retryCount=").append(retryCount);
        sb.append(", prepayId=").append(prepayId);
        sb.append(", extInfo=").append(extInfo);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}
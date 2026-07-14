package com.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息日志表（本地消息表）
 * @TableName broker_message_log
 */
@TableName(value ="broker_message_log")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BrokerMessageLog {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
     */
    @TableField(value = "order_id")
    private Long orderId;

    /**
     * 消息ID（业务幂等）
     */
    @TableField(value = "message_id")
    private String messageId;

    /**
     * MQ交换机
     */
    @TableField(value = "exchange")
    private String exchange;

    /**
     * MQ路由键
     */
    @TableField(value = "routing_key")
    private String routingKey;

    /**
     * 消息体JSON（快照数据）
     */
    @TableField(value = "message_body")
    private String messageBody;

    /**
     * 延迟时间(毫秒)
     */
    @TableField(value = "delay_time")
    private Integer delayTime;

    /**
     * 状态 0-待发送 1-已发送 2-发送失败 3-最终失败
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 已重试次数
     */
    @TableField(value = "retry_count")
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    @TableField(value = "max_retry")
    private Integer maxRetry;

    /**
     * 下次重试时间
     */
    @TableField(value = "next_retry_time")
    private LocalDateTime nextRetryTime;

    /**
     * 最后一次错误信息
     */
    @TableField(value = "error_msg")
    private String errorMsg;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * 用户ID（秒杀/订单关联）
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 商品ID（秒杀/订单关联）
     */
    @TableField(value = "book_id")
    private Long bookId;

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
        BrokerMessageLog other = (BrokerMessageLog) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getOrderId() == null ? other.getOrderId() == null : this.getOrderId().equals(other.getOrderId()))
            && (this.getMessageId() == null ? other.getMessageId() == null : this.getMessageId().equals(other.getMessageId()))
            && (this.getExchange() == null ? other.getExchange() == null : this.getExchange().equals(other.getExchange()))
            && (this.getRoutingKey() == null ? other.getRoutingKey() == null : this.getRoutingKey().equals(other.getRoutingKey()))
            && (this.getMessageBody() == null ? other.getMessageBody() == null : this.getMessageBody().equals(other.getMessageBody()))
            && (this.getDelayTime() == null ? other.getDelayTime() == null : this.getDelayTime().equals(other.getDelayTime()))
            && (this.getStatus() == null ? other.getStatus() == null : this.getStatus().equals(other.getStatus()))
            && (this.getRetryCount() == null ? other.getRetryCount() == null : this.getRetryCount().equals(other.getRetryCount()))
            && (this.getMaxRetry() == null ? other.getMaxRetry() == null : this.getMaxRetry().equals(other.getMaxRetry()))
            && (this.getNextRetryTime() == null ? other.getNextRetryTime() == null : this.getNextRetryTime().equals(other.getNextRetryTime()))
            && (this.getErrorMsg() == null ? other.getErrorMsg() == null : this.getErrorMsg().equals(other.getErrorMsg()))
            && (this.getCreateTime() == null ? other.getCreateTime() == null : this.getCreateTime().equals(other.getCreateTime()))
            && (this.getUpdateTime() == null ? other.getUpdateTime() == null : this.getUpdateTime().equals(other.getUpdateTime()))
            && (this.getUserId() == null ? other.getUserId() == null : this.getUserId().equals(other.getUserId()))
            && (this.getBookId() == null ? other.getBookId() == null : this.getBookId().equals(other.getBookId()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getOrderId() == null) ? 0 : getOrderId().hashCode());
        result = prime * result + ((getMessageId() == null) ? 0 : getMessageId().hashCode());
        result = prime * result + ((getExchange() == null) ? 0 : getExchange().hashCode());
        result = prime * result + ((getRoutingKey() == null) ? 0 : getRoutingKey().hashCode());
        result = prime * result + ((getMessageBody() == null) ? 0 : getMessageBody().hashCode());
        result = prime * result + ((getDelayTime() == null) ? 0 : getDelayTime().hashCode());
        result = prime * result + ((getStatus() == null) ? 0 : getStatus().hashCode());
        result = prime * result + ((getRetryCount() == null) ? 0 : getRetryCount().hashCode());
        result = prime * result + ((getMaxRetry() == null) ? 0 : getMaxRetry().hashCode());
        result = prime * result + ((getNextRetryTime() == null) ? 0 : getNextRetryTime().hashCode());
        result = prime * result + ((getErrorMsg() == null) ? 0 : getErrorMsg().hashCode());
        result = prime * result + ((getCreateTime() == null) ? 0 : getCreateTime().hashCode());
        result = prime * result + ((getUpdateTime() == null) ? 0 : getUpdateTime().hashCode());
        result = prime * result + ((getUserId() == null) ? 0 : getUserId().hashCode());
        result = prime * result + ((getBookId() == null) ? 0 : getBookId().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", orderId=").append(orderId);
        sb.append(", messageId=").append(messageId);
        sb.append(", exchange=").append(exchange);
        sb.append(", routingKey=").append(routingKey);
        sb.append(", messageBody=").append(messageBody);
        sb.append(", delayTime=").append(delayTime);
        sb.append(", status=").append(status);
        sb.append(", retryCount=").append(retryCount);
        sb.append(", maxRetry=").append(maxRetry);
        sb.append(", nextRetryTime=").append(nextRetryTime);
        sb.append(", errorMsg=").append(errorMsg);
        sb.append(", createTime=").append(createTime);
        sb.append(", updateTime=").append(updateTime);
        sb.append(", userId=").append(userId);
        sb.append(", bookId=").append(bookId);
        sb.append("]");
        return sb.toString();
    }
}
package com.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 支付审计日志表
 * @TableName payment_audit_log
 */
@TableName(value ="payment_audit_log")
@Data
@Builder
public class PaymentAuditLog {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 链路追踪ID
     */
    @TableField(value = "trace_id")
    private String traceId;

    /**
     * 操作用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 操作用户名
     */
    @TableField(value = "username")
    private String username;

    /**
     * 客户端IP
     */
    @TableField(value = "client_ip")
    private String clientIp;

    /**
     * 用户代理
     */
    @TableField(value = "user_agent")
    private String userAgent;

    /**
     * 支付单号
     */
    @TableField(value = "payment_id")
    private String paymentId;

    /**
     * 订单ID
     */
    @TableField(value = "order_id")
    private Long orderId;

    /**
     * 退款记录ID
     */
    @TableField(value = "refund_record_id")
    private Long refundRecordId;

    /**
     * 操作类型: CREATE_PAYMENT/PAYMENT_CALLBACK/REFUND/REFUND_CALLBACK/QUERY/CLOSE
     */
    @TableField(value = "operation")
    private String operation;

    /**
     * 操作描述
     */
    @TableField(value = "operation_desc")
    private String operationDesc;

    /**
     * 请求参数（脱敏后）
     */
    @TableField(value = "request_params")
    private String requestParams;

    /**
     * 请求体（脱敏后）
     */
    @TableField(value = "request_body")
    private String requestBody;

    /**
     * 响应体（脱敏后）
     */
    @TableField(value = "response_body")
    private String responseBody;

    /**
     * 操作前状态
     */
    @TableField(value = "before_status")
    private String beforeStatus;

    /**
     * 操作后状态
     */
    @TableField(value = "after_status")
    private String afterStatus;

    /**
     * 操作结果: SUCCESS/FAIL/PROCESSING
     */
    @TableField(value = "result")
    private String result;

    /**
     * 错误码
     */
    @TableField(value = "error_code")
    private String errorCode;

    /**
     * 错误信息
     */
    @TableField(value = "error_msg")
    private String errorMsg;

    /**
     * 耗时（毫秒）
     */
    @TableField(value = "cost_ms")
    private Long costMs;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 操作者类型: USER/SYSTEM/BATCH/COMPENSATE
     */
    @TableField(value = "operator_type")
    private String operatorType;

    /**
     * 上一条记录的哈希值
     */
    @TableField(value = "prev_hash")
    private String prevHash;

    /**
     * 本记录的哈希值
     */
    @TableField(value = "self_hash")
    private String selfHash;

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
        PaymentAuditLog other = (PaymentAuditLog) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getTraceId() == null ? other.getTraceId() == null : this.getTraceId().equals(other.getTraceId()))
            && (this.getUserId() == null ? other.getUserId() == null : this.getUserId().equals(other.getUserId()))
            && (this.getUsername() == null ? other.getUsername() == null : this.getUsername().equals(other.getUsername()))
            && (this.getClientIp() == null ? other.getClientIp() == null : this.getClientIp().equals(other.getClientIp()))
            && (this.getUserAgent() == null ? other.getUserAgent() == null : this.getUserAgent().equals(other.getUserAgent()))
            && (this.getPaymentId() == null ? other.getPaymentId() == null : this.getPaymentId().equals(other.getPaymentId()))
            && (this.getOrderId() == null ? other.getOrderId() == null : this.getOrderId().equals(other.getOrderId()))
            && (this.getRefundRecordId() == null ? other.getRefundRecordId() == null : this.getRefundRecordId().equals(other.getRefundRecordId()))
            && (this.getOperation() == null ? other.getOperation() == null : this.getOperation().equals(other.getOperation()))
            && (this.getOperationDesc() == null ? other.getOperationDesc() == null : this.getOperationDesc().equals(other.getOperationDesc()))
            && (this.getRequestParams() == null ? other.getRequestParams() == null : this.getRequestParams().equals(other.getRequestParams()))
            && (this.getRequestBody() == null ? other.getRequestBody() == null : this.getRequestBody().equals(other.getRequestBody()))
            && (this.getResponseBody() == null ? other.getResponseBody() == null : this.getResponseBody().equals(other.getResponseBody()))
            && (this.getBeforeStatus() == null ? other.getBeforeStatus() == null : this.getBeforeStatus().equals(other.getBeforeStatus()))
            && (this.getAfterStatus() == null ? other.getAfterStatus() == null : this.getAfterStatus().equals(other.getAfterStatus()))
            && (this.getResult() == null ? other.getResult() == null : this.getResult().equals(other.getResult()))
            && (this.getErrorCode() == null ? other.getErrorCode() == null : this.getErrorCode().equals(other.getErrorCode()))
            && (this.getErrorMsg() == null ? other.getErrorMsg() == null : this.getErrorMsg().equals(other.getErrorMsg()))
            && (this.getCostMs() == null ? other.getCostMs() == null : this.getCostMs().equals(other.getCostMs()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getOperatorType() == null ? other.getOperatorType() == null : this.getOperatorType().equals(other.getOperatorType()))
            && (this.getPrevHash() == null ? other.getPrevHash() == null : this.getPrevHash().equals(other.getPrevHash()))
            && (this.getSelfHash() == null ? other.getSelfHash() == null : this.getSelfHash().equals(other.getSelfHash()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getTraceId() == null) ? 0 : getTraceId().hashCode());
        result = prime * result + ((getUserId() == null) ? 0 : getUserId().hashCode());
        result = prime * result + ((getUsername() == null) ? 0 : getUsername().hashCode());
        result = prime * result + ((getClientIp() == null) ? 0 : getClientIp().hashCode());
        result = prime * result + ((getUserAgent() == null) ? 0 : getUserAgent().hashCode());
        result = prime * result + ((getPaymentId() == null) ? 0 : getPaymentId().hashCode());
        result = prime * result + ((getOrderId() == null) ? 0 : getOrderId().hashCode());
        result = prime * result + ((getRefundRecordId() == null) ? 0 : getRefundRecordId().hashCode());
        result = prime * result + ((getOperation() == null) ? 0 : getOperation().hashCode());
        result = prime * result + ((getOperationDesc() == null) ? 0 : getOperationDesc().hashCode());
        result = prime * result + ((getRequestParams() == null) ? 0 : getRequestParams().hashCode());
        result = prime * result + ((getRequestBody() == null) ? 0 : getRequestBody().hashCode());
        result = prime * result + ((getResponseBody() == null) ? 0 : getResponseBody().hashCode());
        result = prime * result + ((getBeforeStatus() == null) ? 0 : getBeforeStatus().hashCode());
        result = prime * result + ((getAfterStatus() == null) ? 0 : getAfterStatus().hashCode());
        result = prime * result + ((getResult() == null) ? 0 : getResult().hashCode());
        result = prime * result + ((getErrorCode() == null) ? 0 : getErrorCode().hashCode());
        result = prime * result + ((getErrorMsg() == null) ? 0 : getErrorMsg().hashCode());
        result = prime * result + ((getCostMs() == null) ? 0 : getCostMs().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getOperatorType() == null) ? 0 : getOperatorType().hashCode());
        result = prime * result + ((getPrevHash() == null) ? 0 : getPrevHash().hashCode());
        result = prime * result + ((getSelfHash() == null) ? 0 : getSelfHash().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", traceId=").append(traceId);
        sb.append(", userId=").append(userId);
        sb.append(", username=").append(username);
        sb.append(", clientIp=").append(clientIp);
        sb.append(", userAgent=").append(userAgent);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", orderId=").append(orderId);
        sb.append(", refundRecordId=").append(refundRecordId);
        sb.append(", operation=").append(operation);
        sb.append(", operationDesc=").append(operationDesc);
        sb.append(", requestParams=").append(requestParams);
        sb.append(", requestBody=").append(requestBody);
        sb.append(", responseBody=").append(responseBody);
        sb.append(", beforeStatus=").append(beforeStatus);
        sb.append(", afterStatus=").append(afterStatus);
        sb.append(", result=").append(result);
        sb.append(", errorCode=").append(errorCode);
        sb.append(", errorMsg=").append(errorMsg);
        sb.append(", costMs=").append(costMs);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", operatorType=").append(operatorType);
        sb.append(", prevHash=").append(prevHash);
        sb.append(", selfHash=").append(selfHash);
        sb.append("]");
        return sb.toString();
    }
}
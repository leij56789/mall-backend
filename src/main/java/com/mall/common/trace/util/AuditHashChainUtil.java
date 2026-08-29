package com.mall.common.trace.util;

import com.mall.entity.PaymentAuditLog;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 哈希链工具
 * <p>
 * 用于审计日志的防篡改哈希链计算
 */
@Component
public class AuditHashChainUtil {

    /**
     * 计算记录的 self_hash
     * <p>
     * 基于以下字段拼接后计算 SHA-256：
     * <ul>
     *   <li>operation</li>
     *   <li>paymentId</li>
     *   <li>orderId</li>
     *   <li>refundRecordId</li>
     *   <li>beforeStatus</li>
     *   <li>afterStatus</li>
     *   <li>userId</li>
     *   <li>clientIp</li>
     *   <li>operatorType</li>
     *   <li>result</li>
     *   <li>errorCode</li>
     *   <li>errorMsg</li>
     *   <li>prevHash</li>
     *   <li>createdAt</li>
     * </ul>
     *
     * @param content 哈希内容
     * @return SHA-256 哈希值（16进制字符串）
     */
    public String calculateSelfHash(AuditHashContent content) {
        // 1. 构建原始字符串
        String raw = content.getOperation() + "|"
                + nullToEmpty(content.getPaymentId()) + "|"
                + nullToEmpty(content.getOrderId()) + "|"
                + nullToEmpty(content.getRefundRecordId()) + "|"
                + nullToEmpty(content.getBeforeStatus()) + "|"
                + nullToEmpty(content.getAfterStatus()) + "|"
                + nullToEmpty(content.getUserId()) + "|"
                + nullToEmpty(content.getClientIp()) + "|"
                + nullToEmpty(content.getOperatorType()) + "|"
                + nullToEmpty(content.getResult()) + "|"
                + nullToEmpty(content.getErrorCode()) + "|"
                + nullToEmpty(content.getErrorMsg()) + "|"
                + nullToEmpty(content.getPrevHash()) + "|"
                + nullToEmpty(content.getCreatedAt());

        // 2. 计算 SHA-256
        return DigestUtils.sha256Hex(raw);
    }


    /**
     * 构建哈希内容对象
     */
    public AuditHashContent buildHashContent(PaymentAuditLog log, String prevHash) {
        return AuditHashContent.builder()
                               .operation(log.getOperation())
                               .paymentId(log.getPaymentId())
                               .orderId(log.getOrderId() != null ? String.valueOf(log.getOrderId()) : null)
                               .refundRecordId(log.getRefundRecordId() != null ? String.valueOf(log.getRefundRecordId()) : null)
                               .beforeStatus(log.getBeforeStatus())
                               .afterStatus(log.getAfterStatus())
                               .userId(log.getUserId() != null ? String.valueOf(log.getUserId()) : "0")
                               .clientIp(log.getClientIp())
                               .operatorType(log.getOperatorType())
                               .result(log.getResult())
                               .errorCode(log.getErrorCode())
                               .errorMsg(log.getErrorMsg())
                               .prevHash(prevHash)
                               .createdAt(log.getCreatedAt() != null ? log.getCreatedAt().toString() : LocalDateTime.now().toString())
                               .build();
    }

    /**
     * 验证哈希链完整性
     */
    public boolean verifyChain(List<PaymentAuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return true;
        }

        for (int i = 0; i < logs.size(); i++) {
            PaymentAuditLog current = logs.get(i);

            // 第一条记录检查 prev_hash = "INIT" 或 null
            if (i == 0) {
                if (current.getPrevHash() != null && !"INIT".equals(current.getPrevHash())) {
                    return false;
                }
                continue;
            }

            // 验证当前记录的 prev_hash 是否等于上一条记录的 self_hash
            PaymentAuditLog prev = logs.get(i - 1);
            if (!prev.getSelfHash().equals(current.getPrevHash())) {
                return false;
            }

            // 验证当前记录的 self_hash 是否正确
            String calculated = calculateSelfHash(buildHashContent(current, current.getPrevHash()));
            if (!calculated.equals(current.getSelfHash())) {
                return false;
            }
        }
        return true;
    }

    /**
     * null 转空字符串
     */
    private String nullToEmpty(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    // ===== 内部类 =====
    @Data
    @Builder
    public static class AuditHashContent {
        private String operation;
        private String paymentId;
        private String orderId;
        private String refundRecordId;
        private String beforeStatus;
        private String afterStatus;
        private String userId;
        private String clientIp;
        private String operatorType;
        private String result;
        private String errorCode;
        private String errorMsg;
        private String prevHash;
        private String createdAt;
    }

}
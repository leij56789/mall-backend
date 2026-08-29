package com.mall.pay.audit;

import com.mall.enums.AuditOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计状态映射器
 * <p>
 * 将状态变化（beforeStatus → afterStatus）映射为对应的 AuditOperation
 */
@Slf4j
@Component
public class AuditStateMapper {

    /**
     * 一对一映射：状态变化 → AuditOperation code
     */
    private static final Map<String, String> ONE_TO_ONE_MAP = new HashMap<>();

    /**
     * 一对多映射：状态变化 → 可能的 AuditOperation code 列表
     * 这类场景需要开发者通过注解手动指定
     */
    private static final Map<String, List<String>> ONE_TO_MANY_MAP = new HashMap<>();

    static {
        // ===== 支付相关 =====
        // 创建支付单
        ONE_TO_ONE_MAP.put("→INIT", "CREATE_PAYMENT");

        // 支付状态流转
        ONE_TO_ONE_MAP.put("INIT→WAITING", "CREATE_PAYMENT");
        ONE_TO_ONE_MAP.put("WAITING→SUCCESS", "PAYMENT_CALLBACK");
        ONE_TO_ONE_MAP.put("WAITING→FAILED", "PAYMENT_FAILED");
        ONE_TO_ONE_MAP.put("SUCCESS→FAILED", "PAYMENT_FAILED");

        // ===== 退款相关 =====
        // 退款记录状态流转
        ONE_TO_ONE_MAP.put("PROCESSING→SUCCESS", "REFUND_CALLBACK");
        ONE_TO_ONE_MAP.put("PROCESSING→FAILED", "REFUND_FAILED");

        // 支付单退款状态流转
        ONE_TO_ONE_MAP.put("SUCCESS→REFUND", "REFUND_SUCCESS");
        ONE_TO_ONE_MAP.put("SUCCESS→PARTIAL_REFUNDED", "REFUND_SUCCESS");
        ONE_TO_ONE_MAP.put("PARTIAL_REFUNDED→REFUND", "REFUND_SUCCESS");
        ONE_TO_ONE_MAP.put("PARTIAL_REFUNDED→PARTIAL_REFUNDED", "REFUND_SUCCESS");

        // ===== 系统补偿 =====
        ONE_TO_ONE_MAP.put("PENDING_CONFIRM→SUCCESS", "PAYMENT_COMPENSATE");
        ONE_TO_ONE_MAP.put("PENDING_CONFIRM→FAILED", "PAYMENT_COMPENSATE");
        ONE_TO_ONE_MAP.put("PENDING_CONFIRM→WAITING", "PAYMENT_COMPENSATE");

        // ===== 关单 =====
        ONE_TO_ONE_MAP.put("WAITING→CLOSED", "CLOSE_PAYMENT");
        ONE_TO_ONE_MAP.put("INIT→CLOSED", "CLOSE_PAYMENT");

        // ===== 一对多（歧义） =====
        // 例如：INIT→FAILED 可能来自创建失败、超时关单、系统取消等
        ONE_TO_MANY_MAP.put("INIT→FAILED", List.of(
                "PAYMENT_FAILED",
                "PAYMENT_CLOSED",
                "PAYMENT_CREATE_FAIL"
        ));

        // INIT→CLOSED 可能来自用户取消或系统关单
        ONE_TO_MANY_MAP.put("INIT→CLOSED", List.of(
                "CLOSE_PAYMENT",
                "PAYMENT_CANCELLED"
        ));

        // PENDING_CONFIRM→FAILED 可能来自查询失败或主动关单
        ONE_TO_MANY_MAP.put("PENDING_CONFIRM→FAILED", List.of(
                "PAYMENT_COMPENSATE",
                "CLOSE_PAYMENT"
        ));

        // PENDING_CONFIRM→SUCCESS 可能来自回调或补偿查询
        ONE_TO_MANY_MAP.put("PENDING_CONFIRM→SUCCESS", List.of(
                "PAYMENT_CALLBACK",
                "PAYMENT_COMPENSATE"
        ));
    }

    /**
     * 推断操作类型
     *
     * @param beforeStatus 操作前状态
     * @param afterStatus  操作后状态
     * @return 操作类型 code，如果存在歧义则返回 null
     */
    public String inferOperation(String beforeStatus, String afterStatus) {
        String key = (beforeStatus == null ? "" : beforeStatus) + "→" + (afterStatus == null ? "" : afterStatus);

        // 1. 检查是否为一对多（歧义）
        if (ONE_TO_MANY_MAP.containsKey(key)) {
            log.debug("状态变化存在歧义: key={}, 请通过注解指定 operation", key);
            return null;  // 由开发者通过注解指定
        }

        // 2. 检查一对一映射
        if (ONE_TO_ONE_MAP.containsKey(key)) {
            return ONE_TO_ONE_MAP.get(key);
        }

        // 3. 没有映射，返回 null，由调用方决定
        log.warn("未找到状态变化映射: key={}", key);
        return null;
    }

    /**
     * 判断状态变化是否存在歧义
     */
    public boolean isAmbiguous(String beforeStatus, String afterStatus) {
        String key = (beforeStatus == null ? "" : beforeStatus) + "→" + (afterStatus == null ? "" : afterStatus);
        return ONE_TO_MANY_MAP.containsKey(key);
    }
}
package com.mall.annotation;

import com.mall.enums.AuditOperation;
import com.mall.enums.AuditTargetType;
import java.lang.annotation.*;

/**
 * 审计日志注解
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 审计支付单
 * {@code @AuditLog(targetTypes = {AuditTargetType.PAYMENT_ORDER}, paymentId = "paymentId")}
 *
 * // 同时审计支付单和退款记录
 * {@code @AuditLog(targetTypes = {AuditTargetType.PAYMENT_ORDER, AuditTargetType.REFUND_RECORD}, paymentId = "paymentId", refundRecordId = "recordId")}
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 审计目标类型（必填）
     * <p>
     * 可选值：{@link AuditTargetType#PAYMENT_ORDER}, {@link AuditTargetType#REFUND_RECORD}
     * 可同时指定多个
     */
    AuditTargetType[] targetTypes();

    /**
     * 支付单号对应的参数名（可选）
     * <p>
     * 指定方法参数中哪个参数是 paymentId
     */
    String paymentId() default "";

    /**
     * 退款记录ID对应的参数名（可选）
     * <p>
     * 指定方法参数中哪个参数是 refundRecordId
     */
    String refundRecordId() default "";

    /**
     * ✅ 订单ID对应的参数名（当目标类型包含 ORDER 时使用）
     */
    String orderId() default "";

    /**
     * 手动指定操作类型（可选）
     * <p>
     * 当状态变化自动映射存在歧义时，可通过此参数指定
     * <br>使用枚举值，避免魔法字符串
     */
    AuditOperation operation() default AuditOperation.UNKNOWN;

    /**
     * 操作描述（可选）
     */
    String desc() default "";
}
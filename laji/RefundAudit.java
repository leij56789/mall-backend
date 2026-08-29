package com.mall.annotation;

import java.lang.annotation.*;

/**
 * 退款审计日志注解
 * <p>
 * 用于标记需要自动记录退款审计日志的方法。
 * 切面会在方法执行前/后自动记录状态变化。
 *
 * <p><b>使用条件：</b>
 * <ul>
 *   <li>方法参数中必须包含 {@code recordId}（退款记录ID），类型为 {@code Long}</li>
 *   <li>或参数中包含 {@code paymentId}（支付单号），类型为 {@code String}</li>
 *   <li>如果同时存在，优先使用 {@code recordId}</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * <ul>
 *   <li>退款记录标记成功：{@code @RefundAudit("SUCCESS")}</li>
 *   <li>退款记录标记失败：{@code @RefundAudit("FAILED")}</li>
 * </ul>
 *
 * <p><b>示例：</b>
 * <pre>
 * {@code
 * @RefundAudit("SUCCESS")
 * public void markSuccess(Long recordId, String thirdPartyRefundNo) {
 *     // ...
 * }
 *
 * @RefundAudit("FAILED")
 * public void markFailed(Long recordId, String failReason) {
 *     // ...
 * }
 * }
 * </pre>
 *
 * <p><b>注意：</b>
 * <ul>
 *   <li>审计日志记录失败不会影响主业务（异常被捕获并记录 warn 日志）</li>
 *   <li>建议配合 {@code @Transactional} 使用，确保审计与业务在同一事务边界内</li>
 * </ul>
 *
 * @author mall
 * @see com.mall.aspect.RefundAuditAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RefundAudit {

    /**
     * 操作类型
     *
     * @return SUCCESS（退款成功）或 FAILED（退款失败）
     */
    String value();
}
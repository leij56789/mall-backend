package com.mall.pay.port;

import com.mall.pay.facade.dto.PayCallbackDTO;
import com.mall.pay.facade.dto.RefundCallbackDTO;
import com.mall.pay.port.model.PaymentOrderSnapshot;

import java.util.Map;

/**
 * 支付模块向业务侧的通知接口 + 内部状态转换接口。
 * <p>
 * <b>职责说明：</b>
 * <ul>
 *   <li>onXxx：业务侧通知（对外），未来只保留这些</li>
 *   <li>toXxx：内部状态转换（pay 内部用），未来会重构掉</li>
 * </ul>
 */
public interface PaymentApplicationPort {

    // ==================== 业务侧通知（对外） ====================

    /** 支付成功（必须实现） */
    void onSuccess(PayCallbackDTO dto);

    /** 支付失败 */
    default void onFailed(PayCallbackDTO dto) {}

    /** 支付单过期关闭 */
    default void onExpired(PayCallbackDTO dto) {}

    /** 支付结果待确认（第三方超时/异常） */
    default void onPendingConfirm(PayCallbackDTO dto) {}

    /** 退款成功 */
    default void onRefundSuccess(RefundCallbackDTO dto) {}

    /** 退款失败 */
    default void onRefundFailed(RefundCallbackDTO dto) {}

    /** 退款处理中 */
    default void onRefundProcessing(RefundCallbackDTO dto) {}

    // ==================== 内部状态转换（历史遗留，未来重构） ====================

    /**
     * PENDING_CONFIRM → WAITING
     * @deprecated 内部用，未来由 PaymentStateMachine 直接调 Repository
     */
    @Deprecated
    void toWaiting(PaymentOrderSnapshot snapshot, String prepayId, Map<String, Object> extInfo);

    /**
     * 支付状态 → SUCCESS
     * @deprecated 内部用
     */
    @Deprecated
    void toSuccess(String paymentId, String expectedStatus, String transactionId);

    /**
     * 支付状态 → FAILED
     * @deprecated 内部用
     */
    @Deprecated
    void toFailed(PaymentOrderSnapshot snapshot, String expectedStatus);
}
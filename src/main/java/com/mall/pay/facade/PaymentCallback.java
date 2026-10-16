package com.mall.pay.facade;

import com.mall.pay.facade.dto.PayCallbackDTO;
import com.mall.pay.facade.dto.RefundCallbackDTO;

/**
 * 支付回调 —— 业务侧实现。
 * <p>
 * 支付模块在状态变更后，异步调用这些方法通知业务侧。
 * <p>
 * <b>注意：</b>
 * <ul>
 *   <li>onSuccess 和 onRefundSuccess 是必须实现的（核心业务）</li>
 *   <li>其他方法提供默认空实现，业务侧按需覆写</li>
 *   <li>业务侧实现必须幂等（同一笔可能多次通知）</li>
 * </ul>
 */
public interface PaymentCallback {

    // ==================== 支付回调 ====================

    /**
     * 支付成功。
     * <p>
     * 业务侧：更新订单状态、发货、通知用户等。
     */
    void onSuccess(PayCallbackDTO dto);

    /**
     * 支付失败（第三方明确返回失败）。
     */
    default void onFailed(PayCallbackDTO dto) {}

    /**
     * 支付单过期关闭。
     */
    default void onExpired(PayCallbackDTO dto) {}

    /**
     * 支付结果待确认（第三方超时/异常，需补偿查询）。
     */
    default void onPendingConfirm(PayCallbackDTO dto) {}

    // ==================== 退款回调 ====================

    /**
     * 退款成功。
     */
    void onRefundSuccess(RefundCallbackDTO dto);

    /**
     * 退款失败。
     */
    default void onRefundFailed(RefundCallbackDTO dto) {}

    /**
     * 退款处理中（可选）。
     */
    default void onRefundProcessing(RefundCallbackDTO dto) {}
}
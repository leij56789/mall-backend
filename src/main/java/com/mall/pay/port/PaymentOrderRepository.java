package com.mall.pay.port;

import com.mall.pay.port.model.PaymentOrderSnapshot;

/**
 * 支付单数据访问接口。业务系统提供实现（通常包装自己的 Mapper）。
 */
public interface PaymentOrderRepository {

    /**
     * 按 paymentId 查询（带 FOR UPDATE 悲观锁）。
     * ⚠️ 必须在事务中调用。
     */
    PaymentOrderSnapshot findByPaymentIdForUpdate(String paymentId);

    /**
     * 按 paymentId 查询（无锁）。
     */
    PaymentOrderSnapshot findByPaymentId(String paymentId);
}
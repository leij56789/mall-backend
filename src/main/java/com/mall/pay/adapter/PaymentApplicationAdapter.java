package com.mall.pay.adapter;

import com.mall.pay.entity.PaymentOrder;
import com.mall.enums.OrderStatus;
import com.mall.pay.mapper.PaymentOrderMapper;
import com.mall.pay.facade.dto.PayCallbackDTO;
import com.mall.pay.facade.dto.RefundCallbackDTO;
import com.mall.pay.port.PaymentApplicationPort;
import com.mall.pay.port.model.PaymentOrderSnapshot;
import com.mall.service.OrderPaymentOrchestrationService;
import com.mall.service.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 系统 A 的 PaymentApplicationPort 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApplicationAdapter implements PaymentApplicationPort {

    private final PaymentOrchestrationService orchestrationService;
    private final PaymentOrderMapper paymentOrderMapper;
    private final OrderPaymentOrchestrationService orderPaymentOrchestrationService;

    // ==================== 对外：业务侧通知 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSuccess(PayCallbackDTO dto) {
        log.info("PaymentApplicationAdapter.onSuccess: paymentId={}, bizType={}, bizId={}, amount={}",
                dto.getPaymentId(), dto.getBizType(), dto.getBizId(), dto.getAmount());

        // 1. 查 payment_order
        PaymentOrder po = paymentOrderMapper.selectByPaymentIdForUpdate(dto.getPaymentId());
        if (po == null) {
            log.error("支付单不存在: paymentId={}", dto.getPaymentId());
            return;
        }

        // 2. 判断"是否系统 A 老业务"（有 orderId）
        if (po.getOrderId() == null) {
            // PayFacade 新业务：不更新 orders（系统 B 自己处理 onSuccess）
            log.info("支付单无关联订单（PayFacade 创建），跳过订单更新: paymentId={}, bizType={}, bizId={}",
                    dto.getPaymentId(), dto.getBizType(), dto.getBizId());
            return;
        }

        // 3. 系统 A 老业务：更新 orders
        try {
            Long paymentIdAsLong = Long.valueOf(dto.getPaymentId());
            orderPaymentOrchestrationService.updateOrderToPaidFromStatus(paymentIdAsLong, OrderStatus.PENDING);
            log.info("订单更新成功: paymentId={}, orderId={}", dto.getPaymentId(), po.getOrderId());
        } catch (Exception e) {
            log.error("更新订单失败: paymentId={}, orderId={}", dto.getPaymentId(), po.getOrderId(), e);
            throw e;
        }
    }

    @Override
    public void onRefundSuccess(RefundCallbackDTO dto) {
        log.info("PaymentApplicationAdapter.onRefundSuccess: refundId={}, paymentId={}",
                dto.getRefundId(), dto.getPaymentId());
        // 系统 A 的退款后续逻辑（如果有，添加）
    }

    // ==================== 内部：状态转换（历史遗留） ====================

    @Override
    public void toWaiting(PaymentOrderSnapshot snapshot, String prepayId, Map<String, Object> extInfo) {
        PaymentOrder po = loadEntity(snapshot.getId());
        orchestrationService.updatePaymentStatusToWaitingFromStatusOnTransactional(
                po, po.getStatus(), prepayId, extInfo);
    }

    @Override
    public void toSuccess(String paymentId, String expectedStatus, String transactionId) {
        orchestrationService.updatePaymentStatusToSuccessFromStatusOnTransactional(
                paymentId, expectedStatus, transactionId);
    }

    @Override
    public void toFailed(PaymentOrderSnapshot snapshot, String expectedStatus) {
        PaymentOrder po = loadEntity(snapshot.getId());
        orchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(po, expectedStatus);
    }

    private PaymentOrder loadEntity(Long id) {
        PaymentOrder po = paymentOrderMapper.selectById(id);
        if (po == null) {
            throw new IllegalStateException("PaymentOrder not found: id=" + id);
        }
        return po;
    }
}
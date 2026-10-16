package com.mall.pay.adapter;

import com.mall.common.BusinessException;
import com.mall.entity.PaymentOrder;
import com.mall.enums.OrderStatus;
import com.mall.enums.ResultCode;
import com.mall.mapper.PaymentOrderMapper;
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

        Long orderId = null;

        // 1. 优先用 bizId
        if (dto.getBizId() != null && !dto.getBizId().isEmpty()) {
            try {
                orderId = Long.valueOf(dto.getBizId());
            } catch (NumberFormatException ignored) {
                log.warn("bizId 不是数字: bizId={}", dto.getBizId());
            }
        }

        // 2. 兜底：通过 paymentId 查 orderId
        if (orderId == null) {
            PaymentOrder po = paymentOrderMapper.selectByPaymentIdForUpdate(dto.getPaymentId());
            if (po != null) {
                orderId = po.getOrderId();
            }
        }

        if (orderId == null) {
            log.error("无法确定 orderId: paymentId={}, bizId={}", dto.getPaymentId(), dto.getBizId());
            throw new BusinessException(ResultCode.PARAM_INVALID, "无法确定 orderId");
        }

        // 3. 更新订单为"已支付"
        try {
            Long paymentIdAsLong = Long.valueOf(dto.getPaymentId());
            orderPaymentOrchestrationService.updateOrderToPaidFromStatus(paymentIdAsLong, OrderStatus.PENDING);
        } catch (NumberFormatException e) {
            log.error("paymentId 不是数字: paymentId={}", dto.getPaymentId());
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
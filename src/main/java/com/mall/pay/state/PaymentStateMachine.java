package com.mall.pay.state;

import com.mall.common.BusinessException;
import com.mall.entity.PaymentOrder;
import com.mall.enums.AuditOperation;
import com.mall.enums.AuditResult;
import com.mall.enums.PaymentStatus;
import com.mall.enums.ResultCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.dto.QueryOrderResponse;
import com.mall.service.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStateMachine {
    private final PaymentOrchestrationService paymentOrchestrationService;

    /**
     * 从 PENDING_CONFIRM 状态转换
     */
    public void transitionFromPendingConfirm(PaymentOrder paymentOrder, QueryOrderResponse thirdResp, PayClient payClient) {
        String tradeState = thirdResp.getTradeState();
        String paymentId = paymentOrder.getPaymentId();
        if(paymentOrder.getOrderId()==null){
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        if(thirdResp==null) {
            throw new BusinessException(ResultCode.PAYMENT_RESPONSE_EMPTY);
        }
        String mapTradeStatus = payClient.mapTradeStatusAfterQueryOrderOnAsyncQueryService(tradeState);
        // ========== 记录审计日志 ==========
//        try {
//            auditService.builder()
//                        .paymentId(paymentId)
//                        .orderId(paymentOrder.getOrderId())
//                        .userId(paymentOrder.getUserId())
//                        .operation(AuditOperation.PENDING_CONFIRM.getCode())
//                        .operationDesc("补偿查询状态流转: " + mapTradeStatus)
//                        .beforeStatus(PaymentStatus.PENDING_CONFIRM.getCode())
//                        .log();
//        } catch (Exception e) {
//            log.warn("记录状态流转前审计日志失败: paymentId={}", paymentId, e);
//        }

        switch (mapTradeStatus) {
            case "WAITING":
                // 第三方已生成 prepay_id，但用户未支付

                if(thirdResp.getPrepayId()==null){
                    throw new BusinessException(ResultCode.PAYMENT_RESPONSE_ERROR);
                }
                paymentOrchestrationService.updatePaymentStatusToWaitingFromStatusOnTransactional(paymentOrder,paymentOrder.getStatus(),thirdResp.getPrepayId(),thirdResp.getExtInfo());
                log.info("PENDING_CONFIRM → WAITING: paymentId={}", paymentId);
                // 审计：流转到 WAITING
//                auditService.builder()
//                            .paymentId(paymentId)
//                            .orderId(paymentOrder.getOrderId())
//                            .userId(paymentOrder.getUserId())
//                            .operation(AuditOperation.PENDING_CONFIRM.getCode())
//                            .operationDesc("PENDING_CONFIRM → WAITING（用户未支付，等待用户付款）")
//                            .beforeStatus(PaymentStatus.PENDING_CONFIRM.getCode())
//                            .afterStatus(PaymentStatus.WAITING.getCode())
//                            .result(AuditResult.PROCESSING.getCode())
//                            .log();
                break;
            case "SUCCESS":
                // 用户已支付成功（回调可能丢失），直接走支付成功流程
                // ✅ 真正成功：更新订单为 PAID
                paymentOrchestrationService.updatePaymentStatusToSuccessFromStatusOnTransactional(paymentId,PaymentStatus.PENDING_CONFIRM.getCode(),thirdResp.getTransactionId());
                log.info("PENDING_CONFIRM → SUCCESS (via query): paymentId={}", paymentId);
                // 审计：流转到 SUCCESS
//                auditService.builder()
//                            .paymentId(paymentId)
//                            .orderId(paymentOrder.getOrderId())
//                            .userId(paymentOrder.getUserId())
//                            .operation(AuditOperation.PENDING_CONFIRM.getCode())
//                            .operationDesc("PENDING_CONFIRM → SUCCESS（补偿查询确认支付成功）")
//                            .beforeStatus(PaymentStatus.PENDING_CONFIRM.getCode())
//                            .afterStatus(PaymentStatus.SUCCESS.getCode())
//                            .result(AuditResult.SUCCESS.getCode())
//                            .log();
                break;

            case "FAILED":
                paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(paymentOrder,PaymentStatus.PENDING_CONFIRM.getCode());
                log.info("PENDING_CONFIRM → FAILED (via query): paymentId={}", paymentId);
                // 审计：流转到 FAILED
//                auditService.builder()
//                            .paymentId(paymentId)
//                            .orderId(paymentOrder.getOrderId())
//                            .userId(paymentOrder.getUserId())
//                            .operation(AuditOperation.PENDING_CONFIRM.getCode())
//                            .operationDesc("PENDING_CONFIRM → FAILED（补偿查询确认支付失败）")
//                            .beforeStatus(PaymentStatus.PENDING_CONFIRM.getCode())
//                            .afterStatus(PaymentStatus.FAILED.getCode())
//                            .result(AuditResult.FAIL.getCode())
//                            .log();
                break;
//            case "CLOSED":
//                // 明确失败或不存在，转为 FAILED，允许用户重试
//                paymentOrderService.cancelTimeoutPaymentByPaymentOrderFromPendingConfirm(paymentOrder,PaymentStatus.PENDING_CONFIRM);
//                log.info("PENDING_CONFIRM → CLOSED: paymentId={}, reason={}", paymentId, tradeState);
//                break;

            default:
                // UNKNOWN 或其他状态，保留 PENDING_CONFIRM，不处理（等待下次定时扫描）
                log.info("查询结果未知，保留 PENDING_CONFIRM: paymentId={}, tradeState={}",
                        paymentId, tradeState);
                // 审计：状态不变，仅记录
//                auditService.builder()
//                            .paymentId(paymentId)
//                            .orderId(paymentOrder.getOrderId())
//                            .userId(paymentOrder.getUserId())
//                            .operation(AuditOperation.PENDING_CONFIRM.getCode())
//                            .operationDesc("PENDING_CONFIRM 保持（第三方返回未知状态: " + tradeState + "）")
//                            .beforeStatus(PaymentStatus.PENDING_CONFIRM.getCode())
//                            .afterStatus(PaymentStatus.PENDING_CONFIRM.getCode())
//                            .result(AuditResult.PROCESSING.getCode())
//                            .log();
                break;
        }
    }
}
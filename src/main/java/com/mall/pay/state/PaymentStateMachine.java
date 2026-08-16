package com.mall.pay.state;

import com.mall.common.BusinessException;
import com.mall.entity.PaymentOrder;
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
        switch (mapTradeStatus) {
            case "WAITING":
                // 第三方已生成 prepay_id，但用户未支付

                if(thirdResp.getPrepayId()==null){
                    throw new BusinessException(ResultCode.PAYMENT_RESPONSE_ERROR);
                }
                paymentOrchestrationService.updatePaymentStatusToWaitingFromStatusOnTransactional(paymentOrder,paymentOrder.getStatus(),thirdResp.getPrepayId(),thirdResp.getExtInfo());
                log.info("PENDING_CONFIRM → WAITING: paymentId={}", paymentId);
                break;
            case "SUCCESS":
                // 用户已支付成功（回调可能丢失），直接走支付成功流程
                // ✅ 真正成功：更新订单为 PAID
                paymentOrchestrationService.updatePaymentStatusToSuccessFromStatusOnTransactional(paymentId,PaymentStatus.PENDING_CONFIRM.getCode(),thirdResp.getTransactionId());
                log.info("PENDING_CONFIRM → SUCCESS (via query): paymentId={}", paymentId);
                break;

            case "FAILED":
                paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(paymentOrder,PaymentStatus.PENDING_CONFIRM.getCode());
                log.info("PENDING_CONFIRM → FAILED (via query): paymentId={}", paymentId);
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
                break;
        }
    }
}
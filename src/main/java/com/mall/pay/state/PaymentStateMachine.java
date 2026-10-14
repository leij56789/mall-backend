package com.mall.pay.state;

import com.mall.common.BusinessException;
import com.mall.enums.PaymentStatus;
import com.mall.enums.ResultCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.dto.QueryOrderResponse;
import com.mall.pay.port.PaymentApplicationPort;
import com.mall.pay.port.model.PaymentOrderSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStateMachine {
    private final PaymentApplicationPort paymentApplicationPort;   // ← 换

    public void transitionFromPendingConfirm(PaymentOrderSnapshot snapshot, QueryOrderResponse thirdResp, PayClient payClient) {
        String tradeState = thirdResp.getTradeState();
        String paymentId = snapshot.getPaymentId();
        if (snapshot.getOrderId() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        if (thirdResp == null) {
            throw new BusinessException(ResultCode.PAYMENT_RESPONSE_EMPTY);
        }
        String mapTradeStatus = payClient.mapTradeStatusAfterQueryOrderOnAsyncQueryService(tradeState);

        switch (mapTradeStatus) {
            case "WAITING":
                if (thirdResp.getPrepayId() == null) {
                    throw new BusinessException(ResultCode.PAYMENT_RESPONSE_ERROR);
                }
                paymentApplicationPort.toWaiting(snapshot, thirdResp.getPrepayId(), thirdResp.getExtInfo());
                log.info("PENDING_CONFIRM → WAITING: paymentId={}", paymentId);
                break;
            case "SUCCESS":
                paymentApplicationPort.toSuccess(paymentId, PaymentStatus.PENDING_CONFIRM.getCode(), thirdResp.getTransactionId());
                log.info("PENDING_CONFIRM → SUCCESS (via query): paymentId={}", paymentId);
                break;
            case "FAILED":
                paymentApplicationPort.toFailed(snapshot, PaymentStatus.PENDING_CONFIRM.getCode());
                log.info("PENDING_CONFIRM → FAILED (via query): paymentId={}", paymentId);
                break;
            default:
                log.info("查询结果未知，保留 PENDING_CONFIRM: paymentId={}, tradeState={}", paymentId, tradeState);
                break;
        }
    }
}
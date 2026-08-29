package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.Orders;
import com.mall.enums.PaymentStatus;
import com.mall.pay.client.PayClient;
import com.mall.pay.dto.*;
import com.mall.entity.PaymentOrder;
import com.mall.mq.message.PaymentTimeoutMessage;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

/**
* @author jiaolei
* @description 针对表【payment_order】的数据库操作Service
* @createDate 2026-07-18 14:37:12
*/
public interface PaymentOrderService extends IService<PaymentOrder> {

    PaymentResponse createPayment(@Valid PaymentCreateRequest request);

    PaymentCallbackResponse handleCallback(@Valid PaymentCallbackRequest callbackRequest);

    PaymentStatusResponse getPaymentStatus(String paymentId);

    void cancelTimeoutPayment(PaymentTimeoutMessage message);

    void cancelTimeoutPaymentByPaymentOrderFromPendingConfirm(PaymentOrder paymentOrder, PaymentStatus paymentStatus);

    void getQrCode(String paymentId, int width, int height, HttpServletResponse response) throws IOException;

    PayClient.RefundResponse refund(PayClient.RefundRequest request);
//    void updatePaymentStatusToSuccessFromStatus(String paymentId,PaymentStatus paymentStatus,String transactionId);

//    void handleSuccess(String outTradeNo, String tradeNo);

//    void handleFailed(String outTradeNo, String 交易关闭);
}

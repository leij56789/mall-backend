package com.mall.pay.event;

import com.mall.common.sse.SseSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentStatusEventListener {
    
    @Autowired
    private SseSessionManager sseManager;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
        sseManager.pushStatus(event.getPaymentId(), event.getNewStatus());
    }
}
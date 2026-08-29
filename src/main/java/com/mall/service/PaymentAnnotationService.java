package com.mall.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.AuditLog;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.entity.Orders;
import com.mall.entity.PaymentOrder;
import com.mall.enums.AuditOperation;
import com.mall.enums.AuditTargetType;
import com.mall.enums.PaymentStatus;
import com.mall.enums.ResultCode;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.producer.PaymentTimeoutProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAnnotationService {
    
    // ? 统一的自注入模式（用 @Lazy 避免循环依赖）
    @Lazy
    @Autowired
    private PaymentAnnotationService self;
    private final ObjectMapper objectMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTimeoutProducer paymentTimeoutProducer;

    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "paymentOrder.paymentId",
            operation = AuditOperation.PAYMENT_WAITING,
            desc = "生成支付凭证（WAITING）"
    )
    @Log("更新状态为waiting")
    public void updatePaymentStatusToWaitingFromStatus(PaymentOrder paymentOrder, String paymentStatus, String prepayId, Orders orders, Map<String,Object> extInfo) {
        String extInfoJson=null;
        if(extInfo!=null){
            try {
                extInfoJson = objectMapper.writeValueAsString(extInfo);
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.PAYMENT_SERIALIZE_FAIL);
            }
        }
        LambdaUpdateWrapper<PaymentOrder> wrapper = new LambdaUpdateWrapper<>();
        int updated = paymentOrderMapper.update(wrapper
                .eq(PaymentOrder::getStatus, paymentStatus)
                .eq(PaymentOrder::getId, paymentOrder.getId())
                .set(PaymentOrder::getStatus, PaymentStatus.WAITING.getCode())
                .set(prepayId!=null,PaymentOrder::getPrepayId,prepayId)
                .set(extInfo!=null,PaymentOrder::getExtInfo, extInfoJson));
        if(updated!=1){
            throw new BusinessException(ResultCode.DB_OPERATION_FAIL);
        }
        paymentTimeoutProducer.sendPaymentTimeoutMessage(paymentOrder, orders, RabbitMQConfig.PAYMENT_DELAY_EXCHANGE,RabbitMQConfig.PAYMENT_DELAY_ROUTING_KEY);
    }
}
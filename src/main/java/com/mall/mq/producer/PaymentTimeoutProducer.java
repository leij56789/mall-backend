package com.mall.mq.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.BusinessException;
import com.mall.config.MessageProperties;
import com.mall.entity.BrokerMessageLog;
import com.mall.entity.Orders;
import com.mall.entity.PaymentOrder;
import com.mall.enums.MessageStatus;
import com.mall.enums.ResultCode;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.message.OrderTimeoutMessage;
import com.mall.mq.message.PaymentTimeoutMessage;
import com.mall.service.BrokerMessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 支付超时消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final BrokerMessageLogService brokerMessageLogService;
    private final GenericMessageProducer genericMessageProducer;
    private final MessageProperties messageProperties;

    /**
     * 发送支付超时延迟消息
     * 消息在 delay.queue 停留 TTL 后自动转发到 payment.timeout.queue
     *
     *
     */

    @Transactional
    public void sendPaymentTimeoutMessage(PaymentOrder paymentOrder,Orders orders, String exchange, String routingKey) {
        //orders不能为空
        PaymentTimeoutMessage message = PaymentTimeoutMessage
                .builder()
                .paymentId(paymentOrder.getPaymentId())
                .orderId(orders.getId())
                .userId(orders.getUserId())
                .orderType(orders.getOrderType())
                .bookId(orders.getBookId())
                .quantity(orders.getQuantity())
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .expireTimestamp(paymentOrder.getExpiredAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .sendTimestamp(System.currentTimeMillis())
                .build();
        if(exchange==null||routingKey==null){
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        String json = null;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.MESSAGE_SERIALIZE_FAIL);
        }
        //保存到本地事务表(状态：待发送)
        BrokerMessageLog messageLog = BrokerMessageLog.builder()
                                                      .orderId(orders.getId())
                                                      .messageId(message.getMessageId())
                                                      .exchange(exchange)
                                                      .routingKey(routingKey)
                                                      .messageBody(json)
                                                      .delayTime((int) messageProperties.getPaymentOrderDelayTimeS().toMillis())
                                                      .status(MessageStatus.PENDING.getCode())
                                                      .retryCount(messageProperties.getInitialRetryCount())
                                                      .maxRetry(messageProperties.getMaxRetry())
                                                      .nextRetryTime(LocalDateTime.now().plusSeconds(messageProperties.getInitialRetryDelaySeconds()))
                                                      .build();
        //把消息存到本地事务表
        boolean saved = brokerMessageLogService.save(messageLog);
        if(!saved){
            throw new BusinessException(ResultCode.MESSAGE_INSERT_FAIL);
        }
        //事务提交后异步发送
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        genericMessageProducer.trySend(messageLog);
                    }
                }
        );

    }
}
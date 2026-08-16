package com.mall.mq.producer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.config.MessageProperties;
import com.mall.entity.BrokerMessageLog;
import com.mall.entity.Orders;
import com.mall.entity.SeckillBook;
import com.mall.entity.SeckillRecord;
import com.mall.enums.MessageStatus;
import com.mall.enums.ResultCode;
import com.mall.mapper.BrokerMessageLogMapper;
import com.mall.mapper.SeckillBookMapper;
import com.mall.mapper.SeckillRecordMapper;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.message.OrderTimeoutMessage;
import com.mall.service.AlertService;
import com.mall.service.BrokerMessageLogService;
import com.mall.service.RedisRollbackService;
import com.mall.service.SeckillBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor//注入ObjectMapper
public class SeckillOrderMessageProducer {
    private final MessageProperties messageProperties;

    private final RabbitTemplate rabbitTemplate;
    private final BrokerMessageLogService brokerMessageLogService;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;
    private final RedisRollbackService redisRollbackService;

    private final GenericMessageProducer genericMessageProducer;
    private final SeckillBookMapper seckillBookMapper;
    private final SeckillRecordMapper seckillRecordMapper;
    private final BrokerMessageLogMapper brokerMessageLogMapper;

    @Log("秒杀订单超时重试消息生产者")
    public void sendOrderTimeoutMessage(Orders orders,String exchange,String routingKey) {
        //orders不能为空
        log.info("time={},timestamp={}",orders.getExpireTime(),orders.getExpireTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        if(exchange==null||routingKey==null){
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        OrderTimeoutMessage message = OrderTimeoutMessage.builder()
                .orderId(orders.getId())
                .bookId(orders.getBookId())
                .quantity(orders.getQuantity())
                .orderType(orders.getOrderType())
                .userId(orders.getUserId())
                .expireTimestamp(orders.getExpireTime()
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .createTimestamp(System.currentTimeMillis())
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .build();

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
                .delayTime(messageProperties.getSeckillDelayTime().intValue())
                .status(MessageStatus.PENDING.getCode())
                .retryCount(messageProperties.getInitialRetryCount())
                .maxRetry(messageProperties.getMaxRetry())
                .nextRetryTime(LocalDateTime.now().plusSeconds(messageProperties.getInitialRetryDelaySeconds()))
                .build();
        if(messageLog==null){
            log.error("messageLog={}",messageLog);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        //把消息存到本地事务表
        boolean saved = brokerMessageLogService.save(messageLog);
        if(!saved){
            throw new BusinessException(ResultCode.MESSAGE_INSERT_FAIL);
        }
        //事务提交后异步发送
        String finalJson = json;
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
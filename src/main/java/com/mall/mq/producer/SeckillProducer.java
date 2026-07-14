package com.mall.mq.producer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.config.MessageProperties;
import com.mall.entity.BrokerMessageLog;
import com.mall.enums.MessageStatus;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.message.SeckillMessage;
import com.mall.service.AlertService;
import com.mall.service.BrokerMessageLogService;
import com.mall.service.RedisRollbackService;
import com.mall.service.SeckillBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final BrokerMessageLogService brokerMessageLogService;
    private final AlertService alertService;
    private final RedisRollbackService redisRollbackService;
    private final MessageProperties messageProperties;
    private final GenericMessageProducer genericMessageProducer;

    /**
     * 发送秒杀消息（传入 SeckillMessage）
     */
    @Log("秒杀消息消费者")
    public void sendSeckillMessage(SeckillMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            // 1. 保存本地消息表（PENDING）
            BrokerMessageLog messageLog = BrokerMessageLog.builder()
                    .userId(message.getUserId())
                    .bookId(message.getBookId())
                    .messageId(message.getMessageId())
                    .exchange(RabbitMQConfig.SECKILL_EXCHANGE)
                    .routingKey(RabbitMQConfig.SECKILL_ROUTING_KEY)
                    .messageBody(json)
                    .status(MessageStatus.PENDING.getCode())
                    .retryCount(0)
                    .maxRetry(3)
                    .nextRetryTime(LocalDateTime.now().plusSeconds(30))
                    .build();
            brokerMessageLogService.save(messageLog);

            // 2. 事务提交后异步发送
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            genericMessageProducer.trySend(messageLog);
                        }
                    }
            );

            log.info("秒杀消息已保存到本地消息表：userId={}, bookId={}, messageId={}",
                    message.getUserId(), message.getBookId(), message.getMessageId());

        } catch (Exception e) {
            log.error("秒杀消息发送失败：userId={}, bookId={}",
                    message.getUserId(), message.getBookId(), e);
            throw new RuntimeException("秒杀消息发送失败", e);
        }
    }

//    /**
//     * 异步发送消息
//     */
//    @Async
//    public void trySend(BrokerMessageLog messageLog) {
//        Long orderId = messageLog.getOrderId();
//        String json = messageLog.getMessageBody();
//        String messageId = messageLog.getMessageId();
//        Integer oldRetryCount = messageLog.getRetryCount();
//        boolean locked = brokerMessageLogService.tryLockMessage(messageId, oldRetryCount, messageLog.getExchange(), messageLog.getRoutingKey());
//        if(!locked){
//            log.warn("乐观锁冲突，消息已被其他线程处理，放弃本次发送：messageId={}",messageId);
//            return;
//        }
//        try {
//            rabbitTemplate.convertAndSend(
//                    messageLog.getExchange(),
//                    messageLog.getRoutingKey(),
//                    json, msg->{
//                        msg.getMessageProperties().setMessageId(messageId);
//                        return msg;
//                    }
//            );
//            brokerMessageLogService.updateStatusAndRetryCount(messageLog.getMessageId(),MessageStatus.SENT.getCode(),
//                    messageLog.getExchange(),messageLog.getRoutingKey(),oldRetryCount,null);
//            log.info("消息发送成功：messageId={}", messageLog.getMessageId());
//
//
//        } catch (Exception e) {
//            log.error("消息发送失败，等待补偿：messageId={}", messageLog.getMessageId(), e);
//            if(oldRetryCount+1>= messageProperties.getMaxRetry()){
//                seckillBookService.rollbackRedisSeckill(messageLog.getBookId(), messageLog.getUserId());
//                brokerMessageLogService.updateStatusAndRetryCount(messageLog.getMessageId(),MessageStatus.FINAL_FAILED.getCode(),
//                        messageLog.getExchange(),messageLog.getRoutingKey(),oldRetryCount,null);
//                //不同消费者需要不同处理的区域
//
//                if(messageLog.getExchange().equals(RabbitMQConfig.SECKILL_EXCHANGE)
//                        ||messageLog.getExchange().equals(RabbitMQConfig.SECKILL_DELAY_EXCHANGE)){
//                    //redis回滚
//                    seckillBookService.rollbackRedisSeckill(messageLog.getBookId(), messageLog.getUserId());
//                }
//
//                //
//                log.error("消息超过最大重试次数，触发告警：messageId={}", messageLog.getMessageId());
//                alertService.sendAlert(
//                        "消息重试发送三次失败",
//                        String.format("messageId=%s, userId=%d, bookId=%d, exchange=%s, routingKey=%s",
//                                messageLog.getMessageId(),
//                                messageLog.getUserId(),
//                                messageLog.getBookId(),
//                                messageLog.getExchange(),
//                                messageLog.getRoutingKey()
//                        )
//                );
//            }else{
//                brokerMessageLogService.updateStatusAndRetryCount(messageLog.getMessageId(),MessageStatus.FAILED.getCode(),
//                        messageLog.getExchange(),messageLog.getRoutingKey(),oldRetryCount,LocalDateTime.now()
//                                .plusMinutes(messageProperties.getRetryIntervalMinutes()));
//                log.warn("消息发送失败，等待补偿重试：messageId={}, retryCount={}",
//                        messageLog.getMessageId(), oldRetryCount);
//            }
//        }
//    }
}
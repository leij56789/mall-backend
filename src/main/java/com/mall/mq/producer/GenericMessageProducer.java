package com.mall.mq.producer;

import com.mall.annotation.Log;
import com.mall.config.MessageProperties;
import com.mall.entity.BrokerMessageLog;
import com.mall.enums.MessageStatus;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.service.AlertService;
import com.mall.service.BrokerMessageLogService;
import com.mall.service.RedisRollbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenericMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final BrokerMessageLogService brokerMessageLogService;
    private final AlertService alertService;
    private final MessageProperties messageProperties;
//    private final SeckillBookService seckillBookService;  // 用于秒杀回滚
    private final RedisRollbackService redisRollbackService;
    // 未来新增类型只需注入对应的 Service

    @Log("生产者发送消息")
    @Async("taskExecutor")
    public void trySend(BrokerMessageLog messageLog) {
//        log.info("trySend taskHash={}，当前编号={}", MDC.get("taskHash"),MDC.get("callSeq"));
        Long orderId = messageLog.getOrderId();
        String json = messageLog.getMessageBody();
        String messageId = messageLog.getMessageId();
        Integer oldRetryCount = messageLog.getRetryCount();
        boolean locked = brokerMessageLogService.tryLockMessage(messageId, oldRetryCount, messageLog.getExchange(), messageLog.getRoutingKey());
        if(!locked){
            log.warn("乐观锁冲突，消息已被其他线程处理，放弃本次发送：messageId={}",messageId);
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    messageLog.getExchange(),
                    messageLog.getRoutingKey(),
                    json, msg->{
                        msg.getMessageProperties().setMessageId(messageId);
                        return msg;
                    }
            );
            brokerMessageLogService.updateStatusAndRetryCount(messageLog.getMessageId(),MessageStatus.SENT.getCode(),
                    messageLog.getExchange(),messageLog.getRoutingKey(),oldRetryCount,null);
            log.info("消息发送成功：messageId={}", messageLog.getMessageId());


        } catch (Exception e) {
            log.error("消息发送失败，等待补偿：messageId={}", messageLog.getMessageId(), e);
            if(oldRetryCount+1>= messageProperties.getMaxRetry()){
                //不同消费者需要不同处理的区域

                if(messageLog.getExchange().equals(RabbitMQConfig.SECKILL_EXCHANGE)
                        ||messageLog.getExchange().equals(RabbitMQConfig.SECKILL_DELAY_EXCHANGE)){
                    //redis回滚
                    redisRollbackService.rollbackRedisSeckillOrThrow(messageLog.getBookId(), messageLog.getUserId(),messageLog.getOrderId());
                }

                //
                brokerMessageLogService.updateStatusAndRetryCount(messageLog.getMessageId(),MessageStatus.FINAL_FAILED.getCode(),
                        messageLog.getExchange(),messageLog.getRoutingKey(),oldRetryCount,null);
                log.error("消息超过最大重试次数，触发告警：messageId={}", messageLog.getMessageId());
                alertService.sendAlert(
                        "消息重试发送三次失败",
                        String.format("messageId=%s, userId=%d, bookId=%d, exchange=%s, routingKey=%s",
                                messageLog.getMessageId(),
                                messageLog.getUserId(),
                                messageLog.getBookId(),
                                messageLog.getExchange(),
                                messageLog.getRoutingKey()
                        )
                );
            }else{
                brokerMessageLogService.updateStatusAndRetryCount(messageLog.getMessageId(),MessageStatus.FAILED.getCode(),
                        messageLog.getExchange(),messageLog.getRoutingKey(),oldRetryCount,LocalDateTime.now()
                                .plusMinutes(messageProperties.getRetryIntervalMinutes()));
                log.warn("消息发送失败，等待补偿重试：messageId={}, retryCount={}",
                        messageLog.getMessageId(), oldRetryCount);
            }
        }
    }
}
package com.mall.mq.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.config.MessageProperties;
import com.mall.entity.BrokerMessageLog;
import com.mall.entity.Orders;
import com.mall.enums.MessageStatus;
import com.mall.enums.ResultCode;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.message.OrderTimeoutMessage;
import com.mall.service.AlertService;
import com.mall.service.BrokerMessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
@Slf4j
@Component
@RequiredArgsConstructor//注入ObjectMapper
public class OrderMessageProducer {
    private final MessageProperties messageProperties;
    private static final int RETRY_COUNT = 0;

    private final RabbitTemplate rabbitTemplate;
    private final BrokerMessageLogService brokerMessageLogService;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;

    @Log("订单超时重试消息生产者")
    public void sendOrderTimeoutMessage(Orders orders) {
        OrderTimeoutMessage message = OrderTimeoutMessage.builder()
                .orderId(orders.getId())
                .bookId(orders.getBookId())
                .quantity(orders.getQuantity())
                .expireTimestamp(orders.getExpireTime()
                        .toInstant(ZoneOffset.UTC).toEpochMilli())
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
        BrokerMessageLog log = BrokerMessageLog.builder()
                .orderId(orders.getId())
                .messageId(message.getMessageId())
                .exchange(RabbitMQConfig.ORDERTIMEOUT_EXCHANGE)
                .routingKey(RabbitMQConfig.ORDERTIMEOUT_ROUTING_KEY)
                .messageBody(json)
                .delayTime(messageProperties.getDelayTime().intValue())
                .status(MessageStatus.PENDING.getCode())
                .retryCount(RETRY_COUNT)
                .maxRetry(messageProperties.getMaxRetry())
                .nextRetryTime(LocalDateTime.now().plusSeconds(messageProperties.getInitialRetryDelaySeconds()))
                .build();
        //把消息存到本地事务表
        boolean saved = brokerMessageLogService.save(log);
        if(!saved){
            throw new BusinessException(ResultCode.MESSAGE_INSERT_FAIL);
        }
        //事务提交后异步发送
        String finalJson = json;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        trySend(log);
                    }
                }
        );
    }

    @Async
    public void trySend(BrokerMessageLog brokerMessageLog) {
        Long orderId = brokerMessageLog.getOrderId();
        String json = brokerMessageLog.getMessageBody();
        String messageId = brokerMessageLog.getMessageId();
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.DELAY_EXCHANGE,
                RabbitMQConfig.DELAY_ROUTING_KEY,
                    json, msg->{
                    msg.getMessageProperties().setMessageId(messageId);
                    return msg;
                    }
            );

            boolean updated = brokerMessageLogService.lambdaUpdate()
                    .set(BrokerMessageLog::getStatus, MessageStatus.SENT.getCode())
                    .eq(BrokerMessageLog::getMessageId, messageId).update();
            if(!updated){
                log.error("数据库更新失败：orderId={},messageId={}", orderId,messageId);
            }else{
                log.info("订单超时消息发送成功：orderId={},messageId={}", orderId,messageId);
            }

        } catch (Exception e) {
            log.error("订单超时消息发送失败，等待补偿：orderId={}", orderId,e);
            //更新重试次数和状态（由定时任务同一补偿）
            //数据库里存的重试次数不参与mq重试次数的逻辑
            Integer retryCount = brokerMessageLog.getRetryCount()+1;
            int newMessageStatus= retryCount >=messageProperties.getMaxRetry()
                    ?MessageStatus.FINAL_FAILED.getCode() :MessageStatus.FAILED.getCode();
            boolean updated = brokerMessageLogService.lambdaUpdate()
                    .set(BrokerMessageLog::getStatus, newMessageStatus)
                    .set(BrokerMessageLog::getRetryCount, retryCount)
                    .set(BrokerMessageLog::getNextRetryTime,LocalDateTime.now()
                            .plusMinutes(messageProperties.getRetryIntervalMinutes()))
                    .eq(BrokerMessageLog::getMessageId, messageId).update();
            if(!updated){
                log.error("数据库更新失败：orderId={},messageId={}", orderId,messageId);
            }else{
                log.info("数据库更新成功：orderId={},messageId={}", orderId,messageId);
            }
            if(retryCount>=messageProperties.getMaxRetry()){
                log.error("消息已超最大重试次数,人工介入：orderId={},messageId={}", orderId,messageId);
                //只打了日志，没有实际触发告警，(钉钉，飞书，邮件)
                String title = "消息发送超过最大重试次数";
                String content = String.format(
                        "订单ID: %s\\n消息ID: %s\\n重试次数: %d\\n最大重试: %d\\n状态: %s",
                        orderId,
                        messageId,
                        retryCount,
                        messageProperties.getMaxRetry(),
                        "FINAL_FAILED"
                );
                alertService.sendAlert(title,content);
            }
        }
    }
}
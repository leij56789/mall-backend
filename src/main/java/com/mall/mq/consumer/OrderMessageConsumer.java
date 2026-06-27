package com.mall.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.config.MessageProperties;
import com.mall.enums.ResultCode;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.message.OrderTimeoutMessage;
import com.mall.service.AlertService;
import com.mall.service.OrdersService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderMessageConsumer {
    private final OrdersService orderService;
    private final ObjectMapper objectMapper;
    private final MessageProperties messageProperties;
    private final AlertService alertService;
    @Log("订单超时消息消费者")
    @RabbitListener(queues = RabbitMQConfig.ORDERTIMEOUT_QUEUE)
    public void handleOrderTimeout(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Long orderId=null;
        long retryCount = getRetryCount(message);
        Integer maxRetry = messageProperties.getMaxRetry();
        if(retryCount >= maxRetry){
            log.error("超过最大重试次数（{}），进入死信队列：retryCount={}", maxRetry,retryCount);
            channel.basicNack(deliveryTag,false,false);
            alertService.sendAlert("消息消费超过最大重试次数",
                    String.format("orderId=%s, retryCount=%d, maxRetry=%d",
                            orderId, retryCount, maxRetry));
            return;
        }
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            OrderTimeoutMessage orderTimeoutMessage =objectMapper.readValue(body, OrderTimeoutMessage.class);
            orderId = orderTimeoutMessage.getOrderId();
            orderService.cancelExpireOrderByOrderTimeMessage(orderTimeoutMessage);
            channel.basicAck(deliveryTag,false);
            log.info("消费成功，orderId={}", orderId);
        } catch (BusinessException e) {
            Integer code = e.getCode();
            if(code.equals(ResultCode.ORDER_UPDATE_FAIL.getCode())||
            code.equals(ResultCode.STOCK_RECOVER_FAIL.getCode())){
                log.warn("乐观锁冲突，消息将延迟重试：orderId={}", orderId);
                channel.basicNack(deliveryTag,false,true);
            }else{
                log.warn("业务异常，确认消息:orderId={},errorCode={},message={}", orderId,code,e.getMessage());
                channel.basicAck(deliveryTag,false);
            }
        } catch (Exception e) {
            log.error("系统异常，消息将延迟重试：orderId={},retryCount={}", orderId,retryCount);
            channel.basicNack(deliveryTag,false,true);
        }
    }
    private long getRetryCount(Message message){
        Object retryCount = message.getMessageProperties().getHeader("x-retry-count");
        if(retryCount==null){
            return 0;
        }
        try {
            return Long.parseLong(retryCount.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
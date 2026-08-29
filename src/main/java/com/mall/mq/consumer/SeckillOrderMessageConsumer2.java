package com.mall.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.common.RedisKeys;
import com.mall.config.MessageProperties;
import com.mall.enums.OrderType;
import com.mall.enums.ResultCode;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.message.OrderTimeoutMessage;
import com.mall.service.AlertService;
import com.mall.service.OrdersService;
import com.mall.service.RedisRollbackService;
import com.mall.service.SeckillBookService;
//import com.mall.utils.RedisUtil;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jiaolei
 * @date 2026-07-06 10:23
 * @description TODO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderMessageConsumer2 {
    private final OrdersService orderService;
    private final ObjectMapper objectMapper;
    private final MessageProperties messageProperties;
    private final AlertService alertService;
    private final RedisTemplate redisTemplate;
//    private final RedisUtil redisUtil;
    // 本地降级缓存（仅 Redis 故障时使用）
    private final ConcurrentHashMap<String, Long> localRetryMap = new ConcurrentHashMap<>();
    private final SeckillBookService seckillBookService;
    private final RedisRollbackService redisRollbackService;


    @Log("秒杀订单超时消息消费者")
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_TIMEOUT_QUEUE)
    public void handleOrderTimeout(Message message, Channel channel) throws IOException {
        log.info("秒杀订单超时消费者");
//        Map<String, Object> headers = message.getMessageProperties().getHeaders();
//        log.info("消费者收到的 Headers: {}", headers);
        Long orderId=null;
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        String retryKey = RedisKeys.MESSAGE_RETRY + messageId;
        Long retryCount = getRetryCountWithFallback(retryKey);
//        log.info("Redis increment 结果：key={}, value={}", retryKey, retryCount);
        Integer maxRetry = messageProperties.getMaxRetry();
        //3.设置过期时间（防止内存泄漏）
        if(retryCount==1){
            redisTemplate.expire(retryKey,RedisKeys.TTL_MESSAGE_RETRY);
        }
        OrderTimeoutMessage orderTimeoutMessage = null;
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            orderTimeoutMessage = objectMapper.readValue(body, OrderTimeoutMessage.class);
        } catch (Exception e) {
            log.warn("业务异常，确认消息:orderId={},message={}", orderId,e.getMessage());
            clearRetryKey(retryKey);
            channel.basicAck(deliveryTag,false);
            return;
        }
        if(retryCount > maxRetry){
            log.error("超过最大重试次数（{}），进入死信队列：retryCount={}", maxRetry,retryCount);
            clearRetryKey(retryKey);
            channel.basicNack(deliveryTag,false,false);
            alertService.sendAlert("消息消费超过最大重试次数",
                    String.format("orderId=%s, retryCount=%d, maxRetry=%d",
                            orderId, retryCount, maxRetry));
            return;
        }
        try {
            orderId = orderTimeoutMessage.getOrderId();
            Integer orderType = orderTimeoutMessage.getOrderType();
            if(orderType==null||orderId==null
                    ||(orderType!= OrderType.NORMAL.getCode()
                    &&orderType!=OrderType.SECKILL.getCode())){
                log.error("消费者消息异常：orderType={},orderId={}",orderType,orderId);
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            }
            if(orderType.equals(OrderType.NORMAL.getCode())){
                orderService.cancelExpireOrderByOrderTimeMessage(orderTimeoutMessage);
            }else{
                orderService.cancelSeckillExpireOrderByOrderTimeMessage(orderTimeoutMessage);
            }
            channel.basicAck(deliveryTag,false);
            log.info("消费成功，orderId={}", orderId);
            redisTemplate.delete(retryKey);
//            throw new RuntimeException("模拟消费失败，触发死信转发测试");
        } catch (BusinessException e) {
            Integer code = e.getCode();
            if(code.equals(ResultCode.STOCK_RECOVER_FAIL.getCode())){
                log.warn("乐观锁冲突，消息将延迟重试：orderId={}", orderId);
                channel.basicNack(deliveryTag,false,true);
            }else{
                log.warn("业务异常，确认消息:orderId={},errorCode={},message={}", orderId,code,e.getMessage());
                clearRetryKey(retryKey);
                channel.basicAck(deliveryTag,false);
            }
        } catch (Exception e) {
//            log.info("key={},value={}",retryKey,redisTemplate.opsForValue().get(retryKey));
            log.error("系统异常，消息将延迟重试：orderId={},retryCount={}", orderId,retryCount);
            channel.basicNack(deliveryTag,false,true);
        }
    }
    private long getRetryCountWithFallback(String retryKey) {
        try {
            // 优先尝试 Redis
            Long count = redisTemplate.opsForValue().increment(retryKey);
            if (count == null) {
                // 理论上不会为 null，但防御处理
                log.warn("Redis 返回 null，使用本地内存计数");
                return localRetryMap.compute(retryKey, (k, v) -> v == null ? 1 : v + 1);
            }
            // 若 Redis 恢复正常，且本地有残留，主动清理
            localRetryMap.remove(retryKey);
            return count;
        } catch (Exception e) {
            // Redis 异常，降级到内存
            log.error("Redis 连接失败，降级到本地内存计数", e);
            alertService.sendAlert("Redis 故障，已降级到本地内存", "retryKey=" + retryKey);
            return localRetryMap.compute(retryKey, (k, v) -> v == null ? 1 : v + 1);
        }
    }

    private void clearRetryKey(String retryKey) {
        try {
            redisTemplate.delete(retryKey);
        } catch (Exception e) {
            log.warn("Redis 删除失败，从本地内存移除", e);
        } finally {
            localRetryMap.remove(retryKey);
        }
    }
}
package com.mall.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.common.trace.context.TraceContext;
import com.mall.config.MessageProperties;
import com.mall.enums.ResultCode;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.mq.message.SeckillMessage;
import com.mall.service.AlertService;
import com.mall.service.RedisRollbackService;
import com.mall.service.SeckillBookService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillConsumer {

    private final SeckillBookService seckillBookService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final AlertService alertService;
    private final MessageProperties messageProperties;
    private final ConcurrentHashMap<String, Long> localRetryMap = new ConcurrentHashMap<>();
    private final RedisRollbackService redisRollbackService;


    @Log("真正秒杀消息消费者")
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void handleSeckill(Message message, Channel channel) throws Exception {
        log.info("秒杀消息消费者");
//        Map<String, Object> headers = message.getMessageProperties().getHeaders();
//        log.info("消费者收到的 Headers: {}", headers);

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        SeckillMessage msg = null;
        // 先解析消息体，以便后面回滚使用
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            msg = objectMapper.readValue(body, SeckillMessage.class);
        } catch (Exception e) {
            // 解析失败直接确认，不重试（坏消息）
            log.error("消息解析失败，确认丢弃：messageId={}", messageId, e);
            alertService.sendAlert("Redis不能回滚,等待补偿", "messageId=" + messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }
        // 重试计数
        String retryKey = "message:retry:" + messageId;
        Long retryCount = getRetryCountWithFallback(retryKey);
        Integer maxRetry = messageProperties.getMaxRetry();
        if (retryCount == 1) {
            long ttl = 5;
            redisTemplate.expire(retryKey, ttl, TimeUnit.MINUTES);
        }
        // ?? 超过最大重试次数 → 回滚 Redis → 死信
        if (retryCount > maxRetry) {
            log.error("超过最大重试次数（{}），进入死信队列：retryCount={}", maxRetry, retryCount);
            // ? 回滚 Redis 资源
            redisRollbackService.rollbackRedisSeckillOrThrow(msg.getBookId(),msg.getUserId(),null);
            clearRetryKey(retryKey);
            channel.basicNack(deliveryTag, false, false);
            alertService.sendAlert("消息消费超过最大重试次数",
                    String.format("userId=%d, bookId=%d, retryCount=%d, maxRetry=%d",
                            msg.getUserId(), msg.getBookId(), retryCount, maxRetry));
            return;
        }
        try {
            log.info("秒杀消息消费,开始生成订单：userId={}, bookId={}, messageId={}",
                    msg.getUserId(), msg.getBookId(), msg.getMessageId());
            seckillBookService.processSeckillOrder(msg);
            channel.basicAck(deliveryTag, false);
//            //删除缓存
//            String userKey= SECKILL_USER_KEY+msg.getBookId()+msg.getUserId();
//            String queueKey = "seckill:queue:" + msg.getBookId();
//            redisTemplate.delete(userKey);
//            redisTemplate.opsForZSet().remove(queueKey,String.valueOf(msg.getUserId()));
            log.info("秒杀消息消费成功：userId={}, bookId={}", msg.getUserId(), msg.getBookId());
        } catch (BusinessException e) {
            Integer code = e.getCode();
            // 库存不足时，processSeckillOrder 内部已经回滚了 Redis
            if (code != null && code.equals(ResultCode.OPTIMISTIC_LOCK_CONFLICT.getCode())) {
                log.error("乐观锁冲突，将重试：userId={}, bookId={}, retryCount={}",
                        msg.getUserId(), msg.getBookId(), retryCount, e);
                channel.basicNack(deliveryTag, false,true);
                clearRetryKey(retryKey);
            } else {
                log.warn("业务异常，消息确认：errorCode={}, errorMsg={}", code, e.getMessage());
                redisRollbackService.rollbackRedisSeckillOrThrow(msg.getBookId(),msg.getUserId(),null);
                channel.basicAck(deliveryTag, false);
                clearRetryKey(retryKey);
            }
        } catch (Exception e) {
            log.error("秒杀消息消费失败，将重试：userId={}, bookId={}, retryCount={}",
                    msg.getUserId(), msg.getBookId(), retryCount, e);
            // 系统异常：重试（由 MQ 的 x-max-delivery-count 控制次数）
            channel.basicNack(deliveryTag, false, true);
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
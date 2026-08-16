package com.mall.service.impl;

import com.mall.common.BusinessException;
import com.mall.common.RedisKeys;
import com.mall.enums.ResultCode;
import com.mall.mq.message.SeckillMessage;
import com.mall.service.AlertService;
import com.mall.service.RedisRollbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.mall.common.SeckillConstants.SECKILL_STOCK_KEY;
import static com.mall.common.SeckillConstants.SECKILL_USER_KEY;

/**
 * @author jiaolei
 * @date 2026-07-07 10:11
 * @description TODO
 */
@Service
@Slf4j
public class RedisRollbackServiceImpl implements RedisRollbackService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    AlertService alertService;

    @Override
    public void rollbackRedisSeckill(Long bookId, Long userId) {
        String stockKey = RedisKeys.SECKILL_STOCK + bookId;
        String usersKey = RedisKeys.SECKILL_USERS + bookId + ":";
        String queueKey = RedisKeys.SECKILL_QUEUE + bookId;

        // 1. 恢复库存（INCR）
        Long newStock = stringRedisTemplate.opsForValue().increment(stockKey);

        log.info("库存回滚成功：bookId={}, userId={}, newStock={}, removedUser={}, removedQueue={}",
                bookId, userId, newStock, null, null);
        // 2. 移除用户抢购标记
        Long removedUser = stringRedisTemplate.opsForSet().remove(usersKey,userId.toString());
        log.info("移除用户抢购标记：bookId={}, userId={}, newStock={}, removedUser={}, removedQueue={}",
                bookId, userId, newStock, removedUser, null);
        // 3. 移除排队记录
        Long removedQueue = stringRedisTemplate.opsForZSet().remove(queueKey, userId.toString());
        log.info("移除用户抢购标记：bookId={}, userId={}, newStock={}, removedUser={}, removedQueue={}",
                bookId, userId, newStock, removedUser, removedQueue);
        log.info("Redis秒杀回滚完成：bookId={}, userId={}, newStock={}, removedUser={}, removedQueue={}",
                bookId, userId, newStock, removedUser, removedQueue);
    }

    @Override
    public void rollbackRedisSeckillOrThrow(Long bookId, Long userId,Long orderId) {
        try {
            if(bookId==null||userId==null){
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            }
            rollbackRedisSeckill(bookId, userId);
            log.info("Redis回滚成功：orderId={}", orderId);
        } catch (Exception e) {
            // Redis 回滚失败 → 记录补偿，不阻塞主流程
            log.error("Redis回滚失败，记录补偿：orderId={}, bookId={},userId={}", orderId, bookId,userId, e);
            alertService.sendAlert("Redis回滚失败", "orderId=" + orderId);
        }
    }

    @Override
    public void rollbackRedis(SeckillMessage msg) {
        try {
            rollbackRedisSeckill(msg.getBookId(), msg.getUserId());
            log.info("Redis回滚成功：userId={}, bookId={}", msg.getUserId(), msg.getBookId());
        } catch (Exception e) {
            log.error("Redis回滚失败，需人工介入：userId={}, bookId={}", msg.getUserId(), msg.getBookId(), e);
            alertService.sendAlert("秒杀Redis回滚失败",
                    String.format("userId=%d, bookId=%d", msg.getUserId(), msg.getBookId()));
        }
    }
}
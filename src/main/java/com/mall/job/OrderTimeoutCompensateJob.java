package com.mall.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.annotation.Log;
import com.mall.common.RedisKeys;
import com.mall.config.CompensateProperties;
import com.mall.entity.Orders;
import com.mall.enums.OrderStatus;
import com.mall.enums.OrderType;
import com.mall.mapper.OrdersMapper;
import com.mall.service.AlertService;
import com.mall.service.OrdersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.mall.common.RedisKeys.TTL_COMPENSATE_LOCK;

/**
 * @author jiaolei
 * @date 2026-07-08 16:19
 * @description TODO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCompensateJob {

    private final OrdersMapper ordersMapper;
    private final OrdersService ordersService;
    private final AlertService alertService;
    private final RedisTemplate redisTemplate;
    private final CompensateProperties compensateProperties;

    @Log("秒杀订单补偿任务")
    @Scheduled(fixedDelayString = "${mall.compensate.order-timeout-compensate-fixed-delay}")  // 每分钟执行
    public void compensateStuckOrders() {
        String lockKey=RedisKeys.COMPENSATE_LOCK+"seckillOrder";
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisKeys.TTL_COMPENSATE_LOCK);
        if(!locked){
            log.info("其他实例正在执行补偿任务，跳过");
            return;
        }
        try {
            // 查询超时 5 分钟以上且仍为 PENDING 的秒杀订单
            List<Orders> stuckOrders = ordersMapper.selectList(
                    new LambdaQueryWrapper<Orders>()
                            .eq(Orders::getOrderType, OrderType.SECKILL.getCode())
                            .eq(Orders::getStatus, OrderStatus.PENDING.getValue())
                            .le(Orders::getExpireTime, LocalDateTime.now())
                            .le(Orders::getCreatedAt, LocalDateTime.now().minusMinutes(compensateProperties.getPendingLongMinutes()))
                            .last("LIMIT 100")
            );
            if(stuckOrders.isEmpty()){
                return;
            }
            log.info("补偿任务：发现 {} 条卡住的订单", stuckOrders.size());

            // 2. 逐条处理（每条独立事务）
            int successCount = 0;
            int failCount = 0;

            for (Orders order : stuckOrders) {
                try {
                    ordersService.cancelSeckillExpireOrderByOrder(order);
                    successCount++;
                }catch (Exception e) {
                    failCount++;
                    log.error("补偿取消失败：orderId={}", order.getId(), e);
                }
            }
            // 3. ✅ 批次告警（避免告警轰炸）
            long tooLongCount = countStuckOrdersExceed(compensateProperties.getPendingTooLongMinutes());
            if (tooLongCount > 0) {
                alertService.sendAlert(
                        "秒杀订单长期PENDING",
                        String.format("有 %d 个订单超过"+compensateProperties.getPendingTooLongMinutes()+"分钟未处理", tooLongCount)
                );
            }
        }finally {
            redisTemplate.delete(lockKey);
        }

    }

    /**
     * 查询超过指定分钟数仍为 PENDING 的秒杀订单数量
     */
    private long countStuckOrdersExceed(int minutes) {
        return ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getOrderType, OrderType.SECKILL.getCode())
                        .eq(Orders::getStatus, OrderStatus.PENDING.getValue())
                        .le(Orders::getCreatedAt, LocalDateTime.now().minusMinutes(minutes))
        );
    }

}
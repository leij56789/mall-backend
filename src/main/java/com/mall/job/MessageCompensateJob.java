package com.mall.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.config.CompensateProperties;
import com.mall.config.MessageProperties;
import com.mall.entity.BrokerMessageLog;
import com.mall.entity.Orders;
import com.mall.enums.MessageStatus;
import com.mall.enums.OrderStatus;
import com.mall.enums.OrderType;
import com.mall.enums.ResultCode;
import com.mall.mapper.OrdersMapper;
import com.mall.mq.message.OrderTimeoutMessage;
import com.mall.mq.producer.GenericMessageProducer;
import com.mall.mq.producer.OrderMessageProducer;
import com.mall.service.BrokerMessageLogService;
import lombok.Locked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageCompensateJob {
    private final BrokerMessageLogService brokerMessageLogService;
    private final MessageProperties messageProperties;
    private final RedisTemplate<String,String> redisTemplate;
    private final GenericMessageProducer genericMessageProducer;
    private final CompensateProperties compensateProperties;


    @Log("消息定时扫描补偿任务")
    @Scheduled(fixedDelayString="${mall.compensate.message-compensate-fixed-delay}")
    public void compensate(){

        String lockKey="compensate:message:lock";
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", compensateProperties.getLockKeyTimeoutSeconds(), TimeUnit.SECONDS);
        if(!locked){
            log.info("其他实例正在执行补偿任务，跳过");
            return;
        }
        try{
            List<BrokerMessageLog> logs = brokerMessageLogService.lambdaQuery()
                    .in(BrokerMessageLog::getStatus,
                            MessageStatus.PENDING.getCode(),
                            MessageStatus.FAILED.getCode())
                    .lt(BrokerMessageLog::getRetryCount,messageProperties.getMaxRetry())
                    .le(BrokerMessageLog::getNextRetryTime, LocalDateTime.now())
                    .orderByAsc(BrokerMessageLog::getCreateTime)
                    .last("limit 100")
                    .list();
            for (BrokerMessageLog log : logs) {
                genericMessageProducer.trySend(log);
            }
            //扫描sending超时，退回为failed(再重试）
            List<BrokerMessageLog> stuckLogs = brokerMessageLogService.lambdaQuery()
                    .eq(BrokerMessageLog::getStatus,
                            MessageStatus.SENDING.getCode())
                    .lt(BrokerMessageLog::getRetryCount,messageProperties.getMaxRetry())
                    .le(BrokerMessageLog::getNextRetryTime, LocalDateTime.now())
                    .orderByAsc(BrokerMessageLog::getCreateTime)
                    .last("limit 100")
                    .list();
            for (BrokerMessageLog stuckLog : stuckLogs) {
                boolean updated = brokerMessageLogService.lambdaUpdate()
                        .set(BrokerMessageLog::getStatus, MessageStatus.FAILED.getCode())
                        .set(BrokerMessageLog::getNextRetryTime, LocalDateTime.now().plusMinutes(messageProperties.getRetryIntervalMinutes()))
                        .eq(BrokerMessageLog::getMessageId, stuckLog.getMessageId())
                        .eq(BrokerMessageLog::getStatus, MessageStatus.SENDING.getCode())
                        .eq(BrokerMessageLog::getRetryCount, stuckLog.getRetryCount()).update();
                if(updated){
                    stuckLog.setStatus(MessageStatus.FAILED.getCode());
                    log.warn("SENDING超时，回退为FAILED,messageId={}",stuckLog.getMessageId());
                    genericMessageProducer.trySend(stuckLog);
                }
            }
            log.info("补偿任务：发现{}条补偿消息",logs.size()+stuckLogs.size());
        }finally{
            redisTemplate.delete(lockKey);
        }
    }
}
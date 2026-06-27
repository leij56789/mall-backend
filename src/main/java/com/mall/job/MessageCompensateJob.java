package com.mall.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.config.MessageProperties;
import com.mall.entity.BrokerMessageLog;
import com.mall.entity.Orders;
import com.mall.enums.MessageStatus;
import com.mall.mapper.OrdersMapper;
import com.mall.mq.message.OrderTimeoutMessage;
import com.mall.mq.producer.OrderMessageProducer;
import com.mall.service.BrokerMessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageCompensateJob {
    private final BrokerMessageLogService brokerMessageLogService;
    private final OrderMessageProducer orderMessageProducer;
    private final MessageProperties messageProperties;

    @Log("定时扫描补偿任务")
    @Scheduled(fixedDelay=300000)
    public void compensate(){
        List<BrokerMessageLog> logs = brokerMessageLogService.lambdaQuery()
                .in(BrokerMessageLog::getStatus,
                        MessageStatus.PENDING.getCode(),
                        MessageStatus.FAILED.getCode())
                .lt(BrokerMessageLog::getRetryCount,messageProperties.getMaxRetry())
                .le(BrokerMessageLog::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(BrokerMessageLog::getCreateTime)
                .last("limit 100")
                .list();
        if(logs!=null&&!logs.isEmpty()){
            for (BrokerMessageLog log : logs) {
                orderMessageProducer.trySend(log);
            }
            log.info("补偿任务：发现{}条补偿消息",logs.size());
        }
    }
}
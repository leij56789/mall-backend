package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.BrokerMessageLog;

import java.time.LocalDateTime;

/**
* @author jiaolei
* @description 针对表【broker_message_log(消息日志表（本地消息表）)】的数据库操作Service
* @createDate 2026-06-23 16:03:06
*/
public interface BrokerMessageLogService extends IService<BrokerMessageLog> {

    void updateStatusAndRetryCount(String messageId, Integer code ,String exchange, String routingKey, Integer oldRetryCount, LocalDateTime nextRetryTime);
    // BrokerMessageLogService 接口
    boolean tryLockMessage(String messageId, Integer oldRetryCount, String exchange, String routingKey);
}

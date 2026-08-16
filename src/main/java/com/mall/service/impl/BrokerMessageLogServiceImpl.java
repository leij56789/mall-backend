package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.config.MessageProperties;
import com.mall.entity.BrokerMessageLog;
import com.mall.enums.MessageStatus;
import com.mall.enums.ResultCode;
import com.mall.enums.SeckillStatus;
import com.mall.mapper.BrokerMessageLogMapper;
import com.mall.service.BrokerMessageLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
* @author jiaolei
* @description 针对表【broker_message_log(消息日志表（本地消息表）)】的数据库操作Service实现
* @createDate 2026-06-23 16:03:06
*/

@Slf4j
@Service
public class BrokerMessageLogServiceImpl extends ServiceImpl<BrokerMessageLogMapper, BrokerMessageLog>
    implements BrokerMessageLogService {
    @Autowired
    MessageProperties messageProperties;

    @Log("消息生产者更新状态和重试次数")
    @Override
    public void updateStatusAndRetryCount(String messageId, Integer code, String exchange, String routingKey, Integer oldRetryCount, LocalDateTime nextRetryTime) {
        if(messageId==null||code==null||exchange==null||routingKey==null||oldRetryCount==null){
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        LambdaUpdateWrapper<BrokerMessageLog> wrapper = new LambdaUpdateWrapper<>();
        int newRetryCount = oldRetryCount + 1;
        wrapper.eq(BrokerMessageLog::getMessageId, messageId)
                .eq(BrokerMessageLog::getExchange, exchange)
                .eq(BrokerMessageLog::getRoutingKey, routingKey)
                .eq(BrokerMessageLog::getRetryCount, newRetryCount)
                .eq(BrokerMessageLog::getStatus, MessageStatus.SENDING.getCode())
                .set(BrokerMessageLog::getStatus, code)
                .set(nextRetryTime!=null,BrokerMessageLog::getNextRetryTime, nextRetryTime);
        boolean updated = this.update(wrapper);
        if(!updated){
            log.error("数据库更新失败：messageId={}",messageId);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }else{
            log.info("数据库更新成功");
        }
    }

    @Override
    @Transactional
    public boolean tryLockMessage(String messageId, Integer oldRetryCount, String exchange, String routingKey) {
        LambdaUpdateWrapper<BrokerMessageLog> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BrokerMessageLog::getMessageId, messageId)
                .eq(BrokerMessageLog::getExchange, exchange)
                .eq(BrokerMessageLog::getRoutingKey, routingKey)
                .eq(BrokerMessageLog::getRetryCount, oldRetryCount)
                .in(BrokerMessageLog::getStatus,
                        MessageStatus.PENDING.getCode(),
                        MessageStatus.FAILED.getCode())
                .set(BrokerMessageLog::getStatus, MessageStatus.SENDING.getCode())  // 新增中间状态
                .set(BrokerMessageLog::getRetryCount, oldRetryCount + 1);  // 预占

        boolean updated = this.update(wrapper);
        if (!updated) {
            log.warn("乐观锁抢占失败：messageId={}, oldRetryCount={}", messageId, oldRetryCount);
            return false;
        }
        log.info("乐观锁抢占成功：messageId={}, newRetryCount={}", messageId, oldRetryCount + 1);
        return true;
    }
}





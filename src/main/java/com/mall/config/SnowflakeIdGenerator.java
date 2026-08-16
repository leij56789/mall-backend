package com.mall.config;

import com.mall.utils.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {
    private final SnowflakeIdWorker idWorker;

    public SnowflakeIdGenerator(@Value("${hutool.snowflake.worker-id:0}") long workerId,
                                 @Value("${hutool.snowflake.data-center-id:0}") long dataCenterId) {
        this.idWorker = new SnowflakeIdWorker(workerId, dataCenterId);
    }

    public long nextId() {
        return idWorker.nextId();
    }
}
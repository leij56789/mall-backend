package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mall.compensate")
public class CompensateProperties {
    private Long lockKeyTimeoutSeconds=60L;

    //消息补偿
    private Long messageCompensateFixedDelay =20000L;

    //订单超时补偿
    private Long orderTimeoutCompensateFixedDelay =20000L;
    private Integer pendingTooLongMinutes=30;
    private Integer pendingLongMinutes=5;
}
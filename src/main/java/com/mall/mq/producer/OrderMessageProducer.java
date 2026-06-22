package com.mall.mq.producer;

import com.mall.entity.Orders;
import com.mall.mq.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class OrderMessageProducer {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendOrderTimeoutMessage(Long orderId) {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", orderId);
//        message.put("authorUsername", authorUsername);
//        message.put("commentContent", commentContent);
        message.put("timestamp", LocalDateTime.now());
        
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.ORDERTIMEOUT_EXCHANGE,
            RabbitMQConfig.ORDERTIMEOUT_ROUTING_KEY,
            message
        );
    }
}
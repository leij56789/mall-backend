package com.mall.mq.producer;

import com.mall.mq.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class CommentMessageProducer {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendCommentNotification(Long articleId, String authorUsername, String commentContent) {
        Map<String, Object> message = new HashMap<>();
        message.put("articleId", articleId);
        message.put("authorUsername", authorUsername);
        message.put("commentContent", commentContent);
        message.put("timestamp", LocalDateTime.now());
        
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.COMMENT_EXCHANGE,
            RabbitMQConfig.COMMENT_ROUTING_KEY,
            message
        );
    }
}
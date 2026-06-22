package com.mall.mq.consumer;

import com.mall.mq.config.RabbitMQConfig;
import com.mall.service.NotificationsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class CommentMessageConsumer {
//    @Autowired
//    NotificationsService notificationsService;
    @RabbitListener(queues = RabbitMQConfig.COMMENT_QUEUE)
    public void handleCommentNotification(Map<String, Object> message) {
        log.info("收到评论通知: {}", message);
        // 实际项目中这里可以发送邮件、站内信等
        String authorUsername = (String) message.get("authorUsername");
        // 发送通知给作者
//        notificationsService.saveNotification(authorUsername, "您的文章有新评论");
    }
}
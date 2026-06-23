package com.mall.mq.consumer;

import com.mall.common.BusinessException;
import com.mall.entity.Book;
import com.mall.entity.Orders;
import com.mall.mapper.BookMapper;
import com.mall.mapper.OrdersMapper;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.service.OrdersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@Slf4j
public class OrderMessageConsumer {
//    @Autowired
//    NotificationsService notificationsService;
    @Autowired
    OrdersService orderService;

    @RabbitListener(queues = RabbitMQConfig.ORDERTIMEOUT_QUEUE)
    public void handleOrderTimeout(Map<String, Object> message) {
        log.info("收到评论通知: {}", message);
        // 实际项目中这里可以发送邮件、站内信等
//        String authorUsername = (String) message.get("authorUsername");
        // 发送通知给作者
//        notificationsService.saveNotification(authorUsername, "您的文章有新评论");
        //订单超时，取消订单
        Long orderId = (Long) message.get("orderId");
        orderService.cancelExpireOrder(orderId);

    }
}
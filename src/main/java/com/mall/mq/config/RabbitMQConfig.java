package com.mall.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {
    public static final String COMMENT_QUEUE = "comment.queue";
    public static final String COMMENT_EXCHANGE = "comment.exchange";
    public static final String COMMENT_ROUTING_KEY = "comment.routing.key";

    public static final String ORDERTIMEOUT_QUEUE = "orderTimeout.queue";
    public static final String ORDERTIMEOUT_EXCHANGE = "orderTimeout.exchange";
    public static final String ORDERTIMEOUT_ROUTING_KEY = "orderTimeout.routing.key";


    @Bean
    public Queue commentQueue() {
        return new Queue(ORDERTIMEOUT_QUEUE, true);
    }

    @Bean
    public DirectExchange commentExchange() {
        return new DirectExchange(ORDERTIMEOUT_EXCHANGE);
    }

    @Bean
    public Binding commentBinding() {
        return BindingBuilder.bind(commentQueue())
                .to(commentExchange())
                .with(ORDERTIMEOUT_ROUTING_KEY);
    }
    @Bean
    public Queue orderTimeoutQueue() {
        return new Queue(ORDERTIMEOUT_QUEUE, true);
    }

    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(ORDERTIMEOUT_EXCHANGE);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(commentQueue())
                .to(commentExchange())
                .with(ORDERTIMEOUT_ROUTING_KEY);
    }
}
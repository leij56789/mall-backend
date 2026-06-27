package com.mall.mq.config;

import com.mall.config.MessageProperties;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;


@Configuration
public class RabbitMQConfig {

    public static final String ORDERTIMEOUT_QUEUE = "orderTimeout.queue";
    public static final String ORDERTIMEOUT_EXCHANGE = "orderTimeout.exchange";
    public static final String ORDERTIMEOUT_ROUTING_KEY = "orderTimeout.routing.key";

    public static final String ORDER_TIMEOUT_DLQ="order.timeout.dlq";
    public static final String ORDER_DLQ_EXCHANGE="order.dlq.exchange";
    public static final String ORDERTIMEOUT_DLQ_ROUTING_KEY ="ordertimeout.dlq.routing.key";

    public static final String DELAY_QUEUE="delay.queue";
    public static final String DELAY_EXCHANGE="delay.exchange";
    public static final String DELAY_ROUTING_KEY="delay.routing.key";
    @Autowired
    MessageProperties messageProperties;
    @Bean
    public Queue orderTimeoutQueue() {
        HashMap<String, Object> args = new HashMap<>();
        //启用延迟重试
//        args.put("x-delayed-retry-type","all");
        //最小延迟：30s
//        args.put("x-delayed-retry-min",30000);
        //最大延迟：5分钟
//        args.put("x-delayed-retry-max",300000);
        //死信配置（超过重试次数后进入DLQ）
        args.put("x-dead-letter-exchange",ORDER_DLQ_EXCHANGE);
        args.put("x-dead-letter-routing-key",ORDERTIMEOUT_DLQ_ROUTING_KEY);
        //最大投递次数（3次）
        args.put("x-max-delivery-count",3);
        return new Queue(ORDERTIMEOUT_QUEUE, true,false,false,args);
    }

    //主交换机
    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(ORDERTIMEOUT_EXCHANGE);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderTimeoutExchange())
                .with(ORDERTIMEOUT_ROUTING_KEY);
    }
    //死信队列
    @Bean
    public Queue orderTimeoutDlq(){
        return new Queue(ORDER_TIMEOUT_DLQ,true);
    }
    @Bean
    public DirectExchange orderDlqExchange(){
        return new DirectExchange(ORDER_DLQ_EXCHANGE);
    }
    @Bean
    public Binding orderTimeoutDlqBinding(Queue orderTimeoutDlq,DirectExchange orderDlqExchange){
        return BindingBuilder.bind(orderTimeoutDlq)
                .to(orderDlqExchange)
                .with(ORDERTIMEOUT_DLQ_ROUTING_KEY);
    }
    //延迟队列
    @Bean
    public Queue delayQueue(){
        return QueueBuilder
                .durable(DELAY_QUEUE)
                .ttl(Math.toIntExact(messageProperties.getDelayTime()))
                .deadLetterExchange(ORDERTIMEOUT_EXCHANGE)
                .deadLetterRoutingKey(ORDERTIMEOUT_ROUTING_KEY)
                .build();
    }
    @Bean
    public DirectExchange delayExchange(){
        return new DirectExchange(DELAY_EXCHANGE);
    }
    @Bean
    public Binding DelayBinding(Queue delayQueue,DirectExchange delayExchange){
        return BindingBuilder
                .bind(delayQueue)
                .to(delayExchange)
                .with(DELAY_ROUTING_KEY);
    }

}
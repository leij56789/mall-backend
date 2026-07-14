package com.mall.mq.config;

import com.mall.config.MessageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Configuration
@RequiredArgsConstructor
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

    // ========== 秒杀业务队列 ==========
    public static final String SECKILL_QUEUE = "seckill.queue";
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_ROUTING_KEY = "seckill.routing.key";

    // ========== 秒杀死信队列 ==========
    public static final String SECKILL_DLQ = "seckill.dlq";
    public static final String SECKILL_DLQ_EXCHANGE = "seckill.dlq.exchange";
    public static final String SECKILL_DLQ_ROUTING_KEY = "seckill.dlq.routing.key";

    public static final String SECKILL_ORDER_TIMEOUT_QUEUE = "seckill.order.Timeout.queue";
    public static final String SECKILL_ORDER_TIMEOUT_EXCHANGE = "seckill.order.Timeout.exchange";
    public static final String SECKILL_ORDER_TIMEOUT_ROUTING_KEY = "seckill.order.Timeout.routing.key";

    public static final String SECKILL_ORDER_TIMEOUT_DLQ="seckill.order.timeout.dlq";
    public static final String SECKILL_ORDER_DLQ_EXCHANGE="seckill.order.dlq.exchange";
    public static final String SECKILL_ORDER_TIMEOUT_DLQ_ROUTING_KEY ="seckill.order.timeout.dlq.routing.key";

    public static final String SECKILL_DELAY_QUEUE="seckill.delay.queue";
    public static final String SECKILL_DELAY_EXCHANGE="seckill.delay.exchange";
    public static final String SECKILL_DELAY_ROUTING_KEY="seckill.delay.routing.key";
    private final MessageProperties messageProperties;
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
    public Binding delayBinding(Queue delayQueue,DirectExchange delayExchange){
        return BindingBuilder
                .bind(delayQueue)
                .to(delayExchange)
                .with(DELAY_ROUTING_KEY);
    }
    @Bean
    public Queue seckillQueue() {
        Map<String, Object> args = new HashMap<>();
        // 消费失败3次后进入死信
        args.put("x-max-delivery-count", 3);
        args.put("x-dead-letter-exchange", SECKILL_DLQ_EXCHANGE);
        args.put("x-dead-letter-routing-key", SECKILL_DLQ_ROUTING_KEY);
        return new Queue(SECKILL_QUEUE, true, false, false, args);
    }
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }

    // ========== 死信 ==========

    @Bean
    public Queue seckillDlq() {
        return new Queue(SECKILL_DLQ, true);
    }

    @Bean
    public DirectExchange seckillDlqExchange() {
        return new DirectExchange(SECKILL_DLQ_EXCHANGE, true, false);
    }

    @Bean
    public Binding seckillDlqBinding() {
        return BindingBuilder.bind(seckillDlq())
                .to(seckillDlqExchange())
                .with(SECKILL_DLQ_ROUTING_KEY);
    }
    @Bean
    public Queue seckillOrderTimeoutQueue() {
        HashMap<String, Object> args = new HashMap<>();
        //启用延迟重试
//        args.put("x-delayed-retry-type","all");
        //最小延迟：30s
//        args.put("x-delayed-retry-min",30000);
        //最大延迟：5分钟
//        args.put("x-delayed-retry-max",300000);
        //死信配置（超过重试次数后进入DLQ）
        args.put("x-dead-letter-exchange",SECKILL_ORDER_DLQ_EXCHANGE);
        args.put("x-dead-letter-routing-key",SECKILL_ORDER_TIMEOUT_DLQ_ROUTING_KEY);
        //最大投递次数（3次）
        args.put("x-max-delivery-count",3);
        return new Queue(SECKILL_ORDER_TIMEOUT_QUEUE, true,false,false,args);
    }

    //主交换机
    @Bean
    public DirectExchange seckillOrderTimeoutExchange() {
        return new DirectExchange(SECKILL_ORDER_TIMEOUT_EXCHANGE);
    }

    @Bean
    public Binding seckillOrderTimeoutBinding() {
        return BindingBuilder.bind(seckillOrderTimeoutQueue())
                .to(seckillOrderTimeoutExchange())
                .with(SECKILL_ORDER_TIMEOUT_ROUTING_KEY);
    }
    //死信队列
    @Bean
    public Queue seckillOrderTimeoutDlq(){
        return new Queue(SECKILL_ORDER_TIMEOUT_DLQ,true);
    }
    @Bean
    public DirectExchange seckillOrderDlqExchange(){
        return new DirectExchange(SECKILL_ORDER_DLQ_EXCHANGE);
    }
    @Bean
    public Binding seckillOrderTimeoutDlqBinding(Queue seckillOrderTimeoutDlq,DirectExchange seckillOrderDlqExchange){
        return BindingBuilder.bind(seckillOrderTimeoutDlq)
                .to(seckillOrderDlqExchange)
                .with(SECKILL_ORDER_TIMEOUT_DLQ_ROUTING_KEY);
    }
    //延迟队列
    @Bean
    public Queue seckillDelayQueue(){
        return QueueBuilder
                .durable(SECKILL_DELAY_QUEUE)
                .ttl(Math.toIntExact(messageProperties.getSeckillDelayTime()))
                .deadLetterExchange(SECKILL_ORDER_TIMEOUT_EXCHANGE)
                .deadLetterRoutingKey(SECKILL_ORDER_TIMEOUT_ROUTING_KEY)
                .build();
    }
    @Bean
    public DirectExchange seckillDelayExchange(){
        return new DirectExchange(SECKILL_DELAY_EXCHANGE);
    }
    @Bean
    public Binding seckillDelayBinding(Queue seckillDelayQueue,DirectExchange seckillDelayExchange){
        return BindingBuilder
                .bind(seckillDelayQueue)
                .to(seckillDelayExchange)
                .with(SECKILL_DELAY_ROUTING_KEY);
    }

}
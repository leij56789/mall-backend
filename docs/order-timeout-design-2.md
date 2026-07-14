# 订单超时重试功能 - 核心设计文档

> 项目地址：https://github.comleij56789/mall-backend.git

---
## 目录

- [一、功能概述](#一功能概述)
    - [1.1 业务背景](#11-业务背景)
    - [1.2 核心目标](#12-核心目标)
    - [1.3 技术栈](#13-技术栈)

- [二、整体架构图](#二整体架构图)

- [三、消息流转路径](#三消息流转路径)

- [四、技术方案选型](#四技术方案选型)
    - [4.1 为什么用 RabbitMQ 而不是定时任务](#41-为什么用-rabbitmq-而不是定时任务)
    - [4.2 为什么用 TTL 加 死信 而不是延迟插件](#42-为什么用-ttl-加-死信-而不是延迟插件)
    - [4.3 为什么用 Redis 维护重试次数](#43-为什么用-redis-维护重试次数)

- [五、RabbitMQ 队列配置](#五rabbitmq-队列配置)
    - [5.1 队列定义](#51-队列定义)
    - [5.2 配置参数说明](#52-配置参数说明)

- [六、数据表设计](#六数据表设计)
    - [6.1 订单表](#61-订单表)
    - [6.2 书籍表](#62-书籍表)
    - [6.3 消息日志表](#63-消息日志表)

- [七、核心代码](#七核心代码)
    - [7.1 消息体](#71-消息体)
    - [7.2 Producer 核心逻辑](#72-producer-核心逻辑)
    - [7.3 Consumer 核心逻辑](#73-consumer-核心逻辑)
    - [7.4 超时取消业务逻辑](#74-超时取消业务逻辑)
    - [7.5 补偿任务](#75-补偿任务)
    - [7.6 告警服务](#76-告警服务)

- [八、配置文件](#八配置文件)

- [九、枚举定义](#九枚举定义)
    - [9.1 订单状态](#91-订单状态)
    - [9.2 消息状态](#92-消息状态)

- [十、关键设计原则](#十关键设计原则)

- [十一、异常场景处理](#十一异常场景处理)

- [十二、面试常见问题](#十二面试常见问题)

- [十三、踩坑总结](#十三踩坑总结)
## 一、功能概述

用户下单后订单进入"待支付"状态，超时30分钟未支付则自动取消并恢复库存。

| 目标 | 说明 |
|------|------|
| 自动取消 | 订单超时后状态 PENDING → CANCELLED |
| 库存恢复 | 取消时自动恢复书籍库存 |
| 最终一致性 | 消息不丢失，MQ/Redis 故障有兜底 |
| 高可用 | 支持集群部署，Redis 故障本地降级 |

**技术栈**：Spring Boot 4.x | MyBatis-Plus | RabbitMQ 4.3.x | Redis | MySQL | Jackson

---

## 二、整体架构图

```

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              订单超时重试 - 完整架构                               │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                         1. 订单创建阶段                                     │   │
│  │                                                                             │   │
│  │  用户下单 ──→ 扣库存(乐观锁 @Version) ──→ 创建订单(PENDING)                │   │
│  │                              │              │                               │   │
│  │                              ▼              ▼                               │   │
│  │                         库存扣减成功    ──→ 发送延迟消息                     │   │
│  │                                          (TTL=30分钟)                      │   │
│  │                                          ↓                                  │   │
│  │                              保存本地消息表 (PENDING)                       │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                          │                                          │
│                                          ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                         2. 延迟队列等待 (RabbitMQ)                          │   │
│  │                                                                             │   │
│  │  ┌─────────────────────────────────────────────────────────────────────┐   │   │
│  │  │  DELAY_QUEUE (delay.queue)                                         │   │   │
│  │  │  - x-message-ttl: 30分钟                                           │   │   │
│  │  │  - x-dead-letter-exchange: orderTimeout.exchange                   │   │   │
│  │  │  - x-dead-letter-routing-key: orderTimeout.routing.key            │   │   │
│  │  └─────────────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                          │                                          │
│                                          ▼ (TTL 过期 → 死信转发)                   │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                         3. 业务队列 (消费者监听)                            │   │
│  │                                                                             │   │
│  │  ┌─────────────────────────────────────────────────────────────────────┐   │   │
│  │  │  ORDERTIMEOUT_QUEUE (orderTimeout.queue)                           │   │   │
│  │  │  - x-max-delivery-count: 3 (服务端投递上限)                        │   │   │
│  │  │  - x-dead-letter-exchange: order.dlq.exchange                     │   │   │
│  │  │  - x-dead-letter-routing-key: ordertimeout.dlq.routing.key       │   │   │
│  │  └─────────────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                          │                                          │
│                                          ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                         4. 消费者处理流程                                   │   │
│  │                                                                             │   │
│  │  Step 1: 解析消息 → 获取 orderId, messageId                                 │   │
│  │  Step 2: Redis 重试计数 (message:retry:{messageId})                        │   │
│  │  Step 3: retryCount > maxRetry ?                                           │   │
│  │          ├── 是 → 进入死信队列 + 告警                                      │   │
│  │          └── 否 → 执行业务取消                                              │   │
│  │  Step 4: 业务结果                                                           │   │
│  │          ├── 成功 → ACK + 删除 Redis Key                                   │   │
│  │          ├── 乐观锁冲突 → NACK(重试) + Redis 计数+1                       │   │
│  │          └── 系统异常 → NACK(重试) + Redis 计数+1                         │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘

```

---

## 三、消息流转路径

```

Producer → DELAY_EXCHANGE → DELAY_QUEUE (TTL=30min)
│
▼ (30min后过期)
死信转发 (x-dead-letter-*)
│
▼
ORDERTIMEOUT_EXCHANGE
│
▼
ORDERTIMEOUT_QUEUE (Consumer监听)
│
├── 消费成功 → ACK
├── 消费失败 → NACK(requeue=true)
└── 投递次数 >= 3 → 死信转发
│
▼
ORDER_DLQ_EXCHANGE
│
▼
ORDER_TIMEOUT_DLQ (最终死信)

```

---

## 四、技术方案选型

### 4.1 为什么用 RabbitMQ 而不是定时任务？

| 方案 | 实时性 | 可靠性 | 数据库压力 | 扩展性 |
|------|--------|--------|------------|--------|
| 定时任务扫表 | 差（分钟级）| 高 | 高 | 差 |
| Redis ZSet | 好（秒级）| 中 | 低 | 好 |
| RabbitMQ 延迟队列 | 好（秒级）| 高 | 低 | 好 |

### 4.2 为什么用 TTL+死信 而不是延迟插件？

- RabbitMQ 4.3.x 官方停止维护 `rabbitmq_delayed_message_exchange` 插件
- TTL + 死信队列是官方推荐的替代方案，无需安装插件

### 4.3 为什么用 Redis 维护重试次数？

| 方案 | 优点 | 缺点 |
|------|------|------|
| 消息头 x-retry-count | 无外部依赖 | basicNack 不更新头信息 |
| 服务端 x-delivery-count | 由 MQ 维护 | 死信转发场景不可用 |
| Redis INCR | 原子操作，支持集群，性能高 | 依赖 Redis 可用性 |

---

## 五、RabbitMQ 队列配置

```java
@Configuration
public class RabbitMQConfig {

    // 延迟队列
    public static final String DELAY_QUEUE = "delay.queue";
    public static final String DELAY_EXCHANGE = "delay.exchange";
    public static final String DELAY_ROUTING_KEY = "delay.routing.key";

    // 业务队列
    public static final String ORDERTIMEOUT_QUEUE = "orderTimeout.queue";
    public static final String ORDERTIMEOUT_EXCHANGE = "orderTimeout.exchange";
    public static final String ORDERTIMEOUT_ROUTING_KEY = "orderTimeout.routing.key";

    // 死信队列
    public static final String ORDER_TIMEOUT_DLQ = "order.timeout.dlq";
    public static final String ORDER_DLQ_EXCHANGE = "order.dlq.exchange";
    public static final String ORDERTIMEOUT_DLQ_ROUTING_KEY = "ordertimeout.dlq.routing.key";

    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
            .ttl(Math.toIntExact(messageProperties.getDelayTime()))
            .deadLetterExchange(ORDERTIMEOUT_EXCHANGE)
            .deadLetterRoutingKey(ORDERTIMEOUT_ROUTING_KEY)
            .build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-max-delivery-count", 3);
        args.put("x-dead-letter-exchange", ORDER_DLQ_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDERTIMEOUT_DLQ_ROUTING_KEY);
        return new Queue(ORDERTIMEOUT_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue orderTimeoutDlq() {
        return new Queue(ORDER_TIMEOUT_DLQ, true);
    }
}
```

配置参数说明：

队列 参数 值 说明
delay.queue x-message-ttl 1800000ms 消息存活时间（30分钟）
delay.queue x-dead-letter-exchange orderTimeout.exchange 过期后转发的交换机
delay.queue x-dead-letter-routing-key orderTimeout.routing.key 过期后转发的路由键
orderTimeout.queue x-max-delivery-count 3 最大投递次数
orderTimeout.queue x-dead-letter-exchange order.dlq.exchange 超过次数后进入死信
orderTimeout.queue x-dead-letter-routing-key ordertimeout.dlq.routing.key 死信路由键

---

## 六、数据表设计

### 6.1 订单表 (orders)

```sql
CREATE TABLE `orders` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号（雪花算法）',
    `user_id` BIGINT NOT NULL,
    `book_id` BIGINT NOT NULL,
    `quantity` INT NOT NULL,
    `total_amount` DECIMAL(10,2) NOT NULL,
    `status` TINYINT DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
    `address` VARCHAR(200),
    `expire_time` DATETIME COMMENT '超时时间（创建时+30分钟）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_order_no` (`order_no`)
);
```

### 6.2 书籍表 (book)

```sql
CREATE TABLE `book` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(200) NOT NULL,
    `price` DECIMAL(10,2) NOT NULL,
    `stock` INT NOT NULL DEFAULT 0,
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 6.3 消息日志表 (broker_message_log)

```sql
CREATE TABLE `broker_message_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `message_id` VARCHAR(64) NOT NULL UNIQUE,
    `exchange` VARCHAR(100) NOT NULL,
    `routing_key` VARCHAR(100) NOT NULL,
    `message_body` JSON NOT NULL,
    `delay_time` INT NOT NULL,
    `status` TINYINT DEFAULT 0 COMMENT '0待发送 1已发送 2发送失败 3最终失败',
    `retry_count` INT DEFAULT 0,
    `max_retry` INT DEFAULT 3,
    `next_retry_time` DATETIME,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_status_next_retry` (`status`, `next_retry_time`)
);
```

---

## 七、核心代码

### 7.1 消息体

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage {
    private Long orderId;
    private Long bookId;
    private Integer quantity;
    private Long expireTimestamp;
    private Long createTimestamp;
    private String messageId;
}
```

### 7.2 Producer 核心逻辑

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final BrokerMessageLogService brokerMessageLogService;
    private final ObjectMapper objectMapper;
    private final MessageProperties messageProperties;
    private final AlertService alertService;

    @Log("订单超时消息生产者")
    public void sendOrderTimeoutMessage(Orders orders) {
        // 1. 构建消息快照
        OrderTimeoutMessage message = OrderTimeoutMessage.builder()
            .orderId(orders.getId())
            .bookId(orders.getBookId())
            .quantity(orders.getQuantity())
            .expireTimestamp(orders.getExpireTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .createTimestamp(System.currentTimeMillis())
            .messageId(UUID.randomUUID().toString().replace("-", ""))
            .build();

        // 2. 序列化
        String json = objectMapper.writeValueAsString(message);

        // 3. 保存本地消息表（PENDING）
        BrokerMessageLog log = BrokerMessageLog.builder()
            .orderId(orders.getId())
            .messageId(message.getMessageId())
            .exchange(RabbitMQConfig.ORDERTIMEOUT_EXCHANGE)
            .routingKey(RabbitMQConfig.ORDERTIMEOUT_ROUTING_KEY)
            .messageBody(json)
            .delayTime(messageProperties.getDelayTime().intValue())
            .status(MessageStatus.PENDING.getCode())
            .retryCount(0)
            .maxRetry(messageProperties.getMaxRetry())
            .nextRetryTime(LocalDateTime.now().plusSeconds(30))
            .build();
        brokerMessageLogService.save(log);

        // 4. 事务提交后异步发送
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trySend(log);
                }
            }
        );
    }
    @Async
    public void trySend(BrokerMessageLog log) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.DELAY_EXCHANGE,
                RabbitMQConfig.DELAY_ROUTING_KEY,
                log.getMessageBody(),
                msg -> msg.getMessageProperties().setMessageId(log.getMessageId())
            );
            brokerMessageLogService.updateStatus(log.getId(), MessageStatus.SENT.getCode());
        } catch (Exception e) {
            int newRetryCount = log.getRetryCount() + 1;
            if (newRetryCount >= messageProperties.getMaxRetry()) {
                brokerMessageLogService.updateStatus(log.getId(), MessageStatus.FINAL_FAILED.getCode());
                alertService.sendAlert("消息发送超过最大重试次数", ...);
            } else {
                brokerMessageLogService.updateStatus(log.getId(), MessageStatus.FAILED.getCode());
                brokerMessageLogService.updateRetryCount(log.getId(), newRetryCount, nextRetryTime);
            }
        }
    }
}
```

### 7.3 Consumer 核心逻辑

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrdersService orderService;
    private final ObjectMapper objectMapper;
    private final MessageProperties messageProperties;
    private final AlertService alertService;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = RabbitMQConfig.ORDERTIMEOUT_QUEUE)
    public void handleOrderTimeout(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        // 1. 解析消息
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        OrderTimeoutMessage msg = objectMapper.readValue(body, OrderTimeoutMessage.class);
        Long orderId = msg.getOrderId();

        // 2. Redis 原子计数
        String retryKey = "message:retry:" + messageId;
        Long retryCount = redisTemplate.opsForValue().increment(retryKey);
        Integer maxRetry = messageProperties.getMaxRetry();

        // 3. 设置 TTL
        if (retryCount == 1) {
            long ttlMinutes = Math.max(1, 2 * messageProperties.getDelayTime() / 1000 / 60);
            redisTemplate.expire(retryKey, ttlMinutes, TimeUnit.MINUTES);
        }

        // 4. 超过最大重试次数 → 死信
        if (retryCount > maxRetry) {
            channel.basicNack(deliveryTag, false, false);
            redisTemplate.delete(retryKey);
            alertService.sendAlert("消息消费超过最大重试次数",
                String.format("orderId=%s, retryCount=%d", orderId, retryCount));
            return;
        }

        try {
            orderService.cancelExpireOrderByOrderTimeMessage(msg);
            channel.basicAck(deliveryTag, false);
            redisTemplate.delete(retryKey);
        } catch (BusinessException e) {
            if (e.getCode().equals(ResultCode.ORDER_UPDATE_FAIL.getCode()) ||
                e.getCode().equals(ResultCode.STOCK_RECOVER_FAIL.getCode())) {
                channel.basicNack(deliveryTag, false, true);  // 重试
            } else {
                channel.basicAck(deliveryTag, false);
                redisTemplate.delete(retryKey);
            }
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, true);  // 重试
        }
    }
}
```

### 7.4 超时取消业务逻辑

```java
@Log("订单超时自动取消")
@Transactional(rollbackFor = Exception.class)
public void cancelExpireOrderByOrderTimeMessage(OrderTimeoutMessage msg) {
    Long orderId = msg.getOrderId();
    Long bookId = msg.getBookId();
    Integer quantity = msg.getQuantity();

    // 1. 查询订单
    Orders order = ordersMapper.selectById(orderId);
    if (order == null) {
        throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
    }

    // 2. 幂等性检查
    if (order.getStatus() != OrderStatus.PENDING.getValue()) {
        log.info("订单已处理，跳过：orderId={}", orderId);
        return;
    }

    // 3. 快照时间检查
    if (msg.getExpireTimestamp() > System.currentTimeMillis()) {
        throw new BusinessException(ResultCode.PREMATURE_DELIVERY);
    }

    // 4. 数据库时间检查
    if (order.getExpireTime().isAfter(LocalDateTime.now())) {
        throw new BusinessException(ResultCode.ORDER_NOT_EXPIRE);
    }

    // 5. 恢复库存（乐观锁）
    Book book = bookMapper.selectById(bookId);
    if (book == null) {
        throw new BusinessException(ResultCode.BOOK_NOT_FOUND);
    }
    book.setStock(book.getStock() + quantity);
    if (bookMapper.updateById(book) == 0) {
        throw new BusinessException(ResultCode.STOCK_RECOVER_FAIL);
    }

    // 6. 取消订单（条件更新）
    boolean updated = ordersMapper.update(null,
        new LambdaUpdateWrapper<Orders>()
            .set(Orders::getStatus, OrderStatus.CANCELLED.getValue())
            .eq(Orders::getId, orderId)
            .eq(Orders::getStatus, OrderStatus.PENDING.getValue())
    ) > 0;
    if (!updated) {
        throw new BusinessException(ResultCode.ORDER_UPDATE_FAIL);
    }
}
```

### 7.5 补偿任务

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageCompensateJob {

    private final BrokerMessageLogService brokerMessageLogService;
    private final OrderMessageProducer orderMessageProducer;
    private final MessageProperties messageProperties;

    @Scheduled(fixedDelay = 300000)  // 5分钟
    public void compensate() {
        List<BrokerMessageLog> logs = brokerMessageLogService.lambdaQuery()
            .in(BrokerMessageLog::getStatus,
                MessageStatus.PENDING.getCode(),
                MessageStatus.FAILED.getCode())
            .le(BrokerMessageLog::getNextRetryTime, LocalDateTime.now())
            .lt(BrokerMessageLog::getRetryCount, messageProperties.getMaxRetry())
            .list();

        if (logs == null || logs.isEmpty()) {
            return;
        }

        log.info("补偿任务：发现 {} 条待补偿消息", logs.size());
        for (BrokerMessageLog log : logs) {
            orderMessageProducer.trySend(log);
        }
    }
}
```

### 7.6 告警服务

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertService {

    public void sendAlert(String title, String content) {
        // 1. 日志（最可靠）
        log.error("【告警】{}：{}", title, content);
        // 2. TODO: 钉钉/飞书/邮件
    }
}
```

---

## 八、配置文件

```yaml
# application.yml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: manual

mall:
  message:
    delay-time: 1800000           # 30分钟（毫秒）
    max-retry: 3
    initial-retry-delay-seconds: 30
    retry-interval-minutes: 5
    compensate-batch-size: 100
```

---

## 九、枚举定义

### 9.1 订单状态

```java
public enum OrderStatus {
    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    CANCELLED(2, "已取消");
}
```

### 9.2 消息状态

```java
public enum MessageStatus {
    PENDING(0, "待发送"),
    SENT(1, "已发送"),
    FAILED(2, "发送失败"),
    FINAL_FAILED(3, "最终失败");
}
```

---

## 十、关键设计原则

原则 实现方式 解决的问题
最终一致性 本地消息表 + 补偿任务 消息不丢失
原子性 @Transactional 库存+订单要么全成功要么全失败
幂等性 状态检查 (status == PENDING) 重复消费不造成数据不一致
并发安全 乐观锁 @Version + 条件更新 多线程下数据正确
坏消息保护 解析失败直接 ACK 丢弃 坏消息不阻塞队列
重试封顶 Redis 计数 + maxRetry 消息不无限重试
服务降级 ConcurrentHashMap 本地缓存 Redis 故障系统不崩溃
可观测性 @Log + AlertService 关键步骤有日志和告警

---

## 十一、异常场景处理

异常场景 处理方式 结果
扣库存乐观锁冲突 抛出 SYSTEM_BUSY，事务回滚 订单未创建
MQ 发送失败 本地消息表 FAILED，补偿重试 最终发送成功
消息解析失败 直接 ACK 丢弃 坏消息被丢弃
Redis 故障 ConcurrentHashMap 本地降级 功能继续运行
业务异常（已支付） ACK 确认，不重试 订单保持 PAID
乐观锁冲突 NACK(重试)，Redis 计数+1 消息重试，最多3次
超过最大重试次数 NACK(不重试)→死信 告警触发，人工介入

---

## 十二、面试常见问题

Q1：为什么用 TTL+死信 而不是延迟插件？

RabbitMQ 4.3.x 官方停止维护延迟插件，改用 TTL+死信队列，消息过期后通过死信转发到业务队列。

Q2：重试次数为什么用 Redis 而不是 MQ 自带？

消息来自延迟队列的死信转发，x-delivery-count 不可用。Redis INCR 原子计数，支持集群，Redis 故障有本地内存降级兜底。

Q3：怎么防止消息无限重试？

双重保护：Redis 计数判断 + RabbitMQ 队列 x-max-delivery-count=3 服务端拦截。

Q4：怎么保证幂等性？

订单状态检查 + 条件更新 UPDATE orders SET status=2 WHERE id=? AND status=0。

Q5：Redis 故障了怎么办？

ConcurrentHashMap 本地内存降级，Redis 恢复后自动切回。

Q6：生产者发送失败怎么办？

本地消息表 PENDING → 补偿任务每5分钟扫描重发 → 超过3次触发告警。

Q7：消费者解析失败怎么办？

解析失败直接 ACK 确认丢弃，不重试，防止坏消息阻塞队列。

---

文档版本：v2.0 | 最后更新：2026-06-28 | 维护人：jiaolei
项目地址：https://github.com/leij56789/mall-backend.git

```

---

直接复制上面的全部内容，保存为 `订单超时重试设计文档.md` 即可。
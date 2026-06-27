# 订单超时自动取消 - 核心设计文档

> 本文档描述订单超时自动取消系统的完整架构设计和核心逻辑，适用于项目文档和面试准备。

---

## 📋 目录

- [一、系统架构图](#一系统架构图)
- [二、需要准备的框架](#二需要准备的框架)
- [三、生产者端重试逻辑](#三生产者端重试逻辑)
- [四、消费者端重试逻辑](#四消费者端重试逻辑)
- [五、业务处理核心逻辑](#五业务处理核心逻辑)
- [六、核心数据表设计](#六核心数据表设计)
- [七、两种重试对比](#七两种重试对比)
- [八、关键设计原则](#八关键设计原则)
- [九、核心配置](#九核心配置)
- [十、面试话术](#十面试话术)

---

## 一、系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              订单超时取消完整流程                                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                          1. 订单创建阶段                                    │   │
│  │                                                                             │   │
│  │  用户下单 ──→ 扣库存(乐观锁) ──→ 创建订单 ──→ 保存消息日志(PENDING)        │   │
│  │                              │              │                               │   │
│  │                              ▼              ▼                               │   │
│  │                         库存扣减成功    事务提交后异步发送延迟消息           │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                          │                                          │
│                                          ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                          2. 消息发送阶段                                    │   │
│  │                                                                             │   │
│  │  ┌─────────────────────┐    ┌─────────────────────┐                        │   │
│  │  │     发送成功         │    │     发送失败         │                        │   │
│  │  │ 更新状态为 SENT     │    │ 更新状态为 FAILED   │                        │   │
│  │  │ 等待30分钟后消费    │    │ 等待补偿任务重试    │                        │   │
│  │  └─────────────────────┘    └─────────────────────┘                        │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                          │                                          │
│                                          ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                          3. 消息补偿阶段                                    │   │
│  │                                                                             │   │
│  │  补偿任务(每5分钟) ──→ 扫描 PENDING/FAILED ──→ 重试发送                    │   │
│  │                              │              │                               │   │
│  │                              ▼              ▼                               │   │
│  │                         重试成功        重试失败                            │   │
│  │                         更新SENT        retryCount+1                       │   │
│  │                                        retryCount>=3 → FINAL_FAILED + 告警 │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                          │                                          │
│                                          ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                          4. 消息消费阶段                                    │   │
│  │                                                                             │   │
│  │  30分钟后消息到达 ──→ 消费消息 ──→ 处理订单超时                           │   │
│  │                              │              │                               │   │
│  │                              ▼              ▼                               │   │
│  │                         消费成功        消费失败                            │   │
│  │                         basicAck        检查重试次数                        │   │
│  │                                         <3 → 重试                          │   │
│  │                                         >=3 → 死信队列 + 告警              │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                          │                                          │
│                                          ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                          5. 超时取消阶段                                    │   │
│  │                                                                             │   │
│  │  校验订单状态(是否PENDING) ──→ 校验超时时间 ──→ 恢复库存(乐观锁)           │   │
│  │                              │              │                               │   │
│  │                              ▼              ▼                               │   │
│  │                         校验通过         取消订单(状态条件)                  │   │
│  │                         开始处理         完成超时取消 ✅                    │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、需要准备的框架

| 序号 | 组件 | 说明 |
|:---|:---|:---|
| 1 | **本地事务表** | `broker_message_log`，MyBatis-Plus 生成对应类 |
| 2 | **ObjectMapper + MessageProperties** | JSON 解析 + MQ 配置注入 |
| 3 | **枚举补充** | `MessageStatus`（消息状态）+ `ResultCode`（错误码）|
| 4 | **MQ 三件套** | `Config`（配置）、`Consumer`（消费者）、`Producer`（生产者）|
| 5 | **AlertService** | 达最大重试次数后的人工处理告警 |
| 6 | **CompensateJob** | 定时扫描本地消息表，补偿发送失败的消息 |

### 目录结构

```
src/main/java/com/mall/
│
├── entity/
│   ├── Orders.java              # 订单实体
│   ├── Book.java                # 书籍实体
│   └── BrokerMessageLog.java    # 消息日志实体
│
├── mapper/
│   ├── OrdersMapper.java
│   ├── BookMapper.java
│   └── BrokerMessageLogMapper.java
│
├── service/
│   ├── OrdersService.java
│   └── BrokerMessageLogService.java
│
├── mq/
│   ├── config/
│   │   └── RabbitMQConfig.java
│   ├── producer/
│   │   └── OrderMessageProducer.java
│   ├── consumer/
│   │   └── OrderMessageConsumer.java
│   └── message/
│       └── OrderTimeoutMessage.java
│
├── job/
│   └── MessageCompensateJob.java
│
├── common/
│   ├── AlertService.java
│   ├── BusinessException.java
│   └── ResultCode.java
│
├── config/
│   ├── MessageProperties.java
│   └── SnowflakeConfig.java
│
└── enums/
    ├── OrderStatus.java
    └── MessageStatus.java
```

---

## 三、生产者端重试逻辑

```
订单创建
    │
    ▼
Producer.sendOrderTimeoutMessage(orders)
    │
    ├─ 1. 构建快照（OrderTimeoutMessage）
    │      orderId, bookId, quantity, expireTimestamp, messageId
    │
    ├─ 2. 保存本地事务表（PENDING）
    │
    ├─ 3. TransactionSynchronizationManager.afterCommit()
    │      异步调用 trySend()
    │
    └─ 4. trySend() 发送消息
            │
            ├─ 发送成功 → 更新状态 SENT
            │
            └─ 发送失败 → 更新状态 FAILED
                retryCount + 1
                nextRetryTime = now + 5min
                │
                └─ retryCount >= maxRetry
                        → 状态 FINAL_FAILED
                        → AlertService 告警
```

**重试触发者**：`CompensateJob`（定时扫描 PENDING/FAILED 状态的消息，重新调用 `trySend()`）

**达最大重试**：触发 `AlertService`

### 核心代码

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final BrokerMessageLogService logService;
    private final MessageProperties messageProperties;
    private final AlertService alertService;

    private static final int INITIAL_RETRY_COUNT = 0;

    @Async
    public void trySend(BrokerMessageLog log) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDERTIMEOUT_EXCHANGE,
                RabbitMQConfig.ORDERTIMEOUT_ROUTING_KEY,
                log.getMessageBody(),
                msg -> {
                    msg.getMessageProperties().setMessageId(log.getMessageId());
                    return msg;
                }
            );

            // 发送成功 → SENT
            logService.lambdaUpdate()
                .set(BrokerMessageLog::getStatus, MessageStatus.SENT.getCode())
                .eq(BrokerMessageLog::getMessageId, log.getMessageId())
                .update();
            log.info("消息发送成功：orderId={}", log.getOrderId());

        } catch (Exception e) {
            log.error("消息发送失败：orderId={}", log.getOrderId(), e);

            int newRetryCount = log.getRetryCount() + 1;
            boolean exceeded = newRetryCount >= messageProperties.getMaxRetry();

            logService.lambdaUpdate()
                .set(BrokerMessageLog::getStatus, 
                     exceeded ? MessageStatus.FINAL_FAILED.getCode() : MessageStatus.FAILED.getCode())
                .set(BrokerMessageLog::getRetryCount, newRetryCount)
                .set(BrokerMessageLog::getNextRetryTime, 
                     LocalDateTime.now().plusMinutes(messageProperties.getRetryIntervalMinutes()))
                .eq(BrokerMessageLog::getMessageId, log.getMessageId())
                .update();

            if (exceeded) {
                alertService.sendAlert("消息发送超过最大重试次数",
                        "orderId=" + log.getOrderId() + ", messageId=" + log.getMessageId());
            }
        }
    }
}
```

---

## 四、消费者端重试逻辑

```
消息到达
    │
    ▼
retryCount >= maxRetry?
    ├─ 是 → 死信队列 + AlertService 告警 + return
    │
    └─ 否 → 执行 try 块
                │
                ├─ 1. 解析 JSON → OrderTimeoutMessage
                │
                ├─ 2. 业务处理（cancelExpireOrderByOrderTimeMessage）
                │       ├─ 幂等性检查（status == PENDING）
                │       ├─ 超时检查（快照 + 数据库双重检查）
                │       ├─ 恢复库存（乐观锁 @Version）
                │       └─ 取消订单（条件更新 status = CANCELLED WHERE status = PENDING）
                │
                ├─ 成功 → basicAck ✅
                │
                ├─ BusinessException
                │       ├─ 乐观锁冲突（ORDER_UPDATE_FAIL / STOCK_RECOVER_FAIL）
                │       │       → basicNack(requeue=true) 重试
                │       └─ 其他业务异常
                │               → basicAck 确认，不重试
                │
                └─ Exception（系统异常）
                        → basicNack(requeue=true) 重试
```

**重试触发者**：MQ 自身（`basicNack(requeue=true)` 让消息重新入队）

**达最大重试**：进入死信队列 + 触发 `AlertService`

### 核心代码

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMessageConsumer {

    private final OrdersService orderService;
    private final ObjectMapper objectMapper;
    private final MessageProperties messageProperties;
    private final AlertService alertService;

    @RabbitListener(queues = RabbitMQConfig.ORDERTIMEOUT_QUEUE)
    public void handleOrderTimeout(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        // 1. 先判断重试次数（最前面）
        long retryCount = getRetryCount(message);
        int maxRetry = messageProperties.getMaxRetry();

        if (retryCount >= maxRetry) {
            log.error("超过最大重试次数({})，进入死信队列：retryCount={}", maxRetry, retryCount);
            channel.basicNack(deliveryTag, false, false);
            alertService.sendAlert("消息消费超过最大重试次数",
                    String.format("retryCount=%d, maxRetry=%d", retryCount, maxRetry));
            return;
        }

        try {
            // 2. 解析消息
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            OrderTimeoutMessage msg = objectMapper.readValue(body, OrderTimeoutMessage.class);

            // 3. 业务处理
            orderService.cancelExpireOrderByOrderTimeMessage(msg);

            // 4. 成功确认
            channel.basicAck(deliveryTag, false);
            log.info("消费成功：orderId={}", msg.getOrderId());

        } catch (BusinessException e) {
            Integer code = e.getCode();

            if (ResultCode.ORDER_UPDATE_FAIL.getCode().equals(code) ||
                ResultCode.STOCK_RECOVER_FAIL.getCode().equals(code)) {
                log.warn("乐观锁冲突，消息将重试：orderId={}", e.getMessage());
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.warn("业务异常，确认消息：errorCode={}, message={}", code, e.getMessage());
                channel.basicAck(deliveryTag, false);
            }

        } catch (Exception e) {
            log.error("系统异常，消息将重试", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private long getRetryCount(Message message) {
        Object count = message.getMessageProperties().getHeader("x-retry-count");
        return count == null ? 0 : Long.parseLong(count.toString());
    }
}
```

---

## 五、业务处理核心逻辑

```java
@Log("订单超时自动取消")
@Transactional(rollbackFor = Exception.class)
public void cancelExpireOrderByOrderTimeMessage(OrderTimeoutMessage msg) {
    // 1. 查询订单
    Orders order = ordersMapper.selectById(msg.getOrderId());
    if (order == null) {
        throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
    }

    // 2. 幂等性检查（只有 PENDING 才能取消）
    if (order.getStatus() != OrderStatus.PENDING.getValue()) {
        log.info("订单已处理，跳过");
        return;
    }

    // 3. 快照时间检查（异常检测）
    if (msg.getExpireTimestamp() > System.currentTimeMillis()) {
        log.error("消息提前到达");
        throw new BusinessException(ResultCode.PREMATURE_DELIVERY);
    }

    // 4. 数据库时间检查（最终裁决）
    if (order.getExpireTime().isAfter(LocalDateTime.now())) {
        log.warn("订单尚未超时");
        throw new BusinessException(ResultCode.ORDER_NOT_EXPIRE);
    }

    // 5. 恢复库存（乐观锁 @Version）
    Book book = bookMapper.selectById(msg.getBookId());
    if (book == null) {
        throw new BusinessException(ResultCode.BOOK_NOT_FOUND);
    }
    book.setStock(book.getStock() + msg.getQuantity());
    if (bookMapper.updateById(book) == 0) {
        throw new BusinessException(ResultCode.STOCK_RECOVER_FAIL);
    }

    // 6. 取消订单（条件更新）
    boolean updated = ordersMapper.update(null,
        new LambdaUpdateWrapper<Orders>()
            .set(Orders::getStatus, OrderStatus.CANCELLED.getValue())
            .eq(Orders::getId, msg.getOrderId())
            .eq(Orders::getStatus, OrderStatus.PENDING.getValue())
    ) > 0;

    if (!updated) {
        throw new BusinessException(ResultCode.ORDER_UPDATE_FAIL);
    }
}
```

**重要原则**：数据库写操作（恢复库存、取消订单）必须写在最后，确保前面的校验全部通过后才执行。

---

## 六、核心数据表设计

```sql
CREATE TABLE broker_message_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID（全局唯一）',
    exchange VARCHAR(100) NOT NULL COMMENT 'MQ交换机',
    routing_key VARCHAR(100) NOT NULL COMMENT 'MQ路由键',
    message_body JSON NOT NULL COMMENT '消息体（JSON格式）',
    delay_time INT NOT NULL COMMENT '延迟时间（毫秒）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0:PENDING 1:SENT 2:FAILED 3:FINAL_FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME NOT NULL COMMENT '下次重试时间',
    error_msg VARCHAR(500) DEFAULT NULL COMMENT '最后一次错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_id (message_id),
    INDEX idx_status_next_retry (status, next_retry_time),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息日志表（本地消息表）';
```

### 消息状态枚举

```java
@Getter
@AllArgsConstructor
public enum MessageStatus {
    PENDING(0, "待发送"),
    SENT(1, "已发送"),
    FAILED(2, "发送失败"),
    FINAL_FAILED(3, "最终失败");
    
    private final Integer code;
    private final String desc;
}
```

### 订单状态枚举

```java
@Getter
@AllArgsConstructor
public enum OrderStatus {
    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    CANCELLED(2, "已取消");
    
    private final Integer value;
    private final String desc;
}
```

---

## 七、两种重试对比

| 维度 | 生产者端重试 | 消费者端重试 |
|:---|:---|:---|
| **重试触发者** | CompensateJob 定时扫描 | MQ 自身（basicNack） |
| **重试计数** | 数据库 `retry_count` | 消息头 `x-retry-count` |
| **超过次数** | 状态 → FINAL_FAILED | 消息 → 死信队列 |
| **告警触发** | AlertService | AlertService |
| **重试间隔** | 5 分钟（可配置）| 立即（由 MQ 控制）|

---

## 八、关键设计原则

| 原则 | 说明 |
|:---|:---|
| **重试前置拦截** | Consumer 在 try 块前判断 `retryCount >= maxRetry`，统一拦截 |
| **数据库写操作在后** | 恢复库存、取消订单在业务校验全部通过后才执行 |
| **事务保证一致性** | 恢复库存 + 取消订单在同一事务中 |
| **乐观锁防并发** | 库存操作用 `@Version` |
| **条件更新防覆盖** | 订单状态用 `UPDATE ... WHERE status = PENDING` |
| **快照 + 数据库双重检查** | 超时判断既用快照又查数据库，互不替代 |
| **日志分级** | INFO 正常、WARN 可自愈、ERROR 需人工介入 |
| **分层控制** | Consumer 控制 MQ 确认，Service 控制数据库事务 |

---

## 九、核心配置

### application.yml

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: manual  # 手动确认

mall:
  message:
    delay-time: 1800000           # 30分钟（毫秒）
    max-retry: 3
    initial-retry-delay-seconds: 30
    retry-interval-minutes: 5
    compensate-batch-size: 100
```

### MessageProperties 配置类

```java
@Data
@Component
@ConfigurationProperties(prefix = "mall.message")
public class MessageProperties {
    private Long delayTime = 30 * 60 * 1000L;
    private Integer maxRetry = 3;
    private Integer initialRetryDelaySeconds = 30;
    private Integer retryIntervalMinutes = 5;
    private Integer compensateBatchSize = 100;
}
```

---

## 十、面试话术

### 问题1：订单超时取消怎么设计的？

> "我的订单超时取消方案包含三个核心部分：
>
> **1. 消息可靠性保证**
> - 订单创建时，在同一个事务里保存消息日志，状态为 `PENDING`
> - 事务提交后异步发送 MQ，发送成功更新为 `SENT`，失败更新为 `FAILED`
> - 补偿任务每5分钟扫描 `PENDING` 和 `FAILED` 状态的消息重新发送，最多重试3次
> - 超过3次触发告警
>
> **2. 消息消费保证**
> - 30分钟后消息到达，消费时先查订单状态和超时时间
> - 乐观锁冲突可重试，业务异常直接确认不重试
> - 系统异常重试，最多3次，超过进死信队列
>
> **3. 数据一致性保证**
> - 恢复库存用 `@Version` 乐观锁
> - 取消订单用状态条件更新
> - 恢复库存和取消订单在同一事务中"

### 问题2：Producer 和 Consumer 的重试有什么区别？

> "Producer 的重试由 `CompensateJob` 定时任务触发，重试次数记录在数据库 `retry_count` 字段。Consumer 的重试由 MQ 自身的 `basicNack(requeue=true)` 触发，重试次数记录在消息头 `x-retry-count`。
>
> 两者超过最大重试次数后都会触发 `AlertService` 告警，Producer 端状态变为 `FINAL_FAILED`，Consumer 端消息进入死信队列。"

### 问题3：为什么要用快照 + 数据库双重检查？

> "快照用于**快速判断和异常检测**，数据库用于**最终裁决**。如果消息提前到达，快照能发现异常并触发告警。如果管理员修改了订单的超时时间，数据库能保证准确性。两者互不替代。"

---

## 📚 相关文档

- [RabbitMQ 延迟消息配置](./rabbitmq-config.md)
- [本地消息表设计](./broker-message-log.md)
- [错误码规范](./result-code.md)

---

> **文档版本**：v1.0
> **最后更新**：2026-06-26
> **维护人**：jiaolei
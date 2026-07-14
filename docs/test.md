```markdown
# 秒杀系统 - 完整测试方案

> 基于当前秒杀系统代码，覆盖正常路径、异常路径、补偿机制、并发场景的完整测试树

---

## 一、测试树（业务流程全覆盖）

```

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              秒杀业务测试树                                         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  1. 正常路径 ✅                                                                     │
│  ├── 1.1 秒杀成功 → 订单创建 → 库存扣减 → 秒杀记录SUCCESS                           │
│  ├── 1.2 秒杀成功 → MQ消费者落库 → 订单创建 → 库存扣减                              │
│  ├── 1.3 秒杀成功 → 查询秒杀结果 → 返回SUCCESS                                      │
│  └── 1.4 秒杀成功 → 订单支付 → 状态变为PAID → 库存不变                              │
│                                                                                     │
│  2. 超时取消路径 ⏰                                                                 │
│  ├── 2.1 秒杀成功 → 超时消息到达 → 取消成功 → 库存恢复 → Redis回滚 → 状态EXPIRED   │
│  ├── 2.2 秒杀成功 → 已支付 → 超时消息到达 → 取消跳过 → 状态PAID → 库存不变          │
│  ├── 2.3 秒杀成功 → 超时消息消费失败（3次）→ 消费者死信告警 🚨                      │
│  └── 2.4 超时消息消费者抛异常 → 重试3次失败 → 死信队列 → 告警 🚨                   │
│                                                                                     │
│  3. 生产者重试路径 📤                                                               │
│  ├── 3.1 MQ发送失败 → 本地消息表PENDING → 补偿任务重发 → 成功 → SENT                │
│  ├── 3.2 MQ发送失败 → 重试3次失败 → 最终失败 → 生产者告警 🚨                       │
│  └── 3.3 MQ发送成功 → 状态SENT → 无补偿                                              │
│                                                                                     │
│  4. 补偿任务路径 🔄                                                                 │
│  ├── 4.1 扫描到卡住的PENDING订单 → 重新取消 → 成功 → 日志 ✅                        │
│  ├── 4.2 扫描到卡住的PENDING订单 → 重新取消 → 失败3次 → 告警 🚨                    │
│  └── 4.3 扫描到卡住的PENDING订单 → 订单已支付 → 跳过（幂等）                         │
│                                                                                     │
│  5. 缓存一致性路径 📦                                                               │
│  ├── 5.1 秒杀成功 → 删除用户标记 → 清理Redis缓存                                    │
│  ├── 5.2 超时取消 → 恢复Redis库存 → 删除用户标记                                    │
│  └── 5.3 Redis回滚失败 → 补偿任务记录 → 告警 🚨                                    │
│                                                                                     │
│  6. 异常边界路径 ⚠️                                                                │
│  ├── 6.1 库存不足 → 返回STOCK_EMPTY → 不生成订单 → Redis不回滚（Lua原子失败）       │
│  ├── 6.2 重复抢购 → 返回REPEAT_ORDER → 不生成订单 → Redis不回滚                     │
│  ├── 6.3 活动未开始/已结束 → 返回对应错误 → 不生成订单                               │
│  ├── 6.4 消息解析失败 → ACK确认 → 无回滚 ✅                                         │
│  ├── 6.5 订单已支付 → 超时取消跳过 → 无回滚 ✅                                      │
│  └── 6.6 用户取消 → 订单取消 → 恢复库存 → 回滚Redis ✅                              │
│                                                                                     │
│  7. 并发路径 🔀                                                                     │
│  ├── 7.1 超时取消 vs 支付 → 先到先得 → 另一个失败                                   │
│  ├── 7.2 超时取消 vs 用户取消 → 先到先得 → 另一个失败                               │
│  └── 7.3 多个用户同时抢购 → 库存正确扣减 → 无超卖                                   │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘

```


## 二、测试执行优先级

| 优先级 | 测试路径 | 执行方式 | 数据验证点 | 状态 |
|--------|----------|----------|-----------|------|
| **P0** | 1.1 正常秒杀 → 订单创建 | Postman | DB订单、秒杀记录、Redis库存 | |
| **P0** | 2.1 超时取消成功 → 状态EXPIRED | 等待超时 | DB状态、库存、Redis回滚 | |
| **P0** | 3.1 生产者补偿成功 | 停MQ测试 | 消息日志PENDING→SENT | |
| **P0** | 6.1 库存不足 | Postman | 返回错误，无数据变化 | |
| **P0** | 6.2 重复抢购 | Postman | 返回错误，无数据变化 | |
| **P1** | 7.3 并发秒杀 | JMeter | 库存正确，无超卖 | |
| **P1** | 2.2 已支付订单超时跳过 | 支付后等待超时 | 订单状态PAID | |
| **P1** | 4.1 补偿任务成功 | 模拟卡住订单 | 补偿日志 | |
| **P2** | 3.2 生产者最大重试告警 | 持续停MQ | 告警触发 | |
| **P2** | 2.3 消费者死信告警 | 消费者抛异常 | 死信队列+告警 | |
| **P2** | 5.3 Redis回滚失败补偿 | 模拟Redis异常 | 补偿记录+告警 | |


## 三、数据验证清单（测试时必须检查）

| 数据源 | 检查点 | 验证方法 |
|--------|--------|----------|
| **DB orders** | 订单状态、订单类型、expire_time | SELECT * FROM orders WHERE id = {orderId}; |
| **DB seckill_record** | 状态、关联订单ID | SELECT * FROM seckill_record WHERE id = {recordId}; |
| **DB seckill_book** | 库存数量 | SELECT stock FROM seckill_book WHERE book_id = {bookId}; |
| **DB broker_message_log** | 状态、重试次数 | SELECT status, retry_count FROM broker_message_log WHERE message_id = {messageId}; |
| **Redis stock** | seckill:stock:{bookId} | redis-cli GET seckill:stock:{bookId} |
| **Redis user** | seckill:user:{bookId}:{userId} | redis-cli SMEMBERS seckill:user:{bookId}:{userId} |
| **Redis queue** | seckill:queue:{bookId} | redis-cli ZRANGE seckill:queue:{bookId} 0 -1 |
| **Redis retry** | message:retry:{messageId} | redis-cli GET message:retry:{messageId} |
| **RabbitMQ** | 队列消息数、死信队列 | `rabbitmqctl list_queues` |
| **日志** | 成功日志、告警日志 | tail -f logs/app.log \\| grep -E "告警|成功" |


## 四、测试工具与命令

### 1. 快速验证脚本

```bash
#!/bin/bash
# 秒杀一键验证脚本

# 1. 登录获取Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \\
  -H "Content-Type: application/json" \\
  -d '{"usernameOrEmail":"testuser1","password":"123456"}' \\
  | jq -r '.data.token')

echo "Token: ${TOKEN}"

# 2. 秒杀抢购
RESPONSE=$(curl -s -X POST http://localhost:8080/api/seckill/1 \\
  -H "Authorization: Bearer ${TOKEN}" \\
  -H "Content-Type: application/json" \\
  -d '{}')

echo "秒杀响应: ${RESPONSE}"

# 3. 获取recordId
RECORD_ID=$(echo ${RESPONSE} | jq -r '.data.recordId')
echo "Record ID: ${RECORD_ID}"

# 4. 轮询结果（最多20次）
for i in {1..20}; do
  RESULT=$(curl -s -X GET http://localhost:8080/api/seckill/result/${RECORD_ID} \\
    -H "Authorization: Bearer ${TOKEN}")
  STATUS=$(echo ${RESULT} | jq -r '.data.status')
  echo "第 ${i} 次轮询: ${STATUS}"
  if [ "${STATUS}" = "SUCCESS" ]; then
    echo "✅ 抢购成功！"
    break
  elif [ "${STATUS}" = "FAILED" ]; then
    echo "❌ 抢购失败"
    break
  fi
  sleep 1
done
```

2. 数据库验证SQL

```sql
-- 1. 查看最新订单
SELECT id, order_no, user_id, book_id, status, order_type, expire_time, created_at 
FROM orders 
ORDER BY id DESC LIMIT 1;

-- 2. 查看秒杀记录
SELECT id, user_id, book_id, status, order_id, created_at 
FROM seckill_record 
ORDER BY id DESC LIMIT 1;

-- 3. 查看消息日志
SELECT message_id, exchange, status, retry_count, next_retry_time, create_time 
FROM broker_message_log 
ORDER BY id DESC LIMIT 5;

-- 4. 查看库存一致性
SELECT b.stock AS book_stock, sb.stock AS seckill_stock 
FROM book b 
JOIN seckill_book sb ON b.id = sb.book_id 
WHERE b.id = 1;
```

3. Redis 验证命令

```bash
# 查看所有秒杀相关key
redis-cli KEYS "seckill:*"

# 查看库存
redis-cli GET seckill:stock:1

# 查看用户标记
redis-cli SMEMBERS seckill:user:1:2

# 查看排队队列
redis-cli ZRANGE seckill:queue:1 0 -1 WITHSCORES

# 查看消息重试计数
redis-cli KEYS "message:retry:*" | xargs -I {} sh -c "echo {}; redis-cli GET {}"
```

4. RabbitMQ 验证命令

```bash
# 查看所有队列
rabbitmqctl list_queues

# 查看死信队列
rabbitmqctl list_queues | grep dlq

# 清理队列（测试用）
rabbitmqctl purge_queue seckill.queue
rabbitmqctl purge_queue seckill.dlq
rabbitmqctl purge_queue seckill.delay.queue
rabbitmqctl purge_queue seckill.order.Timeout.queue
```

五、测试数据准备

```sql
-- 1. 测试用户
INSERT INTO user (id, username, email, password, address) VALUES 
(1001, 'testuser1', 'test1@test.com', '$2a$10$...', '北京市朝阳区测试路123号'),
(1002, 'testuser2', 'test2@test.com', '$2a$10$...', '上海市浦东新区测试路456号');

-- 2. 测试书籍
INSERT INTO book (id, name, price, stock) VALUES 
(1, 'Java核心技术', 99.90, 100);

-- 3. 秒杀商品（活动进行中）
INSERT INTO seckill_book (id, book_id, seckill_price, stock, start_time, end_time, version) VALUES 
(1, 1, 9.90, 100, DATE_SUB(NOW(), INTERVAL 10 HOUR), DATE_ADD(NOW(), INTERVAL 10 HOUR), 0);

-- 4. 清理历史测试数据
DELETE FROM seckill_record WHERE user_id IN (1001, 1002);
DELETE FROM orders WHERE user_id IN (1001, 1002);
```

```bash
# 清理Redis测试数据
redis-cli DEL seckill:stock:1
redis-cli DEL seckill:user:1:1001
redis-cli DEL seckill:user:1:1002
redis-cli DEL seckill:queue:1
redis-cli --scan --pattern "message:retry:*" | xargs redis-cli DEL
```

六、测试通过标准

测试路径 通过标准
正常秒杀 订单创建成功，库存减少，秒杀记录SUCCESS，Redis库存同步
超时取消 订单状态EXPIRED，库存恢复，Redis回滚，用户标记删除
已支付订单 超时消息跳过，订单保持PAID，库存不变
库存不足 返回STOCK_EMPTY，无订单创建，无Redis回滚
重复抢购 返回REPEAT_ORDER，无订单创建，无Redis回滚
生产者补偿 消息日志从PENDING→SENT，订单正常
消费者死信 消息进入死信队列，告警触发
并发秒杀 库存正确扣减，无超卖，订单数正确
补偿任务 卡住的PENDING订单被取消，库存恢复，Redis回滚

七、执行建议

阶段 内容 负责角色
第1轮 P0路径测试（Postman脚本） 后端自测
第2轮 P1路径测试（含JMeter并发） 后端自测
第3轮 P2路径测试（异常+告警） 后端自测
第4轮 全链路回归测试 测试团队协助

---

文档版本：v1.0
最后更新：2026-07-09
维护人：jiaolei

```
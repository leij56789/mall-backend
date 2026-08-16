好的！我来帮你把全链路追踪的内容整合进项目上下文，并生成一份完整的 Markdown 文件。

---

📋 项目上下文快照（完整版）

复制以下全部内容，粘贴到新对话中，即可恢复完整上下文

---

一、当前目标

我是一名 Java 后端开发，正在找工作，目标岗位是 初级 / 1-3 年经验 Java 后端开发工程师。

我需要用两个完整项目展示独立开发能力，并能够清晰回答面试官的所有技术追问。

---

二、项目一：RealWorld 博客平台

项目定位：对标 Medium.com 的博客平台，展示完整业务能力

技术栈：

· Spring Boot 4.x + MyBatis-Plus + MySQL + Redis + RabbitMQ + JWT + Docker

核心功能（20+ RESTful API）：

· 用户认证：JWT + ThreadLocal 传递用户上下文，BCrypt 加密
· 文章管理：CRUD + 分页查询 + 标签关联
· 评论系统：关联查询 + Redis 缓存
· 点赞功能：Redis Set 存储点赞关系 + 异步同步到 DB
· 关注功能：中间表维护关注关系，防重复关注
· Feed 流：拉模式，查询关注作者的文章列表
· 缓存穿透防护：空值缓存（5分钟 TTL）
· AOP 日志 + Guava RateLimiter 限流
· Docker 容器化部署

技术亮点：

· JWT 认证全流程 + ThreadLocal 用户上下文传递
· 缓存穿透/雪崩/击穿解决方案
· Feed 流拉模式设计
· AOP 解耦日志和限流

---

三、项目二：商城项目（技术深度补充）

项目定位：展示分布式系统、高并发场景的深度技术

技术栈：

· Spring Boot 4.x + MyBatis-Plus + MySQL + Redis + RabbitMQ + JWT + Docker
· Redisson 分布式锁
· 全链路追踪（自研轻量级方案）

---

3.1 订单超时重试（核心亮点）

· 延迟消息：RabbitMQ TTL + 死信队列（4.3.x 无延迟插件）
· 本地消息表：保证消息 100% 投递，补偿任务兜底
· 乐观锁：MyBatis-Plus @Version 防止库存超卖
· 幂等性：状态码条件更新 WHERE status = PENDING
· Redis 原子计数：消费端重试次数控制（最多 3 次）
· 死信队列：超过重试次数进入死信 + 告警
· 手动 ACK：精确控制消息确认/重试/死信

---

3.2 秒杀系统（面试高价值接口）

· Lua 脚本：Redis 原子扣库存，防止超卖
· 分布式锁：Redisson 实现缓存重建互斥，tryLock(0, -1, SECONDS) 快速失败
· 缓存设计：Redis 缓存库存 + 秒杀商品信息，双重检查锁定（Double-Checked Locking）
· MQ 异步落库：秒杀请求立即返回 PENDING，消费者异步创建订单
· 限购防重：Redis Set 存储已抢购用户（seckill:users:{bookId}）
· 排队队列：Redis ZSet 记录用户排队顺序
· 超时取消：秒杀订单 15 分钟未支付自动取消，恢复 DB + Redis 库存
· 事务回滚回调：TransactionSynchronization 自动回滚 Redis（事务失败时）
· 补偿任务：扫描卡住的 PENDING 订单，重新取消 + 告警

---

3.3 消息可靠性（生产者 + 消费者）

· 生产者：本地消息表 broker_message_log（PENDING → SENT / FAILED → FINAL_FAILED）
· 补偿任务：CompensateJob 每 20 秒扫描补偿失败消息
· 消费者：区分业务异常（不重试）和系统异常（重试 3 次）
· 死信告警：超过重试次数 → 死信队列 + AlertService 告警

---

3.4 通用消息生产者（扩展性设计）

· GenericMessageProducer：根据 exchange + routingKey 动态发送
· 新增消息类型无需修改生产者代码

---

3.5 🔥 全链路追踪系统（自研亮点）

设计目标：让一次请求的所有日志（包括异步线程、MQ 消费者、定时任务）能够被完整串联，方便线上问题排查和本地调试。

3.5.1 核心组件

组件 作用
TraceContext 统一管理 traceId、spanId、parentSpanId、userId、tenantId、grayTag 等上下文的生成、传递和清理
TraceConstants 集中管理所有上下文键名（遵循 B3 / W3C 标准）
HandlerInterceptor Web 请求入口：生成 traceId 和根 spanId，解析 JWT 获取 userId，注入 MDC
FeignRequestInterceptor Feign 出站请求：从 MDC 提取上下文，自动塞入 HTTP 请求头（透传下游）
MessagePostProcessor RabbitMQ 发送前拦截：从 MDC 提取上下文，自动塞入消息头（透传消费者）
RabbitListenerTraceAspect MQ 消费者切面：从消息头提取上下文，注入消费者线程 MDC
TaskDecorator @Async 异步任务：捕获父线程 MDC 快照，在子线程中恢复上下文
LogAspect 统一日志切面：打印方法入参、出参、耗时，记录调用层级

3.5.2 跨线程 / 跨进程传递方案

场景 传递方式 说明
Web 请求 → Controller/Service HandlerInterceptor + MDC 入口生成，同线程自动继承
Service → @Async 异步任务 TaskDecorator 捕获 MDC 快照 父线程提交任务时捕获，子线程执行前恢复
Service → MQ 生产者 MessagePostProcessor 塞入消息头 发送前从 MDC 提取，写入 MessageProperties
MQ 消费者 → 业务方法 RabbitListenerTraceAspect 提取消息头 消费前从消息头读取，注入消费者线程 MDC
Service → Feign 调用下游 FeignRequestInterceptor 塞入请求头 调用前从 MDC 提取，写入 HTTP Header

3.5.3 调试级编号树（CallSeq）

在本地调试时，可以为同一个 traceId 下的所有方法生成调用层级编号（如 1-2-1），将纯文本日志还原为可视化的调用树：

```
[1] 进入 SeckillController.seckill()
[1-1] 进入 SeckillService.checkStock()
[1-1-1] 进入 RedisService.getStock()
[1-1-1] 库存: 10
[1-1] 库存检查通过
[1-2] 进入 SeckillProducer.sendSeckillMessage()
[1-2-1] 进入 GenericMessageProducer.trySend()  // 异步线程
[1-2-1-1] 进入 SeckillConsumer.handle()        // MQ 消费者
```

· 开关控制：通过 application.yml 中的 call.seq.enabled: true/false 控制，默认关闭。
· 可插拔：开启时生成编号辅助调试，关闭时零性能损耗，对生产代码无侵入。

3.5.4 日志配置（logback-spring.xml）

```xml
<pattern>
    %d{yyyy-MM-dd HH:mm:ss.SSS}
    [%X{traceId}] [%X{callSeq}] [%X{spanId}] [%X{parentSpanId}]
    [%X{userId}] [%X{bookId}]
    %-5level %logger{36} - %msg%n
</pattern>
```

3.5.5 技术亮点

· 轻量级自研：不依赖 SkyWalking / Zipkin 等重组件，基于 MDC + ThreadLocal 实现
· 全链路覆盖：Web 请求、异步线程（@Async）、MQ 生产者/消费者、Feign 调用、定时任务
· 调试友好：callSeq 编号树让文本日志可视化，极大提升本地 Debug 效率
· 零侵入：通过 AOP + 拦截器实现，业务代码无感知
· 可插拔：开关控制，生产环境关闭，性能无损

---

3.6 数据库设计

· 7 张核心表：用户、书籍、订单、购物车、分类、秒杀商品、秒杀记录
· broker_message_log 本地消息表（含 user_id、book_id 字段）
· orders 表含 order_type 字段区分普通/秒杀订单
· 乐观锁 version 防并发

---

四、关键技术深度

技术点 掌握程度 说明
JWT + ThreadLocal ✅ 熟练 认证流程、用户上下文传递
BCrypt 加密 ✅ 熟练 密码加密、匹配验证
乐观锁（@Version） ✅ 熟练 防超卖、库存扣减/恢复
悲观锁 vs 乐观锁 ✅ 熟练 适用场景、性能对比
Redis 原子操作 ✅ 熟练 INCR、Lua 脚本、Set/ZSet
分布式锁 ✅ 熟练 Redisson、tryLock、看门狗机制
RabbitMQ ✅ 熟练 TTL+死信、延迟消息、手动 ACK
本地消息表 ✅ 熟练 最终一致性、补偿任务
事务管理 ✅ 熟练 @Transactional、事务传播、回滚条件
缓存设计 ✅ 熟练 穿透/雪崩/击穿、空值缓存、布隆过滤器
分页查询 ✅ 熟练 MyBatis-Plus Page、深分页优化
索引优化 ✅ 熟练 EXPLAIN、慢 SQL 排查
全链路追踪 ✅ 熟练 自研 TraceId/SpanId 传递 + MDC + TaskDecorator + MQ/Feign 透传
MDC 跨线程传递 ✅ 熟练 TaskDecorator 捕获快照，子线程恢复上下文
调试级编号树 ✅ 熟练 callSeq 调用栈编号，文本日志可视化

---

五、项目亮点（面试核心话术）

订单超时重试

“RabbitMQ 4.3.x 不支持延迟插件，我用 TTL + 死信队列实现延迟消息。通过本地消息表 + 补偿任务保证消息 100% 投递。乐观锁 + 状态码条件更新保证并发安全。消费端用 Redis 原子计数控制重试次数，超过 3 次进入死信队列并告警。”

秒杀系统

“用 Redis Lua 脚本原子扣库存，防止超卖。Redisson 分布式锁 + 双重检查保证缓存单次加载。MQ 异步落库，秒杀请求立即返回。超时取消自动回滚 DB + Redis 库存。事务回滚回调保证 Redis 和 DB 最终一致性。”

消息可靠性

“本地消息表 + 补偿任务 + 手动 ACK 三重保障。生产者发送失败由补偿任务重试，消费者业务异常不重试，系统异常重试 3 次后进入死信。通用生产者支持动态 exchange/routingKey，新增消息类型无需改代码。”

全链路追踪 🔥

“我自研了一套轻量级全链路追踪方案，基于 MDC + ThreadLocal 实现，不依赖 SkyWalking 等重组件。通过 HandlerInterceptor 在入口生成 traceId 和 spanId，通过 TaskDecorator 实现 @Async 异步任务的上下文传递，通过 MessagePostProcessor 和 RabbitListenerTraceAspect 实现 MQ 生产者和消费者的跨进程透传。

此外，我还开发了一套调试级编号树（callSeq），在本地调试时为同一个 traceId 下的所有方法生成调用层级编号（如 1-2-1），将纯文本日志还原为可视化的调用树，极大提升了复杂场景下的调试效率。

这套方案的核心优势是：轻量、零侵入、可插拔——业务代码完全无感知，生产环境可通过开关关闭，性能无损。”

---

六、面试准备

类型 准备程度
项目介绍 ✅ 熟练
技术追问 ✅ 按维度分类话术
分布式锁 ✅ 原理 + 代码
乐观锁 vs 悲观锁 ✅ 对比清晰
消息可靠性 ✅ 本地消息表 + 补偿
秒杀防超卖 ✅ Lua + 乐观锁双保障
Redis 缓存一致性 ✅ 延迟双删 + 补偿
全链路追踪 ✅ TraceId + SpanId + MDC + TaskDecorator + MQ/Feign 透传 + callSeq 编号树

---

七、GitHub 仓库

· RealWorld 后端：https://github.com/leij56789/realworld-backend
· 商城后端：https://github.com/leij56789/mall-backend

---

八、当前进度

· ✅ RealWorld 博客平台（已完成）
· ✅ 商城订单超时重试（已完成）
· ✅ 商城秒杀系统（已完成）
· ✅ 面试话术整理（已完成）
· ✅ 全链路追踪系统（已完成）
· ⬜ 订单列表/详情接口（待完成）
· ⬜ 购物车功能（待完成）

---

九、常用命令参考

```bash
# 删除 RabbitMQ 队列
rabbitmqctl delete_queue delay.queue
rabbitmqctl delete_queue orderTimeout.queue
rabbitmqctl delete_queue seckill.queue

# 查看 Redis 缓存
redis-cli KEYS "seckill:*"
redis-cli GET seckill:stock:1
redis-cli SMEMBERS seckill:users:1

# 查看 MQ 队列
rabbitmqctl list_queues

# 查看 JMeter 压测报告
# 通过 JMeter GUI 或 CLI 生成
```

---

直接复制以上全部内容，粘贴到新对话即可继续。 🚀
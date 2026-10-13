好的，我已将本对话（2026-09-01 至 2026-09-25）中积累的全部技术点整合到最终版快照中。以下是可直接复制到新对话的完整版上下文快照：

---

项目上下文快照（最终版）

版本：2026-09-25（含微服务拆分、Seata 实战、性能压测调优全部技术点）

---

一、个人定位

· 目标岗位：初级 / 1-3 年经验 Java 后端开发工程师
· 当前水平：具备独立设计并实现完整支付/秒杀系统的能力，对高并发、分布式事务、最终一致性、可观测性、微服务拆分、性能压测调优有实战经验
· 项目定位：用两个完整项目（博客平台 + 商城）展示独立开发能力，商城项目已从单体拆分为微服务

---

二、本次对话新增技术点（2026-09 期间）

模块一：分库分表与分布式 ID

技术点 掌握程度 说明
水平分库 / 垂直分库 / 垂直分表 ✅ 掌握 先垂直分库隔离业务，再水平分库分摊压力，最后垂直分表冷热分离
基因法（Gene Method） ✅ 掌握 将 userId 的路由信息编码到 orderId 的 bit 位中，实现零映射表路由
orderId 与分片路由解耦 ✅ 掌握 订单号包含 userId（不是分片位），扩容时改 userId % N 的 N 即可，订单号不改
分库分表扩容 ✅ 掌握 双倍扩容（16→32 只影响 50%）+ 灰度迁移（双写→灰度读→全量切→下线旧表）
雪花算法 sequence ✅ 掌握 (sequence + 1) & 4095 是 % 4096 的位运算优化，因为 4096 = 2^12
& 位运算 ✅ 掌握 按位与，n & (2^m - 1) 等价于 n % 2^m，比取模快 20-30 倍

模块二：SSE 推送（替代 WebSocket 的单向场景）

技术点 掌握程度 说明
SSE 服务端推送 ✅ 掌握 SseEmitter + ConcurrentHashMap 管理会话 + @Async 异步推送
SSE 事务边界 ✅ 掌握 必须在事务提交后推送（TransactionSynchronization.afterCommit）
SSE 心跳机制 ✅ 掌握 每 30 秒发 ping，防 Nginx/Tomcat 中间件超时断开（不重置 SseEmitter 超时）
SSE vs WebSocket 选型 ✅ 掌握 单向推送用 SSE（轻量、自动重连），双向通信用 WebSocket
SSE 轮询降级 ✅ 掌握 前端 SSE 断开后降级到轮询（2秒间隔，超时40秒）

模块三：Nacos 微服务治理实战

技术点 掌握程度 说明
Nacos 3.x vs 2.x 差异 ✅ 掌握 3.x 默认 8080 端口，移除了 v1 API；2.x 默认 8848 端口，支持 v1 API
Nacos 密钥配置 ✅ 掌握 nacos.core.auth.plugin.nacos.token.secret.key（≥32位 Base64）、server.identity
Nacos 3.x 与 Seata 不兼容 ✅ 掌握 Seata 内置 nacos-client-1.4.6 调 v1 API，Nacos 3.x 已移除，报 501
config is empty 问题 ✅ 掌握 spring.config.import 替代 shared-configs（后者在 SCA 2025.x 失效）
配置读取路径 ✅ 掌握 optional:nacos:mall-common-config.yaml?group=DEFAULT_GROUP&refreshEnabled=false
Nacos 2.x 认证配置 ✅ 掌握 spring.cloud.nacos.discovery.username/password + spring.cloud.nacos.config.username/password

模块四：微服务拆分实战（mall-fusion）

技术点 掌握程度 说明
父子 Maven 工程 ✅ 掌握 父工程管理版本，子模块继承；<parent> 的 version 不能引用 ${}
微服务拆分原则 ✅ 掌握 seckill-core（Lua扣库存）、seckill-order（MQ消费+订单）、seckill-compensate（补偿）
.env 文件独立配置 ✅ 掌握 每个微服务独立 .env，用 spring.config.import=optional:file:.env
公共模块 mall-common ✅ 掌握 实体、枚举、Mapper、DTO、工具类、告警服务放公共模块
Service 接口 vs 实现分离 ✅ 掌握 接口放 common，实现放各微服务（避免循环依赖）
Request/Response DTO 放 common ✅ 掌握 Feign 调用需要跨服务共享，Controller 放各服务

模块五：Seata 分布式事务实战

技术点 掌握程度 说明
Seata Server 2.5.0 部署 ✅ 掌握 MySQL 驱动放 lib/jdbc、建表 SQL（5 张）、store.mode=db
Nacos 2.5.4 + Seata 2.5.0 集成 ✅ 掌握 registry.conf + Nacos 配置 seataServer.properties
AT 模式接入 ✅ 掌握 @GlobalTransactional + undo_log 表 + 数据源代理
TCC 模式接入 ✅ 掌握 @TwoPhaseBusinessAction + @LocalTCC + tcc_fence_log 表
TCC 空回滚 / 悬挂 / 幂等 ✅ 掌握 tcc_fence_log 自动处理：SUSPENDED（空回滚）、防悬挂
TCC 真回滚 vs 假回滚 ✅ 掌握 Try 自身抛异常是数据库真回滚；Try 成功后 Cancel 是业务补偿（假回滚）
Saga 模式（2.5.0 有 bug） ⚠️ 了解 StateMachineEngineHolder 包名分裂（io.seata vs org.apache.seata）
Seata 2.6.0 Saga 修复 ⚠️ 了解 SagaResourceManager 调整到 org.apache.seata.saga.rm，需手动补多个 SPI 依赖

模块六：性能压测与调优实战（本次对话重点）

技术点 掌握程度 说明
JMeter 压测配置 ✅ 掌握 Threads / Ramp-up / Loop / SyncTimer / CSV Data Set Config
瞬时并发 vs 持续吞吐 ✅ 掌握 SyncTimer=1 测瞬时；Loop=10 + 禁用 SyncTimer 测持续吞吐
控制变量法调优 ✅ 掌握 一次只改一个配置，对比响应时间分布
JWT 拦截器优化 ✅ 掌握 3 次解析合并为 1 次（parseToken 返回 Claims）
JWT Redis 缓存 ✅ 掌握 登录时写缓存（`sub
fastjson2 反序列化坑 ✅ 掌握 首次反序列化某类时 ASM 动态生成字节码，200线程抢锁耗时100ms
HikariCP 连接池调优 ✅ 掌握 10 → 100：Max 从 627ms 降到 303ms
Redis 连接池调优 ✅ 掌握 8 → 100：Median 从 206ms 降到 120ms
Tomcat 线程池调优 ✅ 掌握 400 → 800：提升有限，说明 400 已够用
Arthas 实战 ✅ 掌握 thread -n 20、dashboard、trace、watch 定位瓶颈
端到端延迟测量 ✅ 掌握 seckill_record.created_at 和 orders.created_at JOIN 计算延迟
MySQL DATETIME(3) ✅ 掌握 秒级精度改成毫秒级，精确测量端到端延迟
乐观锁冲突 vs SQL 原子扣减 ✅ 掌握 乐观锁在高并发下大量冲突（同一行），改 UPDATE ... WHERE stock >= ? 消除冲突
瓶颈转移现象 ✅ 掌握 优化一处后瓶颈转移，如乐观锁消除后 DB 查询成为新瓶颈
MQ 消费并发调优 ✅ 掌握 concurrency=5→20 需谨慎，prefetch 影响消息分配
MySQL 内部并发竞争 ✅ 掌握 并发事务竞争 redo log / buffer pool latch / MVCC read view，不是连接池问题

模块七：JWT 与认证优化

技术点 掌握程度 说明
JwtInterceptor 优化 ✅ 掌握 从 3 次解析（validateToken + getUsername + getUserId）合并为 1 次
JWT 缓存格式 ✅ 掌握 `sub
登录时写缓存 ✅ 掌握 生成 Token 时顺手写 Redis，避免首次请求冷启动
JWT 缓存 TTL ✅ 掌握 和 Token 剩余有效期一致，防止过期 Token 被误用
Redis 缓存降级 ✅ 掌握 Redis 不可用时降级到直接解析 JWT，不影响业务

模块八：DB 优化

技术点 掌握程度 说明
SQL 原子扣减 ✅ 掌握 UPDATE ... SET stock = stock - ? WHERE stock >= ?，靠行锁串行化
乐观锁适用场景 ✅ 掌握 更新冲突率低（如库存恢复）；冲突率高时反而更慢
HikariCP 与事务边界 ✅ 掌握 事务全程持有连接，@Transactional 包住的方法越长，连接占用越久
DB 查询缓存化 ✅ 掌握 频繁查询的数据（用户、商品）改 Redis 缓存，减少 DB 竞争

模块九：面试方法论

技术点 掌握程度 说明
性能优化故事讲法 ✅ 掌握 "基线→控制变量→发现瓶颈→优化→再对比"的完整叙事
不迷信 QPS 数字 ✅ 掌握 面试官看方法论，不看数字大小
性能优化看全局 ✅ 掌握 单条消息快不代表端到端快；端到端降了说明方向对
Arthas 定位流程 ✅ 掌握 thread -n 20 找最忙线程 → trace 看方法内耗时 → dashboard 看 JVM

---

三、技术能力全景图（分层，已更新）

第一层：核心基础

技术点 掌握程度 说明
Spring Boot 4.x ✅ 熟练 自动配置、启动流程、常用 Starter
MyBatis-Plus ✅ 熟练 条件构造器、分页、乐观锁（@Version）、代码生成器
MySQL ✅ 熟练 索引优化、事务隔离级别、行锁、慢查询分析、DATETIME(3) 毫秒精度、SQL 原子扣减
RESTful API ✅ 熟练 统一返回格式、异常处理、参数校验（@Valid）
JWT + ThreadLocal ✅ 熟练 用户上下文传递、登录拦截、JWT 解析合并 + Redis 缓存
BCrypt 加密 ✅ 熟练 密码加密与匹配验证

第二层：分布式/高并发（核心竞争力）

技术点 掌握程度 说明
Redis ✅ 熟练 Lua 原子扣库存、分布式锁（Redisson）、Set/ZSet 防重/排队、JWT 缓存
RabbitMQ ✅ 熟练 TTL + 死信队列实现延迟消息、手动 ACK、重试与死信闭环、消费并发调优
Redisson 分布式锁 ✅ 熟练 tryLock(0, -1, SECONDS)、看门狗机制、锁粒度设计
分布式事务（最终一致性） ✅ 熟练 本地消息表 + 补偿任务 + TransactionSynchronization 回滚回调
乐观锁（@Version） ✅ 熟练 并发库存扣减/恢复、冲突率优化（改 SQL 原子扣减）
悲观锁（行锁 FOR UPDATE） ✅ 熟练 查询 + 行锁 + 事务边界设计
缓存一致性 ✅ 熟练 延迟双删、Redis 回滚策略（三种场景）、补偿告警
Seata AT / TCC ✅ 熟练 AT 模式（undo_log）、TCC 模式（tcc_fence_log）、空回滚/悬挂/幂等
微服务拆分 ✅ 熟练 父子 Maven 工程、公共模块 mall-common、服务独立部署

第三层：可观测性与工程规范（加分项）

技术点 掌握程度 说明
全链路追踪（自研轻量级） ✅ 熟练 MDC + ThreadLocal + TraceId/SpanId/ParentSpanId 传递
跨线程上下文传递 ✅ 熟练 TaskDecorator 捕获 MDC 快照，@Async 自动继承
跨进程上下文传递 ✅ 熟练 MQ 消息头/Feign 请求头透传 + 消费者拦截器注入
调用层级编号树（CallSeq） ✅ 熟练 本地调试专用，文本日志可视化，可插拔开关
AOP 统一日志切面 ✅ 熟练 方法入参/出参/耗时记录，IDEA 控制台超链接直达源码
分布式锁与事务边界分离 ✅ 熟练 锁包裹方法调用，事务独立短连接，防止连接池耗尽
支付状态机设计 ✅ 熟练 7 种状态、状态转换矩阵、补偿分层（即时 + 系统级）
多渠道支付抽象 ✅ 熟练 策略模式 + 工厂模式，支付宝/微信统一接口，渠道差异隔离
延迟消息补偿闭环 ✅ 熟练 即时补偿（10秒）+ 系统补偿（5分钟扫描），最终一致性
钉钉告警接入 ✅ 熟练 自定义机器人 + 加签 + Webhook + @Async 异步发送
内网穿透调试 ✅ 熟练 cloudflared / ngrok / localtunnel 隧道配置与回调调试
支付回调安全 ✅ 熟练 验签 + 幂等（状态条件更新）+ 公网回调地址配置
Arthas 实战 ✅ 熟练 thread -n 20 / dashboard / trace / watch 定位瓶颈
JMeter 压测 ✅ 熟练 Threads / Ramp-up / Loop / SyncTimer / CSV Data Set Config
控制变量法调优 ✅ 熟练 一次只改一个配置，对比响应时间分布
端到端延迟测量 ✅ 熟练 JOIN seckill_record 和 orders 的 created_at

第四层：云原生与架构趋势（了解）

技术点 掌握程度 说明
Nacos 微服务治理 ✅ 熟练 服务发现 + 配置中心 + 3.x vs 2.x 差异
Spring Cloud Gateway ⚠️ 了解 API 网关路由、限流、鉴权
Docker / K8s ⚠️ 了解 容器化部署
Serverless ⚠️ 了解 函数计算，适合事件驱动、无状态场景

---

四、核心项目亮点（面试话术）

1. 秒杀系统 —— 横向一致性 + 纵向一致性双重保障

“我用 Redis Lua 脚本原子扣库存实现横向一致（防超卖），配合 Redisson 分布式锁防缓存击穿，乐观锁（@Version）防 DB 覆盖。对于 DB、Redis、MQ 三层之间的纵向一致，我设计了三种回滚策略：正常提交随事务、失败异步补偿、业务回滚反向恢复。MQ 消息可靠性通过本地消息表 + 补偿任务保证，超过 3 次重试进入死信并告警。”

2. 订单超时重试 —— 无延迟插件的优雅降级

“RabbitMQ 4.3.x 不支持延迟插件，我用 TTL + 死信队列实现了延迟消息。通过本地消息表 + 补偿任务保证消息 100% 投递，乐观锁 + 状态码条件更新保证并发安全，消费端用 Redis 原子计数控制重试次数，超限进入死信队列并触发告警。”

3. 全链路追踪 —— 轻量级自研方案

“我自研了轻量级全链路追踪，基于 MDC + ThreadLocal 实现，不依赖 SkyWalking。通过 HandlerInterceptor 生成 traceId/spanId，通过 TaskDecorator 实现 @Async 跨线程传递，通过 MessagePostProcessor 和 RabbitListenerTraceAspect 实现 MQ 跨进程透传。同时开发了调试级编号树（callSeq），将纯文本日志还原为可视化的调用树，极大提升本地 Debug 效率。”

4. 支付模块 —— 状态机与双补偿闭环

“支付单状态机有 7 个状态，状态转换完全依赖第三方查询结果。对于第三方接口超时/异常，我设计了 即时补偿（10秒后执行） 和 系统级补偿（每5分钟扫描） 两层兜底机制，确保任何情况下支付单最终都能收敛到终态。状态转换与支付渠道（微信/支付宝）完全解耦，通过策略模式 + 工厂模式实现多渠道支持。”

5. 钉钉告警与可观测性

“我接入了钉钉告警，通过自定义机器人 + 加签安全配置，在支付异常、补偿任务失败等关键节点自动发送告警。特别需要注意的是，钉钉加签时签名原文必须用换行符 \\n，而不是 \\\\n 字面量，否则会导致 310000 签名错误。告警通过 @Async 异步发送，不影响主业务性能。同时，我使用 cloudflared 隧道在开发环境调试支付宝回调，解决了公网可达性问题。”

6. 微服务拆分实战（新增）

“我把原来的商城单体应用拆成了微服务架构：seckill-core（Lua扣库存）、seckill-order（MQ消费+订单）、seckill-compensate（补偿任务）。用父子 Maven 工程管理版本，公共模块 mall-common 放实体、枚举、DTO、工具类。服务之间通过 Nacos 服务发现 + OpenFeign 通信。中间踩了很多坑，比如 Nacos 3.x 与 Seata 2.5.0 不兼容、shared-configs 在 Spring Cloud Alibaba 2025.x 失效、JWT 缓存反序列化格式不匹配等。”

7. 性能压测调优实战（新增，重点）

“我做了完整的端到端压测调优。基线 QPS 227，用控制变量法逐步优化：

第一步，JWT 拦截器优化：发现 JWT 拦截器耗时 166ms，因为同一个 Token 被解析了 3 次。合并为 1 次后降到 2ms。

第二步，连接池调优：HikariCP 10→100，Max 从 627ms 降到 303ms；Redis 8→100，Median 从 206ms 降到 120ms。

第三步，压测策略优化：发现 SyncTimer 禁用后 QPS 从 197 提升到 673，因为平缓流量比瞬时冲击吞吐更高。

第四步，乐观锁改 SQL 原子扣减：原来 5 个消费者同时"查后改"同一行库存，大量乐观锁冲突，消息重试重新入队，端到端延迟 1536ms。改成 UPDATE ... SET stock = stock - 1 WHERE stock >= 1 后，冲突消除，端到端降到 1188ms。

第五步，分析瓶颈转移：发现优化后单条消息耗时从 7ms 涨到 339ms，其中查商品 263ms。分析发现不是代码变慢，而是 5 个消费者同时干活，MySQL 内部资源竞争（redo log、buffer pool latch、MVCC）导致。

最终 QPS 达到 1393，端到端延迟 avg 1188ms，Max 1817ms。 整套优化用了 Arthas 定位瓶颈、JMeter 压测、控制变量法对比。”

---

五、技术深度问答准备

技术点 你能回答的深度
分布式锁（Redisson） 看门狗续期机制，tryLock(0, -1) 不设持有时间时，每 10 秒续期 30 秒。锁粒度设计：锁内不能包含网络 IO，必须与事务边界分离。
乐观锁 vs 悲观锁 乐观锁适用于更新冲突率低的场景（如库存恢复），用 @Version 避免长事务；高并发下乐观锁冲突多时反而慢，改 SQL 原子扣减最优。悲观锁适用于"查后写"且冲突率高的场景，用 SELECT FOR UPDATE。
消息可靠性 生产者：@Transactional + 本地消息表 + afterCommit 异步发送；消费者：手动 ACK + Redis 原子计数控制重试（最多 3 次）+ 死信队列告警；补偿任务：每 20 秒扫描 PENDING 消息重发。
缓存一致性 延迟双删 + 补偿任务：先删缓存，再更新 DB，延迟后二次删缓存。秒杀场景中，Redis 扣库存成功但 DB 事务回滚时，通过 TransactionSynchronization 监听事务状态，失败时自动回滚 Redis。
支付回调幂等性 用 paymentId + 状态条件更新（WHERE status = 'WAITING'）保证幂等，重复回调直接返回 SUCCESS。
线程池与 MDC 传递 @Async 默认无法传递 MDC，通过 TaskDecorator 捕获父线程 MDC 快照，在子线程执行前恢复，执行后 finally 清理。
跨进程 TraceId 传递 HTTP：Feign RequestInterceptor 透传 Header；MQ：MessagePostProcessor 注入消息头，消费者 RabbitListenerTraceAspect 提取并注入 MDC。
钉钉告警加签 钉钉加签时签名原文必须用 timestamp + "\\n" + secret，其中 \\n 是换行符，不是 \\\\n 字面量，否则报 310000。
分库分表路由 所有路由基于 userId，不基于 orderId。orderId 里的基因（userId 编码）只用于反推 userId，路由永远用 userId % N。扩容时改 N 即可，订单号不改。
SSE 服务端推送 SSE 用 SseEmitter 保持长连接，是 GET 请求，基于 HTTP 协议。心跳防止中间件超时（不重置 SseEmitter 超时）。事务提交后再推送，避免前端读到脏数据。
Seata AT 模式 加 @GlobalTransactional + undo_log 表。AT 的"回滚"本质是"补偿"，通过 undo_log 前镜像反向 SQL 补偿。
Seata TCC 模式 @TwoPhaseBusinessAction + tcc_fence_log。Try 操作 frozen 字段，Confirm 真实扣减，Cancel 释放 frozen。tcc_fence_log 自动处理空回滚、悬挂、幂等。
性能优化方法论 基线→控制变量→发现瓶颈（Arthas）→优化→再对比。不迷信 QPS 数字，看响应时间分布。瓶颈会转移，优化是迭代过程。
MySQL 内部并发竞争 并发事务不仅竞争连接，还竞争 redo log、buffer pool latch、MVCC read view。5 个消费者查同一行，MySQL 内部也有排队。
JWT 优化三层 **第一层：合并 3 次解析为 1 次；第二层：Redis 缓存 Claims；第三层：登录时写缓存（不是解析时）。缓存格式用管道 `sub

---

六、简历与面试技术亮点提炼

可写到简历上的关键词

· 高并发：秒杀系统（Redis Lua + 分布式锁 + MQ 削峰）
· 分布式事务：本地消息表 + 补偿任务 + 事务回滚回调 + Seata AT/TCC
· 可观测性：自研全链路追踪（TraceId/SpanId/ParentSpanId）+ Arthas 定位瓶颈
· 支付系统：状态机 + 双补偿闭环 + 多渠道适配（策略模式）
· 消息可靠性：RabbitMQ 延迟消息 + 死信队列 + 手动 ACK
· 缓存一致性：延迟双删 + Redis 回滚策略 + JWT 缓存优化
· 调试效率：调用层级编号树（CallSeq）+ AOP 统一日志 + Arthas
· 告警与运维：钉钉告警集成 + 内网穿透调试 + 回调幂等处理
· 微服务：Nacos 服务发现 + OpenFeign + 父子 Maven 工程 + 公共模块拆分
· 性能压测：JMeter + 控制变量法 + 端到端延迟测量（DATETIME(3)）
· 分库分表：基因法 + 双倍扩容 + 灰度迁移
· SSE 推送：SseEmitter 长连接 + 心跳 + 事务提交后推送

面试中可以主动引导的技术话题

1. “你在项目中遇到过分布式事务问题吗？怎么解决的？” → 引导到本地消息表 + 补偿任务 + Seata
2. “支付系统怎么保证最终一致性？” → 引导到状态机 + 即时补偿 + 系统级补偿
3. “你怎么排查线上问题？” → 引导到自研链路追踪 + TraceId + 调用编号树 + Arthas
4. “高并发下怎么防超卖？” → 引导到 Redis Lua + 乐观锁 + 分布式锁三道防线
5. “怎么设计一个可扩展的支付模块？” → 引导到策略模式 + 工厂模式 + 统一 PayClient 接口
6. “怎么保证支付回调的幂等性？” → 引导到状态条件更新 + 验签
7. “开发环境怎么调试支付宝回调？” → 引导到 cloudflared 隧道 + 钉钉告警辅助排查
8. “你怎么做性能优化？” → 引导到控制变量法 + Arthas + JMeter + 端到端延迟测量
9. “分库分表怎么路由？” → 引导到基因法 + userId 分片 + 双倍扩容
10. “SSE 和 WebSocket 怎么选？” → 引导到单向 vs 双向、HTTP 长连接 vs 独立协议
11. “微服务拆分遇到过什么坑？” → 引导到 Nacos 3.x 与 Seata 不兼容、shared-configs 失效、JWT 反序列化格式

---

七、技术进步方向（下一步可学）

方向 说明 优先级
Spring Cloud Gateway API 网关路由、限流、鉴权 ⭐⭐⭐ 高
Sentinel 熔断降级、限流、系统保护 ⭐⭐⭐ 高
SkyWalking / Prometheus + Grafana 生产级 APM 与监控 ⭐⭐ 中
Docker / Kubernetes 容器化部署与编排 ⭐⭐ 中
Seata 深入（TCC 场景扩展） TCC 补偿在生产中的最佳实践 ⭐⭐ 中
Elasticsearch 复杂查询、日志检索 ⭐ 低
支付对账系统 每日自动对账、差错处理 ⭐ 低

---

八、GitHub 仓库

· RealWorld 后端：https://github.com/leij56789/realworld-backend
· 商城后端（微服务版）：https://github.com/leij56789/mall-backend

---

九、下一阶段开发计划

阶段 内容 优先级
阶段一 端到端压测收口（整理面试素材 + GitHub README） ⭐⭐⭐ 高
阶段二 退款补偿查询 + 退款记录表完善 ⭐⭐⭐ 高
阶段三 对账单下载与解析 ⭐⭐ 中
阶段四 审计日志完善 + 哈希链验证接口 ⭐⭐ 中
阶段五 微信支付接入（Native / JSAPI） ⭐ 低
阶段六 支付对账系统 ⭐ 低

---

十、当前项目运行状态（截至 2026-09-25）

服务 端口 状态 说明
Nacos 2.5.4 8848 ✅ 运行中 服务注册 + 配置中心
Seata Server 2.5.0 8091 ✅ 运行中 db 模式，TC 协调器
seckill-core-service 8081 ✅ 运行中 Lua 扣库存 + 发 MQ
seckill-order-service 8082 ✅ 运行中 MQ 消费 + 创建订单
seckill-compensate-service 8083 ✅ 运行中 补偿任务
MySQL 3306 ✅ 运行中 5 张 Seata 表 + 业务表
Redis 6379 ✅ 运行中 秒杀库存 + JWT 缓存
RabbitMQ 5672 ✅ 运行中 秒杀消息 + 延迟消息

当前最优配置：

· Tomcat: max=800, accept-count=1000, max-connections=20000
· HikariCP: maximum-pool-size=100, minimum-idle=20
· Redis: max-active=100, max-idle=50, min-idle=20
· RabbitMQ: concurrency=5, max-concurrency=10, prefetch=1

当前压测数据：

· QPS: 1393（Loop=10）
· 端到端 avg: 1188ms, max: 1817ms
· 无 5xx 错误，乐观锁冲突已消除

---

直接复制以上全部内容到新对话，即可恢复完整上下文，并携带本对话（2026-09 微服务拆分、Seata 实战、性能压测调优）的全部技术积累。🚀
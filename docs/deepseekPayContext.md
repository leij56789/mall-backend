支付模块改造 — 上下文交接文档

项目与环境

· 项目：mall-backend（Spring Boot 4.0.6 / Java 17 / MyBatis-Plus / MySQL / Redisson）
· 老版本备份：mall-backend-old-2026-10-13（改造前，留着查历史）
· 系统 B：wecom-bot-platform（Spring Boot 4.0.0 / Java 25 / MyBatis-Plus 3.5.15），等待支付模块接入
· GitHub：git@github.com:leij56789/mall-backend.git，分支 main
· 终端：PowerShell（不要用 Git Bash，注意路径用 cd D:\\Work\\Workspace\\mall-backend）

当前进度总览

已完成 4 轮大改造：

阶段 内容 tag
Step 1 PayFacade 门面 + 7 DTO pay-facade-v1
Step 1.5 payment_order 加 biz 字段 pay-biz-fields-v1 / pay-schema-biz-v1
Step 2a 抽 AlipaySignatureVerifier pay-signature-verifier-v1
Step 2b 异步通知 + 重试（NotifyRetryScheduler） pay-notify-retry-v1
Step 3a/3b PayFacade.pay/query/close pay-facade-impl-v1
Step 3c PayFacade.refund 等 3 方法 pay-facade-refund-v1
Step 3d 退款异步通知（RefundNotifyScheduler） pay-refund-notify-v1
P0 修复 回调并发/退款 CAS/事务外/notifyExecutor pay-p0-fixed-v1
P1 修复 补偿查询不误标 FAILED / updateFailed 落 notify pay-p1-fixed-v1
定时任务统一 支付定时任务归 com.mall.pay.job pay-jobs-unified-v1
控制台 PayQueryResult 加 notify + 控制台展示 pay-query-notify-v1
第 1 批搬家 PaymentRefundNotifyLog → pay.entity/mapper pay-refund-notify-log-moved-v1
第 2 批搬家 PaymentAuditLog → pay.entity/mapper pay-audit-log-moved-v1
第 3 批搬家 PaymentRefundRecord → pay.entity/mapper pay-refund-record-moved-v1
第 4 批搬家 PaymentOrder + XML → pay.entity/mapper pay-order-moved-v1
枚举第 1 批 RefundStatus / AlipayExtKey / WechatPayErrorCode → pay.enums pay-enums-moved-1-v1
枚举第 2 批 AuditOperation / AuditResult / AuditTargetType → pay.enums pay-enums-moved-2-v1
枚举第 3 批 PaymentStatus / PaymentChannel / PaymentMethod → pay.enums pay-enums-moved-3-v1
兜底/及时性拆分 PaymentCompensateJob 恢复超时扫；新增 PaymentFastCompensateJob 扫 WAITING 未超时只补 SUCCESS pay-compensate-split-v1
审计退化修复（进行中） PayFacade 加 @AuditLog，AuditLogAspect 支持从返回值取 paymentId/refundRecordId 未完成

重要业务逻辑

兜底任务 PaymentCompensateJob（每 30 秒）

· 扫 INIT/WAITING/PENDING_CONFIRM 且 expired_at < NOW()
· ACQ.TRADE_NOT_EXIST 归 FATAL → 标 FAILED（不重试）
· default 分支返回 true（不算失败，避免误告警）
· 网络异常 → 5 次告警，保留 PENDING_CONFIRM
· last_query_time 不再更新（恢复原逻辑）

及时性任务 PaymentFastCompensateJob（每 60 秒，新增）

· 扫 WAITING 且 expired_at > NOW() 且 last_query_time < now-2min
· 只处理 TRADE_SUCCESS → 补 SUCCESS（回调不可达场景）
· FATAL / 其他 → 跳过，不改状态、不告警
· 网络异常 → 5 次告警

通知机制

· NotifyRetryScheduler：支付成功/失败通知业务侧，3 次重试 + 告警，成功后清空 notify_next_time
· RefundNotifyScheduler：退款成功/失败通知业务侧，同上
· @Async("notify")（注意：ThreadPoolManager 里的 bean 名是 notify，不是 notifyExecutor）

枚举映射（AlipaySignatureVerifier / AbstractAlipayPayClientAdapter）

· ACQ.TRADE_NOT_EXIST → THIRD_PARTY_FATAL_ERROR（实践确认：用户没付，重试无用）
· ACQ.SYSTEM_ERROR → THIRD_PARTY_RETRYABLE_ERROR
· ACQ.INVALID_PARAMETER → THIRD_PARTY_FATAL_ERROR
· ACQ.ENTERPRISE_PAY_BIZ_ERROR → THIRD_PARTY_MANUAL_INTERVENTION

当前 com.mall.pay 结构

```
com.mall.pay/
├── adapter/       AlertServiceAdapter / PaymentApplicationAdapter / PaymentOrderRepositoryAdapter
├── audit/         AuditStateMapper / ParamExtractor
├── channel/       alipay/ wechat/ mock/
├── client/        PayClient / BillDownloadClient / RefundQueryClient
├── config/        PayProperties / PayClientFactory
├── dto/           支付 DTO
├── entity/        PaymentOrder / PaymentRefundRecord / PaymentAuditLog / PaymentRefundNotifyLog  ← 全搬家完成
├── enums/         RefundStatus / AlipayExtKey / pay/WechatPayErrorCode / AuditOperation / AuditResult / AuditTargetType / PaymentStatus / PaymentChannel / PaymentMethod  ← 全搬家完成
├── event/
├── facade/        PayFacade / PaymentCallback / dto/ / impl/PayFacadeImpl
├── job/           NotifyRetryScheduler / RefundNotifyScheduler / PaymentCompensateJob / RefundCompensateJob / PaymentFastCompensateJob
├── mapper/        PaymentOrderMapper / PaymentRefundRecordMapper / PaymentAuditLogMapper / PaymentRefundNotifyLogMapper
├── port/          AlertPort / PaymentApplicationPort / PaymentOrderRepository / model/PaymentOrderSnapshot
├── service/       AsyncQueryService / PaymentCallbackDispatcher / PaymentCallbackProcessor
└── state/         PaymentStateMachine
```

com.mall.pay 剩余 com.mall.* 依赖：

· com.mall.common.BusinessException
· com.mall.common.RedisKeys / RedisLockConfig
· com.mall.annotation.Log / AuditLog
· com.mall.enums.ResultCode（支付段 6000~6199，暂不拆）
· com.mall.service.AlertService（AlertServiceAdapter 用）
· com.mall.service.PaymentOrchestrationService / PaymentOrderService（adapter 用）

关键的 ParamExtractor 说明

com.mall.pay.audit.ParamExtractor.extractParamValue(joinPoint, "dto.paymentId") —— 支持 "obj.field" 表达式，从参数对象反射取字段。

当前正在做的改造：PayFacade 审计补齐

背景

· 老版本审计链 3 段：CREATE_PAYMENT（initPaymentWithLock @AuditLog 带 orderId）→ PAYMENT_CALLBACK（updatePaymentStatusToSuccess @AuditLog 带 paymentId）→ CREATE_REFUND（refundOrder @AuditLog 带 paymentId）
· PayFacadeImpl.pay / refund 没 @AuditLog → 新业务审计只有 PAYMENT_CALLBACK 1 段，缺 CREATE_PAYMENT / CREATE_REFUND，哈希链断
· 根因：AuditLogAspect 里 paymentId 从入参取，但 PayFacadeImpl.pay 的 paymentId 是 proceed() 后在返回值里生成的

已提出的方案（下一对话要执行）

改动 1：AuditLog 注解加 2 个字段（src/main/java/com/mall/annotation/AuditLog.java）：

```java
String paymentIdFromResult() default "";
String refundRecordIdFromResult() default "";
```

改动 2：AuditLogAspect 改 5 处（src/main/java/com/mall/aspect/AuditLogAspect.java）：

· 2a：解析 auditLog.paymentIdFromResult() / auditLog.refundRecordIdFromResult()
· 2b：targets.isEmpty() 判断改为 —— 同时检查 paymentIdFromResult/refundRecordIdFromResult 有无，有则继续 proceed()
· 2c：proceed() 后调用 fillTargetsFromResult(...) 补 targets
· 2d：加私有方法 fillTargetsFromResult(...)（从 result 取 paymentId/refundRecordId 补进 targets + beforeStates）+ extractFieldFromResult(result, fieldName)（反射取值，支持嵌套 "a.b"）
· 2e：finally 里判断 targets 空，空则跳过审计

改动 3：PayFacadeImpl.pay / refund 加 @AuditLog（src/main/java/com/mall/pay/facade/impl/PayFacadeImpl.java）：

```java
// pay
@AuditLog(
    targetTypes = {AuditTargetType.PAYMENT_ORDER},
    paymentIdFromResult = "paymentId",
    operation = AuditOperation.CREATE_PAYMENT,
    desc = "创建支付单（PayFacade）"
)

// refund
@AuditLog(
    targetTypes = {AuditTargetType.PAYMENT_ORDER, AuditTargetType.REFUND_RECORD},
    paymentId = "dto.paymentId",
    refundRecordIdFromResult = "refundId",
    operation = AuditOperation.CREATE_REFUND,
    desc = "创建退款（PayFacade）"
)
```

详细代码在上一轮对话中已给出——如果接手时想再看，可以让用户重发 AuditLogAspect 完整内容。

测试要点：跑一笔支付 + 退款 → 查 payment_audit_log 应有 3 段（CREATE_PAYMENT / PAYMENT_CALLBACK / CREATE_REFUND）→ 哈希链 prev_hash == 上一条 self_hash。

commit 计划：

```
fix(pay): PayFacade 加审计——AuditLogAspect 支持从返回值取 paymentId/refundRecordId（恢复 3 段审计链）
git tag pay-facade-audit-v1
```

关键文件路径

文件 路径
AuditLog 注解 src/main/java/com/mall/annotation/AuditLog.java
AuditLogAspect src/main/java/com/mall/aspect/AuditLogAspect.java
ParamExtractor src/main/java/com/mall/pay/audit/ParamExtractor.java
PayFacadeImpl src/main/java/com/mall/pay/facade/impl/PayFacadeImpl.java
RefundResult src/main/java/com/mall/pay/facade/dto/RefundResult.java
PayResult src/main/java/com/mall/pay/facade/dto/PayResult.java
PayOrderDTO src/main/java/com/mall/pay/facade/dto/PayOrderDTO.java
RefundDTO src/main/java/com/mall/pay/facade/dto/RefundDTO.java
测试控制台 src/main/resources/static/pay-facade-console.html
测试接口 src/main/java/com/mall/controller/PayFacadeTestController.java

工作习惯约定

· 每完成一步：mvn clean compile -DskipTests 确认 BUILD SUCCESS → 跑一笔真实支付宝沙箱支付回归 → git add 精确文件（避免用 git add -A，因为 docs/terminal.md、html/pay.html、html/rawForm.txt、src/test/java/com/mall/pay/AlipayFormToHtml.java 是测试产物不提交）→ git status --short 确认 → commit → tag → push
· 测试用 http://localhost:8080/pay-facade-console.html（必须通过 HTTP 访问，不能用 file://）
· 每次 git add 后如果 IDEA 又自动保存修改，会在 git status 显示  M —— 要再 git add 一次
· 改造步骤：先分析影响面 → 给出精确改动清单 → 编译验证 → 回归测试 → commit

接下来可做的事（优先顺序）

1. [进行中] 审计补齐（PayFacade 加 @AuditLog + AuditLogAspect 支持出参）
2. [高优先级] 复制支付模块到系统 B（wecom-bot-platform）：
   · 复制 com.mall.pay + ResultCode（6000~6199 段）+ BusinessException + Log + AuditLog + RedisKeys + RedisLockConfig
   · 复制 4 张表 DDL（payment_order / payment_refund_record / payment_audit_log / payment_refund_notify_log）
   · pom 加：alipay-sdk-java 4.40.0 / alipay-sdk-java-v3 3.1.78 / hutool-all 5.8.40 / redisson-spring-boot-starter
   · @MapperScan 加 com.mall.pay.mapper
   · 实现 2 个 Adapter：AlertPort / PaymentApplicationPort（系统 B 自定义）
   · 写正式 PayFacadeController
   · PayProperties 配支付宝沙箱 + Redisson
   · 测试
3. [可选] 枚举第 4 批：ResultCode 支付段（6000~6199）抽成 PayErrorCode —— 风险高，可能不改
4. [可选] P2 体检：状态机非法流转 / 分布式锁粒度

已知的待办/隐患

· application-dev.yaml 硬编码沙箱密钥（生产前必须整改）
· AuditLogAspect 里 userId == null 直接 return（支付回调 SYSTEM 时 userId=0 可能受此影响）
· payment_order.user_id、order_id 两字段业务侧还在用；PayFacade 用 biz_type/biz_id/user_id，order_id 为 null
· PaymentMethod.MOCK 的 channel 写的是 WECHAT（历史复制粘贴残留）

下一对话开场建议

"继续 mall-backend 支付模块改造。上一轮在做 PayFacade 审计补齐（AuditLogAspect 支持从返回值取 paymentId），代码还没改。请先让我确认 AuditLogAspect 当前内容，再给我精确改动。"

或者：

"审计补齐完成，开始复制支付模块到系统 B（wecom-bot-platform）。"
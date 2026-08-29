package com.mall.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.AuditLog;
import com.mall.common.BusinessException;
import com.mall.common.trace.constant.TraceConstants;
import com.mall.common.trace.context.TraceContext;
import com.mall.entity.PaymentOrder;
import com.mall.entity.PaymentRefundRecord;
import com.mall.enums.AuditOperation;
import com.mall.enums.AuditResult;
import com.mall.enums.AuditTargetType;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.mapper.PaymentRefundRecordMapper;
import com.mall.pay.audit.AuditStateMapper;
import com.mall.pay.audit.ParamExtractor;
import com.mall.service.PaymentAuditService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundRecordMapper refundRecordMapper;
    private final PaymentAuditService auditService;
    private final AuditStateMapper stateMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        Object[] args = joinPoint.getArgs();
        // ✅ 提取入参
        String requestParams = extractRequestParams(joinPoint);
        String requestBody = extractRequestBody(joinPoint);
        // 1. 获取目标类型（枚举数组）
        AuditTargetType[] targetTypes = auditLog.targetTypes();
        if (targetTypes == null || targetTypes.length == 0) {
            log.warn("审计注解未指定 targetTypes，跳过审计: method={}",
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        // 2. 解析参数名
        String paymentIdParam = auditLog.paymentId();
        String refundRecordIdParam = auditLog.refundRecordId();
        String orderIdParam = auditLog.orderId();

        List<String> paramNames = new ArrayList<>();
        if (StringUtils.hasText(paymentIdParam)) {
            paramNames.add(paymentIdParam);
        }
        if (StringUtils.hasText(refundRecordIdParam)) {
            paramNames.add(refundRecordIdParam);
        }
        if(StringUtils.hasText(orderIdParam)){
            paramNames.add(orderIdParam);
        }
        Map<String, Object> params = ParamExtractor.extractParams(
                joinPoint, paramNames.toArray(new String[0]));

        // 3. 构建审计目标列表（根据枚举判断）
        List<AuditTarget> targets = new ArrayList<>();

        // 判断是否包含 PAYMENT_ORDER
        if (Arrays.asList(targetTypes).contains(AuditTargetType.PAYMENT_ORDER)) {
            Object paymentIdValue = params.get(paymentIdParam);
            if (paymentIdValue != null) {
                targets.add(new AuditTarget(
                        AuditTargetType.PAYMENT_ORDER,
                        paymentIdValue.toString(),
                        null,
                        null
                ));
            } else {
                String found = findPaymentIdFromArgs(joinPoint.getArgs());
                if (found != null) {
                    targets.add(new AuditTarget(
                            AuditTargetType.PAYMENT_ORDER,
                            found,
                            null,
                            null
                    ));
                }
            }
        }

        // 判断是否包含 REFUND_RECORD
        if (Arrays.asList(targetTypes).contains(AuditTargetType.REFUND_RECORD)) {
            Object recordIdValue = ParamExtractor.extractParamValue(joinPoint, refundRecordIdParam);
            if (recordIdValue != null) {
                Long recordId = null;
                if (recordIdValue instanceof Long) {
                    recordId = (Long) recordIdValue;
                } else if (recordIdValue instanceof String) {
                    try {
                        recordId = Long.parseLong((String) recordIdValue);
                    } catch (NumberFormatException e) {
                        log.warn("recordId 格式错误: {}", recordIdValue);
                    }
                }
                if (recordId != null) {
                    targets.add(new AuditTarget(
                            AuditTargetType.REFUND_RECORD,
                            null,
                            recordId,
                            null
                    ));
                }
            } else {
                Long found = findRecordIdFromArgs(joinPoint.getArgs());
                if (found != null) {
                    targets.add(new AuditTarget(
                            AuditTargetType.REFUND_RECORD,
                            null,
                            found,
                            null
                    ));
                }
            }
        }
        // ? 处理 ORDER（通过 orderId 查询支付单）
        if (Arrays.asList(targetTypes).contains(AuditTargetType.ORDER)) {
            Object orderIdValue = ParamExtractor.extractParamValue(joinPoint, orderIdParam);
            if (orderIdValue != null) {
                targets.add(new AuditTarget(
                        AuditTargetType.ORDER,
                        null,
                        null,
                        orderIdValue.toString()
                ));
            }
        }

        // 如果无法提取任何目标，记录警告并继续执行业务（但不审计）
        if (targets.isEmpty()) {
            log.warn("无法提取审计目标，跳过审计: method={}", joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        // 5. 对每个目标执行审计
        Map<String, State> beforeStates = new HashMap<>();
        Map<String, State> afterStates = new HashMap<>();

        // 5a. 查询执行前的状态
        for (AuditTarget target : targets) {
            State state = queryState(target);
            beforeStates.put(target.getKey(), state);
        }

        // 5b. 执行业务方法
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;
        String responseBody = null;
        String errorCode=null;
        String errorMsg=null;

        try {
            result = joinPoint.proceed();
            // ✅ 提取出参
            responseBody = extractResponseBody(result);
            return result;
        } catch (Throwable e) {
            exception = e;
            // ✅ 异常时记录异常信息
            // ✅ 提取错误码和错误消息
            if (e instanceof BusinessException) {
                BusinessException be = (BusinessException) e;
                errorCode = String.valueOf(be.getCode());
                errorMsg = be.getMessage();
                responseBody = String.format("错误码: %s, 异常: %s", errorCode, errorMsg);
            } else {
                errorCode = "500";
                errorMsg = e.getClass().getSimpleName() + " - " + e.getMessage();
                responseBody = "异常: " + errorMsg;
            }
            throw e;
        } finally {
            long costMs = System.currentTimeMillis() - startTime;

            // 查询执行后的状态（仅当无异常时）
            afterStates = new HashMap<>();
            if (exception == null) {
                for (AuditTarget target : targets) {
                    State state = queryState(target);
                    afterStates.put(target.getKey(), state);
                }
            } else {
                log.warn("业务方法执行异常，跳过查询执行后状态: method={}", methodName);
            }

            // 记录审计日志（无论是否有异常都记录）
            try {
                for (AuditTarget target : targets) {
                    State before = beforeStates.get(target.getKey());
                    State after = afterStates.get(target.getKey());
                    recordAuditLog(target, before, after, auditLog, costMs,
                            requestParams, requestBody, responseBody, errorCode, errorMsg);
                }
            } catch (Exception e) {
                log.warn("审计日志记录失败: targets={}", targets, e);
            }
        }
    }
    /**
     * 提取请求参数（参数名 + 参数值）
     */
    private String extractRequestParams(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            if (paramNames == null || args == null || args.length == 0) {
                return "{}";
            }
            Map<String, Object> paramMap = new LinkedHashMap<>();
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                paramMap.put(paramNames[i], maskSensitiveValue(args[i]));
            }
            return objectMapper.writeValueAsString(paramMap);
        } catch (Exception e) {
            log.warn("提取请求参数失败", e);
            return "{}";
        }
    }

    /**
     * 提取请求体（参数值的 JSON 表示）
     */
    private String extractRequestBody(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return "{}";
            }
            // 如果只有一个参数，直接序列化；多个参数则序列化数组
            if (args.length == 1) {
                return objectMapper.writeValueAsString(maskSensitiveValue(args[0]));
            } else {
                return objectMapper.writeValueAsString(args);
            }
        } catch (Exception e) {
            log.warn("提取请求体失败", e);
            return "{}";
        }
    }

    /**
     * 提取响应体
     */
    private String extractResponseBody(Object result) {
        if (result == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(maskSensitiveValue(result));
        } catch (Exception e) {
            log.warn("提取响应体失败", e);
            return result.toString();
        }
    }

    /**
     * 脱敏处理（递归脱敏）
     */
    private Object maskSensitiveValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String str = (String) value;
            // 支付ID/订单号脱敏
            if (str.matches("\\d{15,20}")) {
                return str.substring(0, 4) + "****" + str.substring(str.length() - 4);
            }
            return str;
        }
        if (value instanceof PaymentOrder) {
            PaymentOrder order = (PaymentOrder) value;
            // 只保留关键字段，避免循环引用
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("paymentId", maskSensitiveValue(order.getPaymentId()));
            safe.put("orderId", order.getOrderId());
            safe.put("status", order.getStatus());
            safe.put("amount", order.getAmount());
            return safe;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> safe = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                safe.put(String.valueOf(entry.getKey()), maskSensitiveValue(entry.getValue()));
            }
            return safe;
        }
        if (value instanceof Collection) {
            Collection<?> coll = (Collection<?>) value;
            List<Object> safe = new ArrayList<>();
            for (Object item : coll) {
                safe.add(maskSensitiveValue(item));
            }
            return safe;
        }
        return value;
    }

    /**
     * 检查是否包含指定的目标类型
     */
    private boolean containsTargetType(String[] targetTypes, String type) {
        for (String t : targetTypes) {
            if (type.equals(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从参数中查找 paymentId
     */
    private String findPaymentIdFromArgs(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String) {
                String str = (String) arg;
                if (str.matches("\\d+") && str.length() > 10) {
                    return str;
                }
            }
        }
        return null;
    }

    /**
     * 从参数中查找 recordId
     */
    private Long findRecordIdFromArgs(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
        }
        return null;
    }

    /**
     * 查询实体状态
     */
    private State queryState(AuditTarget target) {
        if (target.getType() == AuditTargetType.PAYMENT_ORDER) {
            PaymentOrder order = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getPaymentId, target.getPaymentId())
            );
            if (order != null) {
                return new State(order.getStatus(), order.getPaymentId(), target.getPaymentId());
            }
            return new State(null, null, target.getPaymentId());
        }

        if (target.getType() == AuditTargetType.REFUND_RECORD) {
            PaymentRefundRecord record = refundRecordMapper.selectById(target.getRecordId());
            if (record != null) {
                return new State(record.getStatus(), record.getPaymentId(), target.getRecordId().toString());
            }
            return new State(null, null, target.getRecordId().toString());
        }

        // ✅ ORDER 类型：通过 orderId 查询支付单
        if (target.getType() == AuditTargetType.ORDER && target.getOrderId() != null) {
            PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getOrderId, Long.parseLong(target.getOrderId()))
                            .orderByDesc(PaymentOrder::getCreatedAt)
                            .last("LIMIT 1")
            );
            if (paymentOrder != null) {
                return new State(paymentOrder.getStatus(), paymentOrder.getPaymentId(), target.getOrderId());
            }
            // ✅ 支付单不存在：返回 null 状态（表示创建前）
            return new State(null, null, target.getOrderId());
        }
        return null;
    }

    /**
     * 记录审计日志
     */
    private void recordAuditLog(AuditTarget target, State before, State after,
                                AuditLog auditLog, long costMs,
                                String requestParams, String requestBody, String responseBody,
                                String errorCode, String errorMsg) {
        // 1. 获取前后状态
        String beforeStatus = before != null ? before.getStatus() : null;
        String afterStatus = after != null ? after.getStatus() : null;

        // ? 打印状态变化日志
        log.info("审计状态变化: target={}, beforeStatus={}, afterStatus={}",
                target, beforeStatus, afterStatus);

        // 2. 尝试自动推断操作类型
        String inferredOperation = stateMapper.inferOperation(beforeStatus, afterStatus);

        // ? 打印推断结果
        log.debug("推断操作类型: inferredOperation={}", inferredOperation);

        // 3. 决定最终操作类型
        String operation;
        if (auditLog.operation() != AuditOperation.UNKNOWN) {
            operation = auditLog.operation().getCode();
            log.debug("使用注解指定的操作类型: operation={}", operation);
        } else if (inferredOperation != null) {
            operation = inferredOperation;
            log.debug("使用推断的操作类型: operation={}", operation);
        } else {
            log.warn("无法推断操作类型，且注解未指定: target={}, beforeStatus={}, afterStatus={}",
                    target, beforeStatus, afterStatus);
            operation = "UNKNOWN";
        }

        // 4. 构建审计日志
        String paymentId = target.getPaymentId() != null ? target.getPaymentId() :
                (after != null ? after.getPaymentId() : null);

        String description = auditLog.desc();
        if (!StringUtils.hasText(description)) {
            description = "审计日志: " + operation;
        }

        // 从 TraceContext 获取审计信息
        String clientIp = TraceContext.getClientIp();
        String userAgent = TraceContext.getUserAgent();
        String userId = TraceContext.getUserId();
        if(userId==null){
            log.debug("记录审计日志失败，TraceContext.getUserId()={}",userId);
            return;
        }

        Long userIdLong=null;
        try {
            userIdLong = Long.valueOf(userId);
        } catch (NumberFormatException e) {
            log.debug("记录审计日志失败，TraceContext.getUserId()={}",userId);
            return;
        }
        // ... 记录审计日志 ...
        auditService.builder()
                .traceId(TraceContext.generateTraceId())
                .clientIp(clientIp)
                .userAgent(userAgent)
                .userId(userIdLong)
                .operatorType(userIdLong.equals(0L)? TraceConstants.OPERATOR_TYPE_SYSTEM:TraceConstants.OPERATOR_TYPE_USER)
                .paymentId(paymentId)
                .orderId(getOrderIdFromPaymentId(paymentId))
                .refundRecordId(target.getRecordId())
                .operation(operation)
                .operationDesc(description)
                .beforeStatus(beforeStatus)
                .afterStatus(afterStatus)
                .result(AuditResult.SUCCESS.getCode())
                .costMs(costMs)
                .requestParams(requestParams)
                .requestBody(requestBody)
                .responseBody(responseBody)
                .errorCode(errorCode)   // ✅ 新增
                .errorMsg(errorMsg)     // ✅ 新增
                .log();
    }
    /**
     * 通过 paymentId 查询状态（用于 ORDER 类型执行后）
     */
    private State queryStateByPaymentId(String paymentId) {
        PaymentOrder order = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getPaymentId, paymentId)
        );
        if (order != null) {
            return new State(order.getStatus(), order.getPaymentId(), paymentId);
        }
        return new State(null, null, paymentId);
    }

    private Long getOrderIdFromPaymentId(String paymentId) {
        if (paymentId == null) return null;
        try {
            PaymentOrder order = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getPaymentId, paymentId)
                            .select(PaymentOrder::getOrderId)
            );
            return order != null ? order.getOrderId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== 内部类 =====

    @Data
    @AllArgsConstructor
    private static class AuditTarget {
        private AuditTargetType type;
        private String paymentId;
        private Long recordId;
        private String orderId;

        public String getKey() {
            if (type == AuditTargetType.PAYMENT_ORDER && paymentId != null) {
                return type.name() + ":" + paymentId;
            }
            if (type == AuditTargetType.REFUND_RECORD && recordId != null) {
                return type.name() + ":" + recordId;
            }
            if (type == AuditTargetType.ORDER && orderId != null) {
                return type.name() + ":" + orderId;
            }
            return type.name() + ":unknown";
        }

    }

    @Data
    @AllArgsConstructor
    private static class State {
        private String status;
        private String paymentId;
        private String entityId;
    }
}
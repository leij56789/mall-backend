package com.mall.pay.audit.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.AuditLog;
import com.mall.common.BusinessException;
import com.mall.pay.enums.OperatorType;
import com.mall.common.trace.context.TraceContext;
import com.mall.pay.audit.AuditStateMapper;
import com.mall.pay.audit.ParamExtractor;
import com.mall.pay.entity.PaymentOrder;
import com.mall.pay.entity.PaymentRefundRecord;
import com.mall.pay.enums.AuditOperation;
import com.mall.pay.enums.AuditResult;
import com.mall.pay.enums.AuditTargetType;
import com.mall.pay.mapper.PaymentOrderMapper;
import com.mall.pay.mapper.PaymentRefundRecordMapper;
import com.mall.pay.audit.service.PaymentAuditService;
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

/**
 * 审计日志切面。
 *
 * <p>支持两种"目标来源"：
 * <ul>
 *   <li><b>入参来源</b>：paymentId / refundRecordId / orderId —— proceed 前构建，可查 beforeState</li>
 *   <li><b>返回值来源</b>：paymentIdFromResult / refundRecordIdFromResult —— proceed 后构建，beforeState 固定 null</li>
 * </ul>
 */
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

        // ========== 0. 入参快照 ==========
        String requestParams = extractRequestParams(joinPoint);
        String requestBody = extractRequestBody(joinPoint);

        // ========== 1. 校验 targetTypes ==========
        AuditTargetType[] targetTypes = auditLog.targetTypes();
        if (targetTypes == null || targetTypes.length == 0) {
            log.warn("审计注解未指定 targetTypes，跳过审计: method={}", methodName);
            return joinPoint.proceed();
        }
        List<AuditTargetType> typeList = Arrays.asList(targetTypes);

        // ========== 2. 解析注解声明 ==========
        String paymentIdParam = auditLog.paymentId();
        String refundRecordIdParam = auditLog.refundRecordId();
        String orderIdParam = auditLog.orderId();
        String paymentIdFromResult = auditLog.paymentIdFromResult();
        String refundRecordIdFromResult = auditLog.refundRecordIdFromResult();

        // ========== 3. proceed 前构建"入参来源"的 targets ==========
        List<AuditTarget> targets = new ArrayList<>();

        // 3a. 入参来源：PAYMENT_ORDER
        if (typeList.contains(AuditTargetType.PAYMENT_ORDER)) {
            String paymentId = null;
            if (StringUtils.hasText(paymentIdParam)) {
                Object v = ParamExtractor.extractParamValue(joinPoint, paymentIdParam);
                if (v != null) {
                    paymentId = v.toString();
                } else {
                    paymentId = findPaymentIdFromArgs(joinPoint.getArgs());
                }
            }
            // 无论 paymentId 是否为空，都建 target
            targets.add(new AuditTarget(AuditTargetType.PAYMENT_ORDER, paymentId, null, null));
        }

        // 3b. 入参来源：REFUND_RECORD
        if (typeList.contains(AuditTargetType.REFUND_RECORD) && StringUtils.hasText(refundRecordIdParam)) {
            Object recordIdValue = ParamExtractor.extractParamValue(joinPoint, refundRecordIdParam);
            if (recordIdValue != null) {
                Long recordId = parseLongOrNull(recordIdValue);
                if (recordId != null) {
                    targets.add(new AuditTarget(AuditTargetType.REFUND_RECORD, null, recordId, null));
                }
            } else {
                Long found = findRecordIdFromArgs(joinPoint.getArgs());
                if (found != null) {
                    targets.add(new AuditTarget(AuditTargetType.REFUND_RECORD, null, found, null));
                }
            }
        }

        // 3c. 入参来源：ORDER
        if (typeList.contains(AuditTargetType.ORDER) && StringUtils.hasText(orderIdParam)) {
            Object orderIdValue = ParamExtractor.extractParamValue(joinPoint, orderIdParam);
            if (orderIdValue != null) {
                targets.add(new AuditTarget(AuditTargetType.ORDER, null, null, orderIdValue.toString()));
            }
        }

        // 3d. 如果"入参来源"和"返回值来源"都没有，直接跳过审计
        boolean hasResultSource = StringUtils.hasText(paymentIdFromResult)
                || StringUtils.hasText(refundRecordIdFromResult);
        if (targets.isEmpty() && !hasResultSource) {
            log.warn("无法提取审计目标（无入参来源、无返回值来源），跳过审计: method={}", methodName);
            return joinPoint.proceed();
        }

        // ========== 4. 查 proceed 前状态 ==========
        Map<String, State> beforeStates = new HashMap<>();
        for (AuditTarget target : targets) {
            beforeStates.put(target.getKey(), queryState(target));
        }

        // ========== 5. 执行业务 ==========
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;
        String responseBody = null;
        String errorCode = null;
        String errorMsg = null;

        try {
            result = joinPoint.proceed();
            responseBody = extractResponseBody(result);
            return result;
        } catch (Throwable e) {
            exception = e;
            if (e instanceof BusinessException be) {
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

            // ========== 6. 从返回值取数据 ==========
            ResultExtras extras = new ResultExtras();
            if (exception == null) {
                extras = extractFromResult(result, paymentIdFromResult, refundRecordIdFromResult);
            }

            // ★ 新增：extras.paymentId 空时，从入参取（refund 用 dto.paymentId）
            if (extras.paymentId == null && StringUtils.hasText(paymentIdParam)) {
                Object v = ParamExtractor.extractParamValue(joinPoint, paymentIdParam);
                if (v != null) extras.paymentId = v.toString();
            }

            log.info("[AUDIT-DEBUG] method={}, resultClass={}, extras.paymentId={}, extras.refundRecordId={}, targets.size={}",
                    methodName, result == null ? "null" : result.getClass().getSimpleName(),
                    extras.paymentId, extras.refundRecordId, targets.size());

            // ========== 6.5 回填：把 extras.paymentId 填到已有的"null paymentId 的 PAYMENT_ORDER target" ==========
            if (StringUtils.hasText(extras.paymentId)) {
                for (AuditTarget target : targets) {
                    if (target.getType() == AuditTargetType.PAYMENT_ORDER
                            && target.getPaymentId() == null) {
                        String oldKey = target.getKey();           // PAYMENT_ORDER:unknown
                        target.setPaymentId(extras.paymentId);     // 回填
                        String newKey = target.getKey();           // PAYMENT_ORDER:899...
                        // beforeStates 的 key 迁移
                        State oldBefore = beforeStates.remove(oldKey);
                        if (oldBefore != null) {
                            // before 本来就是 null（新建单），保持 null
                            beforeStates.put(newKey, oldBefore);
                        } else {
                            beforeStates.put(newKey, new State(null, null, extras.paymentId));
                        }
                        log.info("[AUDIT-DEBUG] 6.5 回填 paymentId: {} -> {}", oldKey, newKey);
                        break;
                    }
                }
            }

            // ========== 7. 查 proceed 后状态 ==========
            Map<String, State> afterStates = new HashMap<>();
            if (exception == null) {
                for (AuditTarget target : targets) {
                    afterStates.put(target.getKey(), queryState(target));
                }
                // ★ 新增：extras.refundRecordId 有值，但 targets 没对应 target → 补查
                final Long extrasRefundRecordId = extras.refundRecordId;
                if (extrasRefundRecordId != null
                        && targets.stream().noneMatch(t -> t.getType() == AuditTargetType.REFUND_RECORD
                        && extrasRefundRecordId.equals(t.getRecordId()))) {
                    AuditTarget temp = new AuditTarget(AuditTargetType.REFUND_RECORD, null, extrasRefundRecordId, null);
                    afterStates.put(temp.getKey(), queryState(temp));
                }
            }
            log.info("[AUDIT-DEBUG] afterStates={}", afterStates);

            // ========== 8. 记录审计 ==========
            try {
                if (!targets.isEmpty()) {
                    for (AuditTarget target : targets) {
                        State b = beforeStates.get(target.getKey());
                        State a = afterStates.get(target.getKey());
                        log.info("[AUDIT-DEBUG] 写审计(target 分支): key={}, before={}, after={}", target.getKey(), b, a);
                        recordAuditLog(target, b, a, auditLog, costMs,
                                requestParams, requestBody, responseBody, errorCode, errorMsg, extras);
                    }
                } else if (StringUtils.hasText(extras.paymentId) || extras.refundRecordId != null) {
                    AuditTarget temp;
                    if (extras.refundRecordId != null) {
                        temp = new AuditTarget(AuditTargetType.REFUND_RECORD, null, extras.refundRecordId, null);
                    } else {
                        temp = new AuditTarget(AuditTargetType.PAYMENT_ORDER, extras.paymentId, null, null);
                    }
                    State b = beforeStates.get(temp.getKey());
                    State a = afterStates.get(temp.getKey());
                    log.info("[AUDIT-DEBUG] 写审计(extras 分支): key={}, before={}, after={}", temp.getKey(), b, a);
                    recordAuditLog(temp, b, a, auditLog, costMs,
                            requestParams, requestBody, responseBody, errorCode, errorMsg, extras);
                } else {
                    log.info("[AUDIT-DEBUG] 不写审计: targets 空且 extras 空");
                }
            } catch (Exception e) {
                log.warn("审计日志记录失败: targets={}", targets, e);
            }
        }
    }

    /**
     * 从返回值提取数据（不新建 target）。
     * 返回的 extras 在写审计时作为 paymentId / refundRecordId 的 fallback。
     */
    private ResultExtras extractFromResult(Object result,
                                           String paymentIdFromResult,
                                           String refundRecordIdFromResult) {
        ResultExtras extras = new ResultExtras();
        if (result == null) return extras;

        if (StringUtils.hasText(paymentIdFromResult)) {
            extras.paymentId = extractFieldFromResult(result, paymentIdFromResult);
        }
        if (StringUtils.hasText(refundRecordIdFromResult)) {
            String idStr = extractFieldFromResult(result, refundRecordIdFromResult);
            extras.refundRecordId = parseLongOrNull(idStr);
        }
        return extras;
    }

    private static class ResultExtras {
        String paymentId;
        Long refundRecordId;
    }

    /**
     * 从对象按字段名取值（支持 a.b.c 嵌套）
     */
    private String extractFieldFromResult(Object result, String fieldName) {
        if (result == null || !StringUtils.hasText(fieldName)) return null;
        try {
            Object current = result;
            for (String part : fieldName.split("\\.")) {
                if (current == null) return null;
                java.lang.reflect.Field field = current.getClass().getDeclaredField(part);
                field.setAccessible(true);
                current = field.get(current);
            }
            return current != null ? current.toString() : null;
        } catch (Exception e) {
            log.warn("从返回值提取字段 {} 失败", fieldName, e);
            return null;
        }
    }

    private Long parseLongOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Long) return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
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
            if (paramNames == null || args == null || args.length == 0) return "{}";
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

    private String extractRequestBody(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) return "{}";
            if (args.length == 1) {
                return objectMapper.writeValueAsString(maskSensitiveValue(args[0]));
            }
            return objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            log.warn("提取请求体失败", e);
            return "{}";
        }
    }

    private String extractResponseBody(Object result) {
        if (result == null) return "null";
        try {
            return objectMapper.writeValueAsString(maskSensitiveValue(result));
        } catch (Exception e) {
            log.warn("提取响应体失败", e);
            return result.toString();
        }
    }

    private Object maskSensitiveValue(Object value) {
        if (value == null) return null;
        if (value instanceof String str) {
            if (str.matches("\\\\d{15,20}")) {
                return str.substring(0, 4) + "****" + str.substring(str.length() - 4);
            }
            return str;
        }
        if (value instanceof PaymentOrder order) {
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("paymentId", maskSensitiveValue(order.getPaymentId()));
            safe.put("orderId", order.getOrderId());
            safe.put("status", order.getStatus());
            safe.put("amount", order.getAmount());
            return safe;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                safe.put(String.valueOf(entry.getKey()), maskSensitiveValue(entry.getValue()));
            }
            return safe;
        }
        if (value instanceof Collection<?> coll) {
            List<Object> safe = new ArrayList<>();
            for (Object item : coll) safe.add(maskSensitiveValue(item));
            return safe;
        }
        return value;
    }

    private String findPaymentIdFromArgs(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String str) {
                if (str.matches("\\\\d+") && str.length() > 10) return str;
            }
        }
        return null;
    }

    private Long findRecordIdFromArgs(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long l) return l;
        }
        return null;
    }

    private State queryState(AuditTarget target) {
        if (target.getType() == AuditTargetType.PAYMENT_ORDER) {
            if (target.getPaymentId() == null) {
                return new State(null, null, null);      // ← 新增：无 paymentId 不查库
            }
            PaymentOrder order = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getPaymentId, target.getPaymentId()));
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

        if (target.getType() == AuditTargetType.ORDER && target.getOrderId() != null) {
            PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getOrderId, Long.parseLong(target.getOrderId()))
                            .orderByDesc(PaymentOrder::getCreatedAt)
                            .last("LIMIT 1"));
            if (paymentOrder != null) {
                return new State(paymentOrder.getStatus(), paymentOrder.getPaymentId(), target.getOrderId());
            }
            return new State(null, null, target.getOrderId());
        }
        return null;
    }

    private void recordAuditLog(AuditTarget target, State before, State after,
                                AuditLog auditLog, long costMs,
                                String requestParams, String requestBody, String responseBody,
                                String errorCode, String errorMsg,ResultExtras extras) {
        log.info("[AUDIT-DEBUG] recordAuditLog: target={}, target.paymentId={}, target.recordId={}, extras.paymentId={}, extras.refundRecordId={}",
                target, target.getPaymentId(), target.getRecordId(), extras.paymentId, extras.refundRecordId);
        String beforeStatus = before != null ? before.getStatus() : null;
        String afterStatus = after != null ? after.getStatus() : null;

        log.info("审计状态变化: target={}, beforeStatus={}, afterStatus={}",
                target, beforeStatus, afterStatus);

        String inferredOperation = stateMapper.inferOperation(beforeStatus, afterStatus);

        String operation;
        if (auditLog.operation() != AuditOperation.UNKNOWN) {
            operation = auditLog.operation().getCode();
        } else if (inferredOperation != null) {
            operation = inferredOperation;
        } else {
            log.warn("无法推断操作类型，且注解未指定: target={}, beforeStatus={}, afterStatus={}",
                    target, beforeStatus, afterStatus);
            operation = "UNKNOWN";
        }

        String paymentId = target.getPaymentId() != null ? target.getPaymentId()
                : (after != null ? after.getPaymentId() : null);
        if (paymentId == null) {
            paymentId = extras.paymentId;          // ← fallback
        }

        Long refundRecordId = target.getRecordId() != null ? target.getRecordId()
                : extras.refundRecordId;            // ← fallback

        String description = auditLog.desc();
        if (!StringUtils.hasText(description)) {
            description = "审计日志: " + operation;
        }

        String clientIp = TraceContext.getClientIp();
        String userAgent = TraceContext.getUserAgent();
        String userId = TraceContext.getUserId();
        if (userId == null) {
            log.debug("记录审计日志失败，TraceContext.getUserId()=null");
            return;
        }
        Long userIdLong;
        try {
            userIdLong = Long.valueOf(userId);
        } catch (NumberFormatException e) {
            log.debug("记录审计日志失败，userId 非数字: {}", userId);
            return;
        }

        auditService.builder()
                .traceId(TraceContext.generateTraceId())
                .clientIp(clientIp)
                .userAgent(userAgent)
                .userId(userIdLong)
                .operatorType(userIdLong.equals(0L) ? OperatorType.SYSTEM.getCode() : OperatorType.USER.getCode())
                .paymentId(paymentId)
                .refundRecordId(refundRecordId)
                .orderId(getOrderIdFromPaymentId(paymentId))
                .operation(operation)
                .operationDesc(description)
                .beforeStatus(beforeStatus)
                .afterStatus(afterStatus)
                .result(AuditResult.SUCCESS.getCode())
                .costMs(costMs)
                .requestParams(requestParams)
                .requestBody(requestBody)
                .responseBody(responseBody)
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .log();
    }

    private Long getOrderIdFromPaymentId(String paymentId) {
        if (paymentId == null) return null;
        try {
            PaymentOrder order = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getPaymentId, paymentId)
                            .select(PaymentOrder::getOrderId));
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
            if (type == AuditTargetType.PAYMENT_ORDER && paymentId != null) return type.name() + ":" + paymentId;
            if (type == AuditTargetType.REFUND_RECORD && recordId != null) return type.name() + ":" + recordId;
            if (type == AuditTargetType.ORDER && orderId != null) return type.name() + ":" + orderId;
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
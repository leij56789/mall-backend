package com.mall.pay.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * AOP 参数解析工具（支持对象字段提取）
 */
@Slf4j
public class ParamExtractor {

    /**
     * 提取单个参数值，支持点号表达式（如 "payment.paymentId"）
     *
     * @param joinPoint       切点
     * @param paramExpression 参数表达式（参数名 或 对象名.字段名）
     * @return 参数值，如果提取失败返回 null
     */
    public static Object extractParamValue(ProceedingJoinPoint joinPoint, String paramExpression) {
        if (!StringUtils.hasText(paramExpression)) {
            return null;
        }

        // 如果包含点号，表示从对象中取字段
        if (paramExpression.contains(".")) {
            String[] parts = paramExpression.split("\\.");
            if (parts.length != 2) {
                log.warn("不支持的表达式格式: {}", paramExpression);
                return null;
            }
            String objectParamName = parts[0];
            String fieldName = parts[1];

            // 获取对象参数值
            Object obj = getParamValue(joinPoint, objectParamName);
            if (obj == null) {
                log.debug("未找到对象参数: {}", objectParamName);
                return null;
            }

            // 使用反射获取字段值
            return getFieldValue(obj, fieldName);
        } else {
            // 直接获取参数值
            return getParamValue(joinPoint, paramExpression);
        }
    }

    /**
     * 从方法参数中根据参数名获取值
     */
    private static Object getParamValue(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames == null) {
            return null;
        }

        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(paramName)) {
                return args[i];
            }
        }
        return null;
    }

    /**
     * 通过反射获取对象指定字段的值
     */
    private static Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException e) {
            log.warn("对象 {} 中不存在字段: {}", obj.getClass().getSimpleName(), fieldName);
        } catch (IllegalAccessException e) {
            log.warn("无法访问对象 {} 的字段: {}", obj.getClass().getSimpleName(), fieldName);
        }
        return null;
    }

    // 原有的 extractParams 方法保持不变，但可以简化
    public static Map<String, Object> extractParams(ProceedingJoinPoint joinPoint, String... paramNames) {
        Map<String, Object> result = new HashMap<>();
        if (paramNames == null || paramNames.length == 0) {
            return result;
        }
        for (String paramName : paramNames) {
            Object value = extractParamValue(joinPoint, paramName);
            if (value != null) {
                result.put(paramName, value);
            }
        }
        return result;
    }
}
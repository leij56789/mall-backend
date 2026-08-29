package com.mall.pay.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * AOP 参数解析工具
 * <p>
 * 根据注解中指定的参数名，从方法参数中提取对应的值
 */
@Slf4j
public class ParamExtractor {

    /**
     * 从方法参数中提取指定名称的参数值
     *
     * @param joinPoint  切点
     * @param paramNames 要提取的参数名列表（如 "paymentId", "recordId"）
     * @return 参数名 → 参数值的映射
     */
    public static Map<String, Object> extractParams(ProceedingJoinPoint joinPoint, String... paramNames) {
        Map<String, Object> result = new HashMap<>();

        if (paramNames == null || paramNames.length == 0) {
            return result;
        }

        // 1. 获取方法参数名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames == null) {
            log.warn("无法获取方法参数名: {}", method.getName());
            // 尝试通过参数类型匹配
            return extractByType(joinPoint, paramNames);
        }

        // 2. 按名称匹配
        for (String paramName : paramNames) {
            for (int i = 0; i < parameterNames.length; i++) {
                if (parameterNames[i].equals(paramName)) {
                    result.put(paramName, args[i]);
                    break;
                }
            }
        }

        return result;
    }

    /**
     * 当无法获取参数名时，通过类型匹配（兜底方案）
     */
    private static Map<String, Object> extractByType(ProceedingJoinPoint joinPoint, String[] paramNames) {
        Map<String, Object> result = new HashMap<>();
        Object[] args = joinPoint.getArgs();

        for (String paramName : paramNames) {
            for (Object arg : args) {
                if ("paymentId".equals(paramName) && arg instanceof String) {
                    String str = (String) arg;
                    if (str.matches("\\\\\\\\\\\\\\\\d+") && str.length() > 10) {
                        result.put(paramName, arg);
                        break;
                    }
                }
                if ("refundRecordId".equals(paramName) && arg instanceof Long) {
                    result.put(paramName, arg);
                    break;
                }
                if ("recordId".equals(paramName) && arg instanceof Long) {
                    result.put(paramName, arg);
                    break;
                }
            }
        }

        return result;
    }
}
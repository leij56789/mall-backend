package com.mall.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 异常堆栈工具类
 * 用于从异常堆栈中提取业务代码位置
 */
@Slf4j
public class ExceptionUtils {

    /**
     * 默认业务包名（可配置）
     */
    private static final String DEFAULT_BASE_PACKAGE = "com.mall";

    /**
     * 从异常堆栈中获取第一个业务代码位置
     */
    public static String getBusinessLocation(Throwable e) {
        return getBusinessLocation(e, DEFAULT_BASE_PACKAGE);
    }

    /**
     * 从异常堆栈中获取第一个业务代码位置（自定义包名）
     */
    public static String getBusinessLocation(Throwable e, String basePackage) {
        if (e == null) {
            return "异常对象为空";
        }
        return Arrays.stream(e.getStackTrace())
                .filter(element -> element.getClassName().startsWith(basePackage))
                .map(element -> String.format("%s.%s(%s:%d)",
                        element.getClassName(),
                        element.getMethodName(),
                        element.getFileName(),
                        element.getLineNumber()))
                .findFirst()
                .orElse("无法定位业务代码位置");
    }

    /**
     * 获取完整调用栈（仅业务代码）
     */
    public static List<String> getBusinessStackTrace(Throwable e) {
        return getBusinessStackTrace(e, DEFAULT_BASE_PACKAGE);
    }

    /**
     * 获取完整调用栈（自定义包名）
     */
    public static List<String> getBusinessStackTrace(Throwable e, String basePackage) {
        if (e == null) {
            return List.of("异常对象为空");
        }
        return Arrays.stream(e.getStackTrace())
                .filter(element -> element.getClassName().startsWith(basePackage))
                .map(element -> String.format("  at %s.%s(%s:%d)",
                        element.getClassName(),
                        element.getMethodName(),
                        element.getFileName(),
                        element.getLineNumber()))
                .collect(Collectors.toList());
    }

    /**
     * 获取简短异常信息（含业务代码位置和异常消息）
     */
    public static String getShortExceptionInfo(Throwable e) {
        if (e == null) {
            return "异常对象为空";
        }
        String location = getBusinessLocation(e);
        return String.format("[%s] %s -> %s",
                e.getClass().getSimpleName(),
                e.getMessage() != null ? e.getMessage() : "无异常消息",
                location);
    }

    /**
     * 打印异常摘要到日志
     * 用法：ExceptionUtils.logException(log, e);
     */
    public static void logException(org.slf4j.Logger log, Throwable e) {
        if (e == null) {
            log.warn("异常对象为空");
            return;
        }

        log.error("========== 异常摘要 ==========");
        log.error("异常类型: {}", e.getClass().getName());
        log.error("异常消息: {}", e.getMessage() != null ? e.getMessage() : "无");
        log.error("业务代码位置: {}", getBusinessLocation(e));
        log.error("业务调用栈:");
        getBusinessStackTrace(e).forEach(log::error);
        log.error("==============================");
    }

    /**
     * 打印异常摘要到日志（包含完整堆栈）
     */
    public static void logExceptionWithFullStack(org.slf4j.Logger log, Throwable e) {
        if (e == null) {
            log.warn("异常对象为空");
            return;
        }
        log.error("========== 异常摘要 ==========");
        log.error("异常类型: {}", e.getClass().getName());
        log.error("异常消息: {}", e.getMessage() != null ? e.getMessage() : "无");
        log.error("业务代码位置: {}", getBusinessLocation(e));
        log.error("业务调用栈:");
        getBusinessStackTrace(e).forEach(log::error);
        log.error("==============================");
        log.error("完整堆栈:", e);  // 打印完整堆栈
    }
}
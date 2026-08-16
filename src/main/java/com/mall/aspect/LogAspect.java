package com.mall.aspect;


import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.trace.constant.TraceConstants;
import com.mall.common.trace.context.TraceContext;
import com.mall.common.trace.utils.CallSeqContext;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
/**
 * @author jiaolei
 * @date 2026/6/5 12:52
 * @description
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+1)
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${call.seq.enabled}")
    private boolean callSeqEnabled;

    @PostConstruct
    public void init(){
        CallSeqContext.setEnabled(callSeqEnabled);
        System.out.println("==LogAspect切面已加载");
    }
    /**
     * 切点：所有带有 @Log 注解的方法
     */
    @Pointcut("@annotation(com.mall.annotation.Log)")
    public void logPointcut() {}

    /**
     * 环绕通知：在方法执行前后记录日志
     */
    @Around("logPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 获取方法签名（短格式，如 "SeckillProducer.sendSeckillMessage()"）
        String methodName = joinPoint.getSignature().toShortString();

        // 2. 设置到 CallSeqContext（供异步任务捕获）
        CallSeqContext.setCurrentMethodName(methodName);
        // 1. 进入方法，生成编号并放入 MDC
        String callSeq = CallSeqContext.enter();

        // 2. 记录入口日志（可选，仅当 callSeq 不为 null 时额外打印）
        if (callSeq != null) {
            log.debug("[{}] 进入 {}", callSeq, joinPoint.getSignature().toShortString());
        }

        //以上为CallSeq代码
        String traceId = TraceContext.getTraceId();
        if(StrUtil.isBlank(traceId)){
            TraceContext.initFromRequest(null, TraceConstants.SYSTEM_USER,null,null);
        }
        //以上为trace代码
        // 1. 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);

        //树形日志


        // 2. 获取请求信息
        HttpServletRequest request = getHttpServletRequest();

        // 3. 构建日志信息
        Map<String, Object> logInfo = new HashMap<>();
        logInfo.put("时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        logInfo.put("操作", logAnnotation.value());
        logInfo.put("方法", method.getDeclaringClass().getSimpleName() + "." + method.getName());

        if (request != null) {
            logInfo.put("URL", request.getRequestURL().toString());
            logInfo.put("IP", getClientIp(request));
        }

        // 4. 记录请求参数（可选）
        if (logAnnotation.recordParams()) {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                try {
                    logInfo.put("请求参数", objectMapper.writeValueAsString(args));
                } catch (Exception e) {
                    logInfo.put("请求参数", "参数序列化失败");
                }
            }
        }

        // 5. 记录开始时间
        long startTime = System.currentTimeMillis();
        // 1. 获取业务类名（去掉包名，取简单类名）
        String className = joinPoint.getTarget().getClass().getSimpleName();
        // 如果是代理类（CGLIB 会带 $$），需要提取原始类名
        if (className.contains("$$")) {
            className = className.substring(0, className.indexOf("$$"));
        }
        // 2. 构建业务方法标识（用于日志消息）
        String bizLocation = String.format("(%s.java:%d)", className,1); // 例如: (SeckillService.java:1)
        log.info("【请求开始】 {}{}", objectMapper.writeValueAsString(logInfo),bizLocation);

        // 6. 执行目标方法
        Object result = null;
        try {
            result = joinPoint.proceed();

            // 7. 记录响应结果
            long elapsedTime = System.currentTimeMillis() - startTime;
            Map<String, Object> responseInfo = new HashMap<>();
            responseInfo.put("操作", logAnnotation.value());
            responseInfo.put("耗时", elapsedTime + "ms");
            if (logAnnotation.recordReturnValue()) {
                try {
                    responseInfo.put("返回结果", objectMapper.writeValueAsString(result));
                } catch (Exception e) {
                    responseInfo.put("返回结果", "结果序列化失败");
                }
            }

//            line = getTargetMethodLine(joinPoint);
            // 2. 构建业务方法标识（用于日志消息）
            bizLocation = String.format("(%s.java:%d)", className,1); // 例如: (SeckillService.java:1)

            log.info("【请求结束】 {}{}", objectMapper.writeValueAsString(responseInfo),bizLocation);

            return result;

        } catch (Exception e) {
            // 8. 记录异常信息
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.error("【请求异常】 操作: {}, 耗时: {}ms, 异常: {}",
                    logAnnotation.value(), elapsedTime, e.getMessage(), e);
            throw e;
        }finally {
            long cost = System.currentTimeMillis() - startTime;
            if (callSeq != null) {
                log.debug("[{}] 退出 {} | 耗时={}ms", callSeq, joinPoint.getSignature().toShortString(), cost);
            }
            // 3. 退出方法，弹出栈并恢复父级编号
            CallSeqContext.exit();
        }
    }

    @Async("taskExecutor")  // 添加这个注解，异步执行
    @AfterReturning("@annotation(log)")
    public void afterReturning(JoinPoint point, Log log) {
        // 原有的保存日志逻辑
        System.out.println("异步保存日志: " + log.value());
        // 实际项目中这里会保存到数据库
    }
    /**
     * 获取 HttpServletRequest
     */
    private HttpServletRequest getHttpServletRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest();
            }
        } catch (Exception e) {
            // 非 Web 环境
        }
        return null;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
//    public static int getTargetMethodLine(ProceedingJoinPoint pjp) {
//        String methodName = pjp.getSignature().getName();
//        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
//        for (StackTraceElement e : stack) {
//            if (e.getClassName().startsWith("com.mall")
//                    && e.getMethodName().equals(methodName)) {
//                return e.getLineNumber();
//            }
//        }
//        return 1;
//    }
}
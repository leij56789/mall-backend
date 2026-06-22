package com.mall.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.mall.annotation.RateLimit;
import com.mall.dto.response.Response;
import jakarta.annotation.PostConstruct;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class RateLimitAspect {

    private final ConcurrentHashMap<String, RateLimiter> limiterMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger log=LoggerFactory.getLogger(RateLimitAspect.class);
    @PostConstruct
    public void init(){
        log.info("RateLimitAspect切面已加载");
    }

    @Pointcut("@annotation(com.mall.annotation.RateLimit)")
    public void rateLimitPointcut() {}

    @Around("rateLimitPointcut() && @annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        log.info("进入限流切面，key={}",rateLimit.key());
        // 生成限流 key
        String key = rateLimit.key();
        if (key.isEmpty()) {
            key = joinPoint.getSignature().toShortString();
        }

        // 获取或创建 RateLimiter
        RateLimiter rateLimiter = limiterMap.computeIfAbsent(key,
                k -> RateLimiter.create(rateLimit.permitsPerSecond()));

        // 尝试获取令牌
        boolean acquired;
        if (rateLimit.timeout() > 0) {
            acquired = rateLimiter.tryAcquire(rateLimit.timeout(), TimeUnit.SECONDS);
        } else {
            acquired = rateLimiter.tryAcquire();
        }

        if (!acquired) {
            // 限流触发，直接返回 Result 对象（不要通过 response 写回）
//            return Result.error(429, "请求过于频繁，请稍后再试");
            return new Response();
        }

        // 放行
        return joinPoint.proceed();
    }
}
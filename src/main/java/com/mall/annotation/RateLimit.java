package com.mall.annotation;
import java.lang.annotation.*;
/**
 * @author jiaolei
 * @date 2026-06-05 19:03
 * @description 限流注解
 * 加在 Controller 方法上，限制接口访问频率
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key（可选，用于区分不同接口）
     */
    String key() default "";

    /**
     * 每秒允许的请求数（默认 2 次/秒）
     */
    double permitsPerSecond() default 2.0;

    /**
     * 超时时间（秒），超过等待时间则直接拒绝
     */
    int timeout() default 0;
}
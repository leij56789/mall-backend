package com.mall.annotation;

import java.lang.annotation.*;
/**
 * @author jiaolei
 * @date 2026/6/5 13:19
 * @description 
 */

/**
 * 自定义日志注解
 * 在需要记录日志的方法上加上 @Log 即可
 */
@Target(ElementType.METHOD)  // 作用在方法上
@Retention(RetentionPolicy.RUNTIME)  // 运行时保留
@Documented
public @interface Log {

    /**
     * 操作描述
     */
    String value() default "";

    /**
     * 是否记录返回值（默认 true）
     */
    boolean recordReturnValue() default true;

    /**
     * 是否记录参数（默认 true）
     */
    boolean recordParams() default true;
}
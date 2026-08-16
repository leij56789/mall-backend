package com.mall.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    // 其他异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        // 1. 打印完整的堆栈（以备不时之需）
        log.error("系统异常，请求路径: {}", request.getRequestURI(), e);

        // 2. 【核心】从堆栈中过滤出你自己写的业务代码位置
        String businessLocation = Arrays.stream(e.getStackTrace())
                                        .filter(element -> element.getClassName().startsWith("com.mall")) // 替换成你的根包名
                                        .map(element -> String.format("%s.%s(%s:%d)",
                                                element.getClassName(),
                                                element.getMethodName(),
                                                element.getFileName(),
                                                element.getLineNumber()))
                                        .findFirst()
                                        .orElse("无法定位业务代码位置");

        // 3. 把这个位置单独打印一行，让你一眼看到！
        log.error("🔥 业务代码调用位置: {}", businessLocation);
        return Result.error(500, "服务器繁忙，请稍后再试");
    }
}
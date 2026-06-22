package com.mall.common;

import cn.hutool.db.PageResult;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private Integer code;       // 业务状态码：200成功，其他失败
    private String message;     // 提示信息
    private T data;             // 返回数据
    private Long timestamp;     // 时间戳

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    // ========== 成功 ==========
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    // ========== 失败 ==========
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    // ========== 分页 ==========
    public static <T> Result<PageResult<T>> page(PageResult<T> pageResult) {
        Result<PageResult<T>> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(pageResult);
        return result;
    }

    // ========== 链式调用 ==========
    public Result<T> withData(T data) {
        this.data = data;
        return this;
    }
}
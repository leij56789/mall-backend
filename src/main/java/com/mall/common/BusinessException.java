package com.mall.common;

import com.mall.enums.ResultCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {
    private Integer code;
    private String message;
    private ResultCode resultCode;  // 保留原始枚举，便于上层精确判断

    // 1. 仅消息
    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.message = message;
        this.resultCode = null;  // 无枚举对应
    }

    // 2. 自定义 code + message
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.resultCode = null;
    }

    // 3. 只传 ResultCode（推荐）
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
        this.resultCode = resultCode;   // 关键修复：保存枚举引用
    }

    // 4. ResultCode + 自定义消息（覆盖默认消息）
    public BusinessException(ResultCode resultCode, String message) {
        super(message);  // 使用自定义消息作为异常消息
        this.code = resultCode.getCode();
        this.message = message;
        this.resultCode = resultCode;   // 关键修复：保存枚举引用
    }

//    /**
//     * 获取 ResultCode（供上层决策使用）
//     */
//    public ResultCode getResultCode() {
//        return resultCode;   // 修复：返回实际保存的对象
//    }

    // 可选：为了兼容旧逻辑，保留 getCode() 和 getMessage()（由 @Data 自动生成）
}
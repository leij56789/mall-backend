package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.transaction.annotation.Transactional;

@Getter
@AllArgsConstructor
public enum PaymentStatus {

    /**
     * 初始状态
     * 支付单刚创建，尚未调用第三方接口，或正在调用中
     * 该状态不占用任何第三方资源，允许被重试覆盖
     */
    INIT("INIT", "初始"),

    /**
     * 待支付
     * 已成功调用第三方统一下单，获取到 prepay_id，等待用户支付
     * 该状态占用第三方资源，不可重试，需走超时取消逻辑
     */
    WAITING("WAITING", "待支付"),

    /**
     * 支付成功
     * 第三方回调确认支付成功，资金已到账
     */
    SUCCESS("SUCCESS", "支付成功"),

    /**
     * 支付失败
     * 第三方明确返回业务失败（如余额不足、风控拦截等）
     * 该状态不占用资源，允许用户重试
     */
    FAILED("FAILED", "支付失败"),

    /**
     * 待确认（不确定状态）
     * 第三方调用超时、响应异常、解析失败等，无法确定支付结果
     * 需由后台补偿任务主动查询第三方订单状态，最终转为 SUCCESS 或 FAILED
     */
    PENDING_CONFIRM("PENDING_CONFIRM", "待确认"),

    /**
     * 已关闭（超时）
     * 支付单超过有效期（15分钟）未被支付，被系统自动关闭
     */
    CLOSED("CLOSED", "已关闭"),

    /**
     * 已退款
     * 支付成功后，因用户取消或其它原因发起的退款
     */
    REFUND("REFUND", "已退款"),
    PARTIAL_REFUNDED("PARTIAL_REFUNDED", "部分退款");  // 新增：部分退款
    private final String code;
    private final String desc;
    public static String getDescByCode(String code) {
        for (PaymentStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status.getDesc();
            }
        }
        return "未知状态";
    }

    public static PaymentStatus fromCode(String code) {
        for (PaymentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否可以退款
     */
    public boolean canRefund() {
        return this == SUCCESS || this == PARTIAL_REFUNDED;
    }

}
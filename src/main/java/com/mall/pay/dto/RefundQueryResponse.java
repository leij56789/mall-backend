package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 退款查询响应（渠道无关）
 * <p>
 * 业务层通过 success / processing / failReason 判断退款状态，
 * 不感知支付宝的 refund_status 等渠道特有字段。
 */
@Data
@Builder
public class RefundQueryResponse {

    /**
     * 是否退款成功
     * <p>
     * true：退款已成功（对应支付宝 refund_status = REFUND_SUCCESS）
     * false：退款未成功或状态未知
     */
    private boolean success;

    /**
     * 是否处理中
     * <p>
     * true：退款请求已受理，但尚未完成，需要继续查询
     * false：已终态（成功或明确失败）
     */
    private boolean processing;

    /**
     * 商户订单号
     */
    private String outTradeNo;

    /**
     * 支付宝交易号
     */
    private String tradeNo;

    /**
     * 退款金额（单位：元）
     * <p>
     * 本次退款请求对应的退款金额。
     */
    private String refundAmount;

    /**
     * 退款时间（格式：yyyy-MM-dd HH:mm:ss）
     * <p>
     * 退款执行成功的时间，仅在 success=true 时有值。
     */
    private String gmtRefundPay;

    /**
     * 失败原因（仅在 success=false 时有值）
     * <p>
     * 包含：处理中原因、查询失败原因、业务错误原因等。
     */
    private String failReason;

    /**
     * 快捷判断：退款是否失败（终态且失败）
     */
    public boolean isFailed() {
        return !success && !processing;
    }

    /**
     * 快捷判断：是否为终态（成功或失败）
     */
    public boolean isFinal() {
        return success || !processing;
    }
}
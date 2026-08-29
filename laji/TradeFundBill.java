package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 交易使用的资金渠道明细
 * <p>
 * 对应支付宝响应中的 refund_detail_item_list
 */
@Data
@Builder
public class TradeFundBill {
    /**
     * 资金渠道
     * ALIPAYACCOUNT：支付宝余额
     * BANKCARD：银行卡
     * POINT：积分
     * 等其他值
     */
    private String fundChannel;

    /**
     * 支付金额（元）
     */
    private String amount;

    /**
     * 实际支付金额（元）
     */
    private String realAmount;

    /**
     * 资金类型
     * DEBIT_CARD：借记卡
     * CREDIT_CARD：信用卡
     */
    private String fundType;
}
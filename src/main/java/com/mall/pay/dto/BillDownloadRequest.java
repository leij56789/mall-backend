package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 对账单查询请求
 */
@Data
@Builder
public class BillDownloadRequest {
    /**
     * 账单类型（必填）
     * trade：商户基于支付宝交易收单的业务账单
     * signcustomer：基于商户支付宝余额收入及支出等资金变动的账务账单
     * merchant_act：营销活动账单
     */
    private String billType;

    /**
     * 账单日期（必填）
     * 日账单：yyyy-MM-dd，最早可下载近6年，不支持当日，T+1生成
     * 月账单：yyyy-MM，最早可下载近6年，不支持当月，次月3日生成
     */
    private String billDate;

    /**
     * 二级商户smid（可选，仅 bill_type=trade_zft_merchant 时使用）
     */
    private String smid;

    /**
     * 是否使用安全链接（可选，true 使用 https）
     */
    private Boolean secure;
}
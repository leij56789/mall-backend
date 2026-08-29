package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 对账单查询响应
 */
@Data
@Builder
public class BillDownloadResponse {
    /**
     * 账单下载地址（有效期30秒）
     */
    private String billDownloadUrl;

    /**
     * 账单文件状态说明
     * EMPTY_DATA_WITH_BILL_FILE：当天无账单业务数据但可获取空数据文件
     */
    private String billFileCode;

    /**
     * 支付宝交易号（预留）
     */
    private String tradeNo;

    /**
     * 商户订单号（预留）
     */
    private String outTradeNo;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 失败原因
     */
    private String failReason;
}
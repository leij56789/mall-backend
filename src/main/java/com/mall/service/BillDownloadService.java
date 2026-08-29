package com.mall.service;

import com.mall.pay.dto.BillDownloadRequest;
import com.mall.pay.dto.BillDownloadResponse;

/**
 * 对账单下载服务
 */
public interface BillDownloadService {

    /**
     * 查询对账单下载地址
     */
    BillDownloadResponse getBillDownloadUrl(BillDownloadRequest request);

    /**
     * 获取昨日交易对账单下载地址
     */
    BillDownloadResponse getYesterdayTradeBill();
}
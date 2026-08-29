package com.mall.pay.client;

import com.mall.pay.dto.BillDownloadRequest;
import com.mall.pay.dto.BillDownloadResponse;

/**
 * 对账单查询客户端
 * <p>
 * 对应接口：alipay.data.dataservice.bill.downloadurl.query（查询对账单下载地址）
 */
public interface BillDownloadClient {

    /**
     * 查询对账单下载地址
     *
     * @param request 查询请求
     * @return 对账单响应（包含下载地址）
     */
    BillDownloadResponse queryBillDownloadUrl(BillDownloadRequest request);
}
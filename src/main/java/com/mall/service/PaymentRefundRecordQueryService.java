package com.mall.service;

import com.mall.entity.PaymentRefundRecord;
import com.mall.pay.dto.RefundRecordQueryRequest;
import com.mall.pay.dto.RefundRecordQueryResponse;

import java.util.List;

/**
 * 退款记录查询服务
 */
public interface PaymentRefundRecordQueryService {

    /**
     * 分页查询退款记录
     */
    RefundRecordQueryResponse queryRefundRecords(RefundRecordQueryRequest request);

    /**
     * 根据支付单号查询所有退款记录
     */
    List<RefundRecordQueryResponse.RefundRecordInfo> queryByPaymentId(String paymentId);

    /**
     * 根据退款请求号查询退款记录
     */
    RefundRecordQueryResponse.RefundRecordInfo queryByOutRequestNo(String outRequestNo);

    /**
     * 查询进行中的退款记录（用于补偿任务）
     */
    List<PaymentRefundRecord> queryProcessingTimeout(int limit);
}
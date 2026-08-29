package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.PaymentRefundRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRefundRecordService extends IService<PaymentRefundRecord> {

    /**
     * 创建退款记录（状态为 PROCESSING）
     */
    PaymentRefundRecord createRefundRecord(String paymentId, String outTradeNo,
                                            String tradeNo, BigDecimal refundAmount,
                                            String refundReason, String outRequestNo);

    /**
     * 根据退款请求号查询
     */
    PaymentRefundRecord getByOutRequestNo(String outRequestNo);

    /**
     * 查询超时未完成的退款记录
     */
    List<PaymentRefundRecord> getProcessingTimeout(int limit);

    /**
     * 根据支付单号查询所有退款记录
     */
    List<PaymentRefundRecord> getByPaymentId(String paymentId);

    /**
     * 更新退款为成功
     */
    void markSuccess(Long recordId, String thirdPartyRefundNo);

    /**
     * 更新退款为失败
     */
    void markFailed(Long recordId, String failReason);

    /**
     * 更新下次查询时间（延迟重试）
     */
    void updateNextQueryTime(Long recordId, Integer oldRetryCount, LocalDateTime nextQueryTime);

    /**
     * 查询指定支付单的累计退款金额
     */
    BigDecimal getTotalRefundAmount(String paymentId);

    /**
     * 校验是否可以发起退款（累计退款金额 < 订单金额）
     */
    boolean canRefund(String paymentId, BigDecimal refundAmount, BigDecimal orderTotalAmount);
}
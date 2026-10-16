package com.mall.pay.facade;

import com.mall.pay.facade.dto.*;

import java.util.List;

/**
 * 支付门面 —— 对外统一入口。
 * <p>
 * 业务侧只依赖本接口，不感知支付模块内部实现（表结构、实体、Mapper）。
 * <p>
 * 所有异常统一抛出 {@link com.mall.common.BusinessException}，携带 ResultCode。
 */
public interface PayFacade {

    // ==================== 支付 ====================

    /**
     * 创建支付单。
     * <p>
     * 幂等性：同一 (bizType, bizId) 若存在未完成的支付单，复用并重新生成凭证。
     *
     * @param dto 支付入参
     * @return 支付结果（含 payUrl / prepayId）
     */
    PayResult pay(PayOrderDTO dto);

    /**
     * 查询支付单状态。
     */
    PayQueryResult query(String paymentId);

    /**
     * 关闭支付单（用户取消 / 超时）。
     */
    void close(String paymentId);

    // ==================== 退款 ====================

    /**
     * 发起退款。
     * <p>
     * 幂等性：同一 outRequestNo 只处理一次。
     *
     * @param dto 退款入参
     * @return 退款结果（status=PROCESSING 表示已受理，等待异步通知）
     */
    RefundResult refund(RefundDTO dto);

    /**
     * 查询单笔退款状态。
     */
    RefundResult queryRefund(String outRequestNo);

    /**
     * 查询某支付单的所有退款记录。
     */
    List<RefundResult> listRefunds(String paymentId);
}
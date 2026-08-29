package com.mall.pay.client;

import com.alipay.api.domain.OpenApiRoyaltyDetailInfoPojo;
import com.alipay.api.domain.RefundGoodsDetail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mall.common.BusinessException;
import com.mall.pay.dto.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public interface PayClient {
    ThirdPartyPayResponse unifiedOrder(ThirdPartyPayRequest request);
    QueryOrderResponse queryOrder(QueryOrderRequest request);
    String mapTradeStatusAfterQueryOrderOnPaymentCompensateJob(String alipayStatus);
    String mapTradeStatusAfterQueryOrderOnAsyncQueryService(String alipayStatus);
//    String mapTradeStatusAfterUnifiedOrder(String alipayStatus);
    // AlipayF2FPayClientAdapter
    boolean canRecoverFromPendingConfirm();
    /**
     * 关闭支付订单（主动撤销/关单）
     *
     * @param paymentId 商户订单号（即 out_trade_no）
     * @return true 表示关单成功（或订单已关闭/不存在），false 表示关单失败（需重试或告警）
     * @throws BusinessException 当关单接口调用异常时抛出（如网络超时）
     */

    boolean closeOrder(String paymentId);
    // AlipayWapPayClientAdapter
    default boolean canRecreatePaymentForm(){
        return false;
    }
    // ===== 退款相关（内部类） =====

    /**
     * 统一收单交易退款
     * <p>
     * 注意：
     * 1. 接口返回 code=10000 只代表退款请求成功，不代表退款成功
     * 2. 需通过 fundChange=Y 判断退款是否真正成功
     * 3. 分多次退款时，outRequestNo 必须唯一且不变
     * 4. 同一笔交易累计退款金额不能超过原始交易总金额
     *
     * @param request 退款请求参数
     * @return 退款响应
     * @throws BusinessException 当接口调用失败时抛出
     */
    RefundResponse refundOrder(RefundRequest request);

    /**
     * 退款结果枚举（渠道无关）
     */
    enum RefundResult {
        SUCCESS,     // 退款成功
        PROCESSING,  // 不确定，需查询确认
        FAILED       // 明确失败
    }

    /**
     * 退款资金渠道明细
     */
    @Data
    @Builder
    class TradeFundBill {
        private String fundChannel;
        private String amount;
        private String realAmount;
        private String fundType;
    }

    /**
     * 退款请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    class RefundRequest {
        private String outTradeNo;
        private String tradeNo;
        private String refundAmount;
        private String refundReason;
        private String outRequestNo;
        private List<RefundGoodsDetail> refundGoodsDetail;
        private List<OpenApiRoyaltyDetailInfoPojo> refundRoyaltyParameters;
        private List<String> queryOptions;
    }

    /**
     * 退款响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    class RefundResponse {
        private String outTradeNo;
        private String tradeNo;
        private String refundAmount;
        private RefundResult result;
        private String failReason;
        private List<TradeFundBill> refundDetailItemList;
    }




}

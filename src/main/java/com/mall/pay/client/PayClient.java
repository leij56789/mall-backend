package com.mall.pay.client;

import com.mall.common.BusinessException;
import com.mall.pay.dto.QueryOrderRequest;
import com.mall.pay.dto.QueryOrderResponse;
import com.mall.pay.dto.ThirdPartyPayRequest;
import com.mall.pay.dto.ThirdPartyPayResponse;

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
}

package com.mall.pay.client;

import com.mall.pay.dto.RefundQueryRequest;
import com.mall.pay.dto.RefundQueryResponse;

import java.util.Collections;
import java.util.List;

/**
 * 退款查询客户端（独立于支付客户端）
 * <p>
 * 职责：仅负责退款结果查询，不涉及支付操作
 * 适用场景：补偿任务、退款状态同步等
 */
public interface RefundQueryClient {

    /**
     * 查询退款结果
     *
     * @param request 退款查询请求
     * @return 退款查询响应
     */
    RefundQueryResponse query(RefundQueryRequest request);
    /**
     * 获取该客户端支持的支付方式列表
     * <p>
     * 例如：支付宝退款查询客户端支持 ALIPAY_F2F 和 ALIPAY_WAP
     */
    default List<String> getSupportedMethods() {
        return Collections.emptyList();
    }
}
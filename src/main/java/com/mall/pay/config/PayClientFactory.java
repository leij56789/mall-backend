package com.mall.pay.config;

import com.mall.common.BusinessException;
import com.mall.enums.PaymentChannel;
import com.mall.enums.PaymentMethod;
import com.mall.enums.ResultCode;
import com.mall.pay.client.BillDownloadClient;
import com.mall.pay.client.PayClient;
import com.mall.pay.client.RefundQueryClient;
import com.mall.pay.client.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PayClientFactory {

    private final Map<String, PayClient> payClientMap;
    private final Map<String, RefundQueryClient> refundQueryClientMap;
    private final Map<String, BillDownloadClient> billDownloadClientMap;

    /**
     * 构造器注入，同时管理 PayClient 和 RefundQueryClient
     */
    public PayClientFactory(List<PayClient> payClients,
                            List<RefundQueryClient> refundQueryClients,
                            List<BillDownloadClient> billDownloadClients) {
        // 1. 构建 PayClient 映射
        this.payClientMap = payClients.stream()
                .collect(Collectors.toMap(
                        this::resolvePaymentMethod,
                        Function.identity()
                ));

        // 2. 构建 RefundQueryClient 映射（支持多 key）
        this.refundQueryClientMap = new HashMap<>();
        for (RefundQueryClient client : refundQueryClients) {
            List<String> supportedMethods = client.getSupportedMethods();
            if (supportedMethods == null || supportedMethods.isEmpty()) {
                log.warn("退款查询客户端未声明支持的支付方式: {}", client.getClass().getSimpleName());
                continue;
            }
            for (String method : supportedMethods) {
                if (refundQueryClientMap.containsKey(method)) {
                    log.warn("支付方式 {} 已被其他退款查询客户端注册，跳过: {}", method, client.getClass().getSimpleName());
                    continue;
                }
                refundQueryClientMap.put(method.toUpperCase(), client);
            }
        }
        // 3. BillDownloadClient 映射
        this.billDownloadClientMap = new HashMap<>();
        for (BillDownloadClient client : billDownloadClients) {
            if (client instanceof AlipayBillDownloadClient) {
                // 对账单下载按渠道区分，用 ALIPAY 作为 key
                billDownloadClientMap.put(PaymentChannel.ALIPAY.getCode(), client);
            }
        }

        log.info("PayClientFactory 初始化完成: payClients={}, refundQueryClients={}, billDownloadClients={}",
                payClientMap.keySet(), refundQueryClientMap.keySet(), billDownloadClientMap.keySet());
    }

    /**
     * 获取支付客户端
     */
    public PayClient getPayClient(String paymentMethod) {
        String key = paymentMethod.toUpperCase();
        PayClient client = payClientMap.get(key);
        if (client == null) {
            log.error("不支持的支付方式: {}", paymentMethod);
            throw new BusinessException(ResultCode.PAYMENT_METHOD_NOT_SUPPORT);
        }
        return client;
    }

    /**
     * 获取退款查询客户端
     *
     * @param paymentMethod 支付方式（如：ALIPAY_F2F、ALIPAY_WAP、WECHAT等）
     * @return 退款查询客户端
     * @throws BusinessException 如果该支付方式不支持退款查询
     */
    public RefundQueryClient getRefundQueryClient(String paymentMethod) {
        if (paymentMethod == null) {
            log.error("支付方式为空");
            throw new BusinessException(ResultCode.PARAM_MISSING, "支付方式不能为空");
        }

        String key = paymentMethod.toUpperCase();
        RefundQueryClient client = refundQueryClientMap.get(key);
        if (client == null) {
            log.error("支付方式不支持退款查询: paymentMethod={}", paymentMethod);
            throw new BusinessException(ResultCode.PAYMENT_METHOD_NOT_SUPPORT,
                    "支付方式 [" + paymentMethod + "] 不支持退款查询");
        }
        return client;
    }

    /**
     * 判断支付方式是否支持退款查询
     *
     * @param paymentMethod 支付方式
     * @return true 支持，false 不支持
     */
    public boolean supportsRefundQuery(String paymentMethod) {
        if (paymentMethod == null) {
            return false;
        }
        String key = paymentMethod.toUpperCase();
        return refundQueryClientMap.containsKey(key);
    }

    // ===== BillDownloadClient =====
    public BillDownloadClient getBillDownloadClient(String channel) {
        String key = channel.toUpperCase();
        BillDownloadClient client = billDownloadClientMap.get(key);
        if (client == null) {
            throw new BusinessException(ResultCode.PAYMENT_METHOD_NOT_SUPPORT,
                    "该渠道不支持对账单查询");
        }
        return client;
    }

    public boolean supportsBillDownload(String channel) {
        String key = channel.toUpperCase();
        return billDownloadClientMap.containsKey(key);
    }

    /**
     * 解析支付方式（用于 PayClient）
     */
    private String resolvePaymentMethod(PayClient client) {
        if (client instanceof WechatPayClientAdapter) {
            return PaymentChannel.WECHAT.getCode();
        }
        if (client instanceof AlipayClientAdapter) {
            return PaymentChannel.ALIPAY.getCode();
        }
        if (client instanceof AlipayF2FPayClientAdapter) {
            return PaymentMethod.ALIPAY_F2F.getCode();
        }
        if (client instanceof AlipayWapPayClientAdapter) {
            return PaymentMethod.ALIPAY_WAP.getCode();
        }
        if (client instanceof MockPayClient) {
            return PaymentMethod.MOCK.getCode();
        }
        log.warn("未知支付客户端类型: {}", client.getClass().getName());
        throw new IllegalArgumentException("未知支付渠道: " + client.getClass());
    }

    /**
     * 解析支付方式（用于 RefundQueryClient）
     */
    private String resolvePaymentMethodForRefundQuery(RefundQueryClient client) {
//        if (client instanceof AlipayRefundQueryClient) {
//            return PaymentMethod.ALIPAY_F2F.getCode();
//        }
//        if (client instanceof AlipayRefundQueryClient) {
//            return PaymentMethod.ALIPAY_WAP.getCode();
//        }
        if (client instanceof AlipayRefundQueryClient) {
            // 支付宝退款查询客户端支持所有支付宝支付方式
            // 这里用 ALIPAY 作为统一标识，或者可以细化到具体支付方式
            // 但退款查询是按 outRequestNo 查询，不区分 F2F/WAP/APP
            return PaymentChannel.ALIPAY.getCode();
        }
        // 微信等其他渠道可在此扩展
        log.warn("未知退款查询客户端类型: {}", client.getClass().getName());
        throw new IllegalArgumentException("未知退款查询客户端: " + client.getClass());
    }
}
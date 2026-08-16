package com.mall.pay.client.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.BusinessException;
import com.mall.enums.PaymentStatus;
import com.mall.enums.ResultCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.WechatPayProperties;
import com.mall.pay.dto.QueryOrderRequest;
import com.mall.pay.dto.QueryOrderResponse;
import com.mall.pay.dto.ThirdPartyPayRequest;
import com.mall.pay.dto.ThirdPartyPayResponse;
import com.mall.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author jiaolei
 * @date 2026-07-30 16:09
 * @description TODO
 */
@Slf4j
@RequiredArgsConstructor
public class WechatPayClientAdapter implements PayClient {
    @Qualifier("wechatRestClient")
    private final RestClient restClient;
    private final WechatPayProperties wechatPayProperties;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;
    @Override
    public ThirdPartyPayResponse unifiedOrder(ThirdPartyPayRequest request) {
        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("appid", wechatPayProperties.getAppid());
        body.put("mchid", wechatPayProperties.getMchid());
        body.put("description", request.getSubject());
        body.put("out_trade_no", request.getOutTradeNo());
        body.put("notify_url", wechatPayProperties.getNotifyUrl());
        Map<String, Object> amount = new HashMap<>();
        amount.put("total", (int)(Double.parseDouble(request.getTotalAmount()) * 100));
        amount.put("currency", "CNY");
        body.put("amount", amount);

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PAYMENT_SERIALIZE_FAIL);
        }

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 签名头等暂略

        try {
            // ========== 使用 RestClient 发起请求 ==========
            String responseBody = restClient.post()
                                            .uri(wechatPayProperties.getApiBaseUrl()+"/v3/pay/transactions/jsapi")
                                            .headers(httpHeaders -> httpHeaders.addAll(headers))
                                            .body(bodyJson)
                                            .retrieve()
                                            .body(String.class); // 阻塞获取响应体

            if (StrUtil.isBlank(responseBody)) {
                log.error("微信支付响应体为空");
                throw new BusinessException(ResultCode.PAYMENT_DESERIALIZE_FAIL);
            }
            // 解析响应
            Map<String, Object> respMap;
            try {
                respMap = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.error("解析微信支付响应失败: {}", responseBody, e);
                throw new BusinessException(ResultCode.PAYMENT_DESERIALIZE_FAIL);
            }
            String prepayId = (String) respMap.get("prepay_id");
            String payParams = buildPayParams(prepayId);
            return ThirdPartyPayResponse.builder()
                                        .success(true)
                                        .prepayId(prepayId)
                                        .payParams(payParams)
                                        .build();

        } catch (ResourceAccessException e) {
            // ========== 捕获超时等网络异常 ==========
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                // 读取超时（5秒未返回数据）
                log.error("微信支付读取超时", e);
                throw new BusinessException(ResultCode.PAYMENT_TIMEOUT);
            } else if (cause instanceof ConnectException) {
                // 连接超时（3秒未建立连接）
                log.error("微信支付连接超时", e);
                throw new BusinessException(ResultCode.PAYMENT_TIMEOUT);
            } else {
                // 其他网络异常（如 UnknownHostException）
                log.error("微信支付网络异常", e);
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
            }

        } catch (Exception e) {
            if(e instanceof BusinessException){
                throw (BusinessException)e;
            }
            // 其他异常（如 4xx/5xx 响应，但 RestClient 默认会抛出 HttpClientErrorException 等）
            log.error("微信统一下单异常", e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
        }
    }

    private String buildPayParams(String prepayId) {
        // 实际应根据微信 JSAPI 要求生成签名参数，这里简化
        return String.format(
                "{\"appId\":\"%s\",\"timeStamp\":\"%s\",\"nonceStr\":\"%s\",\"package\":\"prepay_id=%s\",\"signType\":\"RSA\",\"paySign\":\"%s\"}",
                wechatPayProperties.getAppid(),
                System.currentTimeMillis() / 1000,
                UUID.randomUUID().toString().substring(0, 8),
                prepayId,
                "mock_sign"
        );
    }
    @Override
    public QueryOrderResponse queryOrder(QueryOrderRequest request) {
        // 构建请求 URL（微信支付查询订单接口）
        String url = wechatPayProperties.getApiBaseUrl() + "/v3/pay/transactions/out-trade-no/" + request.getPaymentId() + "?mchid=" + wechatPayProperties.getMchid();
        // 注意：微信 V3 查询订单 API 为 GET 请求，需要携带签名

        HttpHeaders headers = buildHeaders(); // 复用签名逻辑
        try {
            String responseBody = restClient.get()
                                            .uri(url)
                                            .headers(httpHeaders -> httpHeaders.addAll(headers))
                                            .retrieve()
                                            .body(String.class);

            // 校验响应体
            if (!StringUtils.hasText(responseBody)) {
                log.error("查询订单响应体为空: paymentId={}", request.getPaymentId());
                throw new BusinessException(ResultCode.PAYMENT_RESPONSE_EMPTY);
            }

            Map<String, Object> respMap = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});

            // 解析交易状态（微信字段：trade_state）
            String tradeState = (String) respMap.get("trade_state");
            String prepayId = (String) respMap.get("prepay_id");
            String transactionId = (String) respMap.get("transaction_id");
            String amount = (String) ((Map) respMap.get("amount")).get("total"); // 注意单位：分

            // 转换为统一状态
            String mappedState = mapTradeState(tradeState);
            return QueryOrderResponse.builder()
                                     .success(true)
                                     .tradeState(mappedState)
                                     .prepayId(prepayId)
                                     .transactionId(transactionId)
                                     .totalAmount(amount != null ? String.valueOf(Integer.parseInt(amount) / 100.0) : null)
                                     .build();
        } catch (ResourceAccessException e) {
            log.error("查询订单网络超时: paymentId={}", request.getPaymentId(), e);
            throw new BusinessException(ResultCode.PAYMENT_TIMEOUT);
        } catch (JsonProcessingException e) {
            log.error("查询订单响应解析失败: paymentId={}", request.getPaymentId(), e);
            throw new BusinessException(ResultCode.PAYMENT_DESERIALIZE_FAIL);
        } catch (HttpClientErrorException e) {
            // 404 表示订单不存在
            if (e.getStatusCode().value() == 404) {
                log.warn("订单不存在: paymentId={}", request.getPaymentId());
                return QueryOrderResponse.builder()
                                         .success(true) // 技术调用成功，但业务上表示不存在
                                         .tradeState("NOT_EXIST")
                                         .build();
            }
            log.error("查询订单HTTP异常: paymentId={}, status={}", request.getPaymentId(), e.getStatusCode());
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
        } catch (Exception e) {
            log.error("查询订单未知异常: paymentId={}", request.getPaymentId(), e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
        }
    }

    @Override
    public String mapTradeStatusAfterQueryOrderOnPaymentCompensateJob(String alipayStatus) {
        if (!StringUtils.hasText(alipayStatus)) return "UNKNOWN";

        switch (alipayStatus) {
            case "SUCCESS":
                return PaymentStatus.SUCCESS.getCode();
            case "CLOSED":
            case "PAYERROR":
            case "NOT_EXIST":
                return PaymentStatus.FAILED.getCode();
            default:
                // 其他状态（WAITING/USERPAYING/UNKNOWN）且已超时，视为异常，转为 FAILED 并告警
                return alipayStatus;
        }
    }

    @Override
    public String mapTradeStatusAfterQueryOrderOnAsyncQueryService(String alipayStatus) {

        return "";
    }

//    @Override
//    public String mapTradeStatusAfterUnifiedOrder(String alipayStatus) {
//
//        return "";
//    }

    @Override
    public boolean canRecoverFromPendingConfirm() {
        return false;
    }

    @Override
    public boolean closeOrder(String paymentId) {
        return false;
    }

    /**
     * 将微信的 trade_state 映射为内部统一状态
     */
    private String mapTradeState(String wechatState) {
        if (wechatState == null) return "UNKNOWN";
        switch (wechatState) {
            case "NOTPAY":
            case "USERPAYING":
                return "WAITING";
            case "SUCCESS":
                return "SUCCESS";
            case "REFUND":
            case "REVOKED":
                return "REFUND";
            case "CLOSED":
            case "PAYERROR":   // 🔥 明确映射为 FAILED
                return "FAILED";
            default:
                log.error("微信支付状态异常: {}", wechatState);
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR);
        }
    }
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // TODO: 实际需要根据微信 V3 要求生成 Authorization 头
        // 示例：headers.set("Authorization", "WECHATPAY2-SHA256-RSA2048 ...");
        return headers;
    }
}
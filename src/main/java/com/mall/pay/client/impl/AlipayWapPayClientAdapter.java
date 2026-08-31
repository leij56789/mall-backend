package com.mall.pay.client.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.diagnosis.DiagnosisUtils;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.alipay.v3.model.AlipayTradePayResponseModel;
import com.mall.common.BusinessException;
import com.mall.enums.ResultCode;
import com.mall.pay.config.PayProperties;
import com.mall.pay.dto.ThirdPartyPayRequest;
import com.mall.pay.dto.ThirdPartyPayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.ws.rs.POST;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.format.DateTimeFormatter;

/**
 * 支付宝手机网站支付（WAP）适配器
 * <p>
 * 对应接口：alipay.trade.wap.pay（手机网站支付接口2.0）
 * 特点：返回 HTML 表单，用户通过浏览器跳转到支付宝完成支付
 * <p>
 * 适用场景：移动端 H5 网页支付
 */
@Slf4j
@Component("alipayWapPayClientAdapter")
//@RequiredArgsConstructor
public class AlipayWapPayClientAdapter extends AbstractAlipayPayClientAdapter {

//    private final AlipayClient alipayClient;
//    private final AlipayProperties properties;
    private static final DateTimeFormatter WAP_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AlipayWapPayClientAdapter(AlipayClient alipayClient, PayProperties properties) {
        super(alipayClient, properties);
    }
    // ==================== 1. 预下单（生成支付表单） ====================

    @Override
    public ThirdPartyPayResponse unifiedOrder(ThirdPartyPayRequest request) {
        try {
            PayProperties.AlipayProperties alipayProperties = getAlipayConfig();
            AlipayTradeWapPayRequest alipayRequest = new AlipayTradeWapPayRequest();
            // 异步通知地址
            alipayRequest.setNotifyUrl(alipayProperties.getNotifyUrl());
            // 同步返回地址（可选，由调用方传入）
            if (StringUtils.hasText(request.getReturnUrl())) {
                alipayRequest.setReturnUrl(request.getReturnUrl());
            }

            AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
            model.setOutTradeNo(request.getOutTradeNo());
            model.setTotalAmount(request.getTotalAmount());
            model.setSubject(request.getSubject());
            model.setProductCode("QUICK_WAP_WAY");

            if (request.getTimeExpire() != null) {
                String timeExpireStr = request.getTimeExpire().format(WAP_TIME_FORMATTER);
                model.setTimeExpire(timeExpireStr);
            }

            if (StringUtils.hasText(request.getQuitUrl())) {
                model.setQuitUrl(request.getQuitUrl());
            }

            // 扩展参数：商户传入业务信息（如用户IP等）
            // 如果请求中有 extInfo，可以设置 businessParams
            // 这里简单处理，可以后续扩展

            alipayRequest.setBizModel(model);

            // ===== 关键：使用 pageExecute 获取表单 =====
            // 第二个参数 "POST" 表示返回 HTML 表单；"GET" 返回跳转 URL
            AlipayTradeWapPayResponse response = alipayClient.pageExecute(alipayRequest, "POST");

            // 在得到 response 后
            String traceId = DiagnosisUtils.getTraceId(response);
            String diagnosisUrl = DiagnosisUtils.getDiagnosisUrl(response);
            log.info("支付宝接口调用完成, outTradeNo: {}, traceId: {},diagnosisUrl={}", request.getOutTradeNo(), traceId,diagnosisUrl);

            if (response.isSuccess()) {
                // 成功：返回 HTML 表单
                String formHtml = response.getBody();
                log.info("支付宝 WAP 支付预下单成功: outTradeNo={}", request.getOutTradeNo());
                return ThirdPartyPayResponse.builder()
                        .success(true)
                        .prepayId(request.getOutTradeNo())
                        .payParams(formHtml)   // HTML 表单，前端直接渲染
                        .build();
            }

            // 业务失败
            String subCode = response.getSubCode();
            String subMsg = response.getSubMsg();
            log.error("支付宝 WAP 支付预下单失败: outTradeNo={}, subCode={}, subMsg={}",
                    request.getOutTradeNo(), subCode, subMsg);

            throw new BusinessException(
                    mapAlipaySubCodeToResultCode(subCode),
                    "预下单失败: " + subMsg
            );

        } catch (AlipayApiException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                log.warn("支付宝 WAP 支付预下单超时: outTradeNo={}", request.getOutTradeNo(), e);
                throw new BusinessException(ResultCode.THIRD_PARTY_TIMEOUT,
                        "预下单超时: " + e.getMessage());
            }
            log.error("支付宝 WAP 支付预下单异常: outTradeNo={}", request.getOutTradeNo(), e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR,
                    "预下单异常: " + e.getMessage());
        }
    }

    // ==================== 2. 查询订单 ====================
    //父类实现

    // ==================== 3. 关闭订单 ====================
    //父类实现
//

    // ==================== 4. 状态映射 ====================

    @Override
    public String mapTradeStatusAfterQueryOrderOnAsyncQueryService(String alipayStatus) {
        return mapTradeStatus(alipayStatus);
    }

    //抽象类实现
//    @Override
//    public String mapTradeStatusAfterQueryOrderOnPaymentCompensateJob(String alipayStatus) {
//        return mapTradeStatus(alipayStatus);
//    }

    private String mapTradeStatus(String alipayStatus) {
        if (!StringUtils.hasText(alipayStatus)) {
            return "UNKNOWN";
        }

        switch (alipayStatus) {
            case "WAIT_BUYER_PAY":
                return "WAITING";
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return "SUCCESS";
            case "TRADE_CLOSED":
                return "FAILED";
            default:
                log.warn("未知支付宝交易状态: {}", alipayStatus);
                return "UNKNOWN";
        }
    }

    // ==================== 5. 恢复判断 ====================

    /**
     * WAP 支付是否支持从 PENDING_CONFIRM 恢复？
     * <p>
     * WAP 支付是页面跳转方式，如果预下单超时，用户无法直接恢复支付（需要重新生成支付表单）。
     * 因此与 F2F 一样，不支持从 PENDING_CONFIRM 恢复。
     * </p>
     */
    @Override
    public boolean canRecoverFromPendingConfirm() {
        return false;
    }

    @Override
    public boolean canRecreatePaymentForm() {
        return true;
    }

    // ==================== 6. 辅助方法 ====================

    private ResultCode mapAlipaySubCodeToResultCode(String subCode) {
        if (!StringUtils.hasText(subCode)) {
            return ResultCode.THIRD_PARTY_UNKNOWN_ERROR;
        }

        switch (subCode) {
            // 可重试
            case "ACQ.SYSTEM_ERROR":
                return ResultCode.THIRD_PARTY_RETRYABLE_ERROR;

            // 不可恢复（参数错误、访问被拒等）
            case "ACQ.INVALID_PARAMETER":
            case "ACQ.ACCESS_FORBIDDEN":
            case "ACQ.RISK_MERCHANT_IP_NOT_EXIST":
                return ResultCode.THIRD_PARTY_FATAL_ERROR;

            // 需人工介入
            case "ACQ.PARTNER_ERROR":
                return ResultCode.THIRD_PARTY_MANUAL_INTERVENTION;

            // 这些状态需要查单确认
            case "ACQ.CONTEXT_INCONSISTENT":
            case "ACQ.TRADE_HAS_CLOSE":
            case "ACQ.TRADE_HAS_SUCCESS":
                return ResultCode.THIRD_PARTY_UNKNOWN_ERROR;

            // 其他未知
            default:
                return ResultCode.THIRD_PARTY_UNKNOWN_ERROR;
        }
    }
}
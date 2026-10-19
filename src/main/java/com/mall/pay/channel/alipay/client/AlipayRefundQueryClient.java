package com.mall.pay.channel.alipay.client;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.mall.common.BusinessException;
import com.mall.pay.enums.PaymentMethod;
import com.mall.enums.ResultCode;
import com.mall.pay.client.RefundQueryClient;
import com.mall.pay.config.PayProperties;
import com.mall.pay.dto.RefundQueryRequest;
import com.mall.pay.dto.RefundQueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayRefundQueryClient implements RefundQueryClient {

    private final AlipayClient alipayClient;
    private final PayProperties payProperties;

    @Override
    public RefundQueryResponse query(RefundQueryRequest request) {
        log.info("支付宝退款查询请求: outRequestNo={}, outTradeNo={}, tradeNo={}",
                request.getOutRequestNo(), request.getOutTradeNo(), request.getTradeNo());

        // 参数校验
        if (request.getOutRequestNo() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "退款请求号不能为空");
        }
        if (request.getOutTradeNo() == null && request.getTradeNo() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "商户订单号和支付宝交易号至少传入一个");
        }

        try {
            AlipayTradeFastpayRefundQueryRequest alipayRequest = new AlipayTradeFastpayRefundQueryRequest();
            AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();

            model.setOutRequestNo(request.getOutRequestNo());

            if (StringUtils.hasText(request.getTradeNo())) {
                model.setTradeNo(request.getTradeNo());
            } else {
                model.setOutTradeNo(request.getOutTradeNo());
            }

            // 查询选项
            List<String> queryOptions = new ArrayList<>();
            queryOptions.add("refund_detail_item_list");
            queryOptions.add("gmt_refund_pay");
            model.setQueryOptions(queryOptions);

            alipayRequest.setBizModel(model);

            AlipayTradeFastpayRefundQueryResponse response = alipayClient.execute(alipayRequest);

            if (response.isSuccess()) {
                return buildSuccessResponse(response);
            }

            // 业务失败
            String subCode = response.getSubCode();
            String subMsg = response.getSubMsg();
            log.warn("支付宝退款查询业务失败: outRequestNo={}, subCode={}, subMsg={}",
                    request.getOutRequestNo(), subCode, subMsg);

            return handleBusinessError(subCode, subMsg);

        } catch (AlipayApiException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                log.warn("支付宝退款查询网络超时: outRequestNo={}", request.getOutRequestNo(), e);
                return RefundQueryResponse.builder()
                                          .processing(true)
                                          .failReason("退款查询网络超时: " + truncate(e.getMessage(), 200))
                                          .build();
            }
            log.error("支付宝退款查询SDK异常: outRequestNo={}", request.getOutRequestNo(), e);
            return RefundQueryResponse.builder()
                                      .processing(true)
                                      .failReason("退款查询SDK异常: " + truncate(e.getMessage(), 200))
                                      .build();
        } catch (Exception e) {
            log.error("支付宝退款查询未知异常: outRequestNo={}", request.getOutRequestNo(), e);
            return RefundQueryResponse.builder()
                                      .processing(true)
                                      .failReason("退款查询异常: " + truncate(e.getMessage(), 200))
                                      .build();
        }
    }

    private RefundQueryResponse buildSuccessResponse(AlipayTradeFastpayRefundQueryResponse response) {
        String refundStatus = response.getRefundStatus();
        String subCode = response.getSubCode();
        String subMsg = response.getSubMsg();

        if (!StringUtils.hasText(refundStatus)) {
            String reason = buildFailReason("退款不存在（支付宝未收到退款请求）", subCode, subMsg);
            log.warn("支付宝退款查询：refund_status 为空，退款不存在: outRequestNo={}, outTradeNo={}, subCode={}, subMsg={}",
                    response.getOutRequestNo(), response.getOutTradeNo(), subCode, subMsg);
            return RefundQueryResponse.builder()
                                      .success(false)
                                      .processing(false)
                                      .outTradeNo(response.getOutTradeNo())
                                      .tradeNo(response.getTradeNo())
                                      .failReason(reason)
                                      .build();
        }

        boolean isSuccess = "REFUND_SUCCESS".equals(refundStatus);

        String gmtRefundPayStr = null;
        Date gmtRefundPay = response.getGmtRefundPay();
        if (gmtRefundPay != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            gmtRefundPayStr = sdf.format(gmtRefundPay);
        }

        String failReason = null;
        if (!isSuccess) {
            failReason = buildFailReason("退款状态: " + refundStatus, subCode, subMsg);
        }

        return RefundQueryResponse.builder()
                                  .success(isSuccess)
                                  .processing(!isSuccess)
                                  .outTradeNo(response.getOutTradeNo())
                                  .tradeNo(response.getTradeNo())
                                  .refundAmount(response.getRefundAmount())
                                  .gmtRefundPay(gmtRefundPayStr)
                                  .failReason(failReason)
                                  .build();
    }

    private RefundQueryResponse handleBusinessError(String subCode, String subMsg) {
        if (subCode == null) {
            return RefundQueryResponse.builder()
                                      .processing(true)
                                      .failReason(buildFailReason("退款查询失败", null, subMsg))
                                      .build();
        }

        switch (subCode) {
            case "ACQ.SYSTEM_ERROR":
                return RefundQueryResponse.builder()
                                          .processing(true)
                                          .failReason(buildFailReason("系统异常，请稍后重试", subCode, subMsg))
                                          .build();

            case "ACQ.INVALID_PARAMETER":
                throw new BusinessException(ResultCode.THIRD_PARTY_FATAL_ERROR,
                        buildFailReason("退款查询参数错误", subCode, subMsg));

            case "ACQ.TRADE_NOT_EXIST":
            case "TRADE_NOT_EXIST":
                throw new BusinessException(ResultCode.PAYMENT_REFUND_FAIL,
                        buildFailReason("退款交易不存在", subCode, subMsg));

            case "ACQ.ENTERPRISE_PAY_BIZ_ERROR":
                return RefundQueryResponse.builder()
                                          .processing(true)
                                          .failReason(buildFailReason("因公付业务异常，需人工处理", subCode, subMsg))
                                          .build();

            default:
                return RefundQueryResponse.builder()
                                          .processing(true)
                                          .failReason(buildFailReason("退款查询失败", subCode, subMsg))
                                          .build();
        }
    }
    @Override
    public List<String> getSupportedMethods() {
        return Arrays.asList(
                PaymentMethod.ALIPAY_F2F.getCode(),
                PaymentMethod.ALIPAY_WAP.getCode()
                // 未来新增支付宝支付方式，只需在这里添加
        );
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    /**
     * 构建失败原因：优先带 subCode + subMsg，无 subCode 只带 subMsg
     */
    private String buildFailReason(String baseReason, String subCode, String subMsg) {
        String truncatedMsg = truncate(subMsg, 200);
        if (StringUtils.hasText(subCode)) {
            return baseReason + " [subCode=" + subCode + ", subMsg=" + truncatedMsg + "]";
        }
        if (StringUtils.hasText(subMsg)) {
            return baseReason + " [" + truncatedMsg + "]";
        }
        return baseReason;
    }

}
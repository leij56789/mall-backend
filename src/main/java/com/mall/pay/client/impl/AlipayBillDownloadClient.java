package com.mall.pay.client.impl;

import com.alipay.api.diagnosis.DiagnosisUtils;
import com.alipay.v3.ApiException;
import com.alipay.v3.api.AlipayDataDataserviceBillDownloadurlApi;
import com.alipay.v3.model.AlipayDataDataserviceBillDownloadurlQueryResponseModel;
import com.mall.common.BusinessException;
import com.mall.enums.ResultCode;
import com.mall.pay.client.BillDownloadClient;
import com.mall.pay.config.PayProperties;
import com.mall.pay.dto.BillDownloadRequest;
import com.mall.pay.dto.BillDownloadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayBillDownloadClient implements BillDownloadClient {

    private final AlipayDataDataserviceBillDownloadurlApi billApi;

    @Override
    public BillDownloadResponse queryBillDownloadUrl(BillDownloadRequest request) {
        log.info("支付宝对账单查询请求: billType={}, billDate={}, smid={}, secure={}",
                request.getBillType(), request.getBillDate(), request.getSmid(), request.getSecure());

        // 参数校验
        if (!StringUtils.hasText(request.getBillType())) {
            log.error("账单类型为空");
            throw new BusinessException(ResultCode.PARAM_MISSING, "账单类型不能为空");
        }
        if (!StringUtils.hasText(request.getBillDate())) {
            log.error("账单日期为空");
            throw new BusinessException(ResultCode.PARAM_MISSING, "账单日期不能为空");
        }

        try {
            // 调用 V3 接口
            String billType = request.getBillType();
            String billDate = request.getBillDate();
            String smid = request.getSmid();
            String secure = request.getSecure() != null && request.getSecure() ? "true" : "false";

            // ✅ 只传三个参数
            AlipayDataDataserviceBillDownloadurlQueryResponseModel response = billApi.query(
                    billType, billDate, smid, null
            );


            log.info("支付宝对账单查询成功: billDate={}, downloadUrl={}",
                    billDate, response.getBillDownloadUrl());

            return BillDownloadResponse.builder()
                    .success(true)
                    .billDownloadUrl(response.getBillDownloadUrl())
                    .billFileCode(response.getBillFileCode())
                    .build();

        } catch (ApiException e) {
            log.error("支付宝对账单查询异常: billDate={}, code={}, msg={}",
                    request.getBillDate(), e.getCode(), e.getMessage(), e);
            return handleApiException(e);
        } catch (Exception e) {
            log.error("支付宝对账单查询未知异常: billDate={}", request.getBillDate(), e);
            return BillDownloadResponse.builder()
                    .success(false)
                    .failReason("对账单查询异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 处理 API 异常
     */
    private BillDownloadResponse handleApiException(ApiException e) {
        int errorCodeInt = e.getCode();
        String errorCode = String.valueOf(errorCodeInt);  // ? 转为 String
        String errorMsg = e.getMessage();

        if (errorCode == null || "0".equals(errorCode)) {
            return BillDownloadResponse.builder()
                                       .success(false)
                                       .failReason("对账单查询失败: " + errorMsg)
                                       .build();
        }

        switch (errorCode) {
            // 可重试
            case "SYSTEM_RATE_LIMIT":
            case "USER_RATE_LIMIT":
            case "UNKNOWN_ERROR":
                return BillDownloadResponse.builder()
                        .success(false)
                        .failReason("系统繁忙，请稍后重试")
                        .build();

            // 参数错误（不可恢复）
            case "INVAILID_ARGUMENTS":
            case "BILL_DATE_BEFORE_REGISTRATION":
                throw new BusinessException(ResultCode.PARAM_INVALID, errorMsg);

            // 账单不存在
            case "BILL_NOT_EXIST":
            case "NO_BILL_DATA":
                return BillDownloadResponse.builder()
                        .success(false)
                        .failReason("账单不存在或当天无业务数据")
                        .build();

            case "TYPE_NOT_SUPPORTED":
                return BillDownloadResponse.builder()
                        .success(false)
                        .failReason("此账单类型不支持下载，请检查 bill_type 参数")
                        .build();

            default:
                return BillDownloadResponse.builder()
                        .success(false)
                        .failReason("对账单查询失败: " + errorMsg)
                        .build();
        }
    }
}
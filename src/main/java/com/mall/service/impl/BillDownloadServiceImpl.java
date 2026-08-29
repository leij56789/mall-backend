package com.mall.service.impl;

import com.mall.common.BusinessException;
import com.mall.enums.PaymentChannel;
import com.mall.enums.ResultCode;
import com.mall.pay.client.BillDownloadClient;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.dto.BillDownloadRequest;
import com.mall.pay.dto.BillDownloadResponse;
import com.mall.service.BillDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillDownloadServiceImpl implements BillDownloadService {

    private final PayClientFactory payClientFactory;

    @Override
    public BillDownloadResponse getBillDownloadUrl(BillDownloadRequest request) {
        // 1. 参数校验
        if (request.getBillType() == null || request.getBillType().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "账单类型不能为空");
        }
        if (request.getBillDate() == null || request.getBillDate().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "账单日期不能为空");
        }

        // 2. 通过工厂获取对账单客户端（目前只有支付宝，固定使用 ALIPAY）
        BillDownloadClient client = payClientFactory.getBillDownloadClient(PaymentChannel.ALIPAY.getCode());

        // 3. 调用客户端查询
        BillDownloadResponse response = client.queryBillDownloadUrl(request);
        if (!response.isSuccess()) {
            log.warn("对账单查询失败: billType={}, billDate={}, reason={}",
                    request.getBillType(), request.getBillDate(), response.getFailReason());
        }
        return response;
    }

    @Override
    public BillDownloadResponse getYesterdayTradeBill() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        log.info("获取昨日交易对账单: date={}", yesterday);

        BillDownloadRequest request = BillDownloadRequest.builder()
                .billType("trade")
                .billDate(yesterday)
                .build();

        return getBillDownloadUrl(request);
    }
}
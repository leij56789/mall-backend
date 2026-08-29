package com.mall.controller;

import com.mall.common.Result;
import com.mall.pay.client.PayClient;
import com.mall.pay.dto.RefundRecordQueryRequest;
import com.mall.pay.dto.RefundRecordQueryResponse;
import com.mall.service.PaymentOrderService;
import com.mall.service.PaymentRefundRecordQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/refund")
@RequiredArgsConstructor
public class RefundController {

    private final PaymentRefundRecordQueryService refundRecordQueryService;
    private final PaymentOrderService paymentOrderService;

    /**
     * 发起退款
     */
    @PostMapping("/create")
    public Result<PayClient.RefundResponse> createRefund(@Valid @RequestBody PayClient.RefundRequest request) {
        log.info("收到退款请求: outTradeNo={}, refundAmount={}",
                request.getOutTradeNo(), request.getRefundAmount());
        PayClient.RefundResponse response = paymentOrderService.refund(request);
        return Result.success(response);
    }

    /**
     * 分页查询退款记录
     */
    @PostMapping("/records")
    public Result<RefundRecordQueryResponse> queryRecords(@RequestBody RefundRecordQueryRequest request) {
        RefundRecordQueryResponse response = refundRecordQueryService.queryRefundRecords(request);
        return Result.success(response);
    }

    /**
     * 根据支付单号查询退款记录
     */
    @GetMapping("/payment/{paymentId}")
    public Result<List<RefundRecordQueryResponse.RefundRecordInfo>> queryByPaymentId(
            @PathVariable String paymentId) {
        List<RefundRecordQueryResponse.RefundRecordInfo> records = 
                refundRecordQueryService.queryByPaymentId(paymentId);
        return Result.success(records);
    }

    /**
     * 根据退款请求号查询退款记录
     */
    @GetMapping("/outRequestNo/{outRequestNo}")
    public Result<RefundRecordQueryResponse.RefundRecordInfo> queryByOutRequestNo(
            @PathVariable String outRequestNo) {
        RefundRecordQueryResponse.RefundRecordInfo record = 
                refundRecordQueryService.queryByOutRequestNo(outRequestNo);
        return Result.success(record);
    }
}
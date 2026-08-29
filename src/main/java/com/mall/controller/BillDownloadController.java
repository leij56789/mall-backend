package com.mall.controller;

import com.mall.common.Result;
import com.mall.pay.dto.BillDownloadRequest;
import com.mall.pay.dto.BillDownloadResponse;
import com.mall.service.BillDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/bill")
@RequiredArgsConstructor
public class BillDownloadController {

    private final BillDownloadService billDownloadService;

    /**
     * 查询对账单下载地址
     */
    @PostMapping("/download/url")
    public Result<BillDownloadResponse> getBillDownloadUrl(@Valid @RequestBody BillDownloadRequest request) {
        log.info("获取对账单下载地址: billType={}, billDate={}", request.getBillType(), request.getBillDate());
        BillDownloadResponse response = billDownloadService.getBillDownloadUrl(request);
        return Result.success(response);
    }

    /**
     * 获取昨日交易对账单下载地址（快捷接口）
     */
    @GetMapping("/download/yesterday")
    public Result<BillDownloadResponse> getYesterdayTradeBill() {
        log.info("获取昨日交易对账单下载地址");
        BillDownloadResponse response = billDownloadService.getYesterdayTradeBill();
        return Result.success(response);
    }
}
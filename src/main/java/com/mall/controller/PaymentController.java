package com.mall.controller;

import cn.hutool.extra.qrcode.QrCodeUtil;
import com.mall.annotation.Log;
import com.mall.common.Result;
import com.mall.entity.PaymentOrder;
import com.mall.pay.dto.PaymentCallbackResponse;
import com.mall.pay.dto.PaymentCreateRequest;
import com.mall.pay.dto.PaymentResponse;
import com.mall.pay.dto.PaymentStatusResponse;
import com.mall.pay.service.AlipayCallbackProcessor;
import com.mall.pay.service.WechatCallbackProcessor;
import com.mall.service.PaymentOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    /**
     * 创建支付单（发起支付）
     */
    @Log("创建支付单")
    @PostMapping("/create")
    public Result<PaymentResponse> createPayment(@Valid @RequestBody PaymentCreateRequest request) {
        PaymentResponse response = paymentOrderService.createPayment(request);
        return Result.success(response);
    }

    /**
     * 支付回调（第三方异步通知）
     * 注意：第三方通常要求返回纯文本 "SUCCESS" 或 "FAIL"
     * 这里为了统一使用 Result，但实际生产可能直接返回字符串
     */
//    @Log("支付回调")
//    @PostMapping("/callback")
//    public String callback(@RequestBody String rawBody) {
//        // 处理逻辑...
//        PaymentCallbackResponse response = paymentOrderService.handleCallback(request);
//        return response.toThirdPartyResponse(); // 返回 "SUCCESS" 或 "FAIL"
//    }
    private final WechatCallbackProcessor wechatProcessor;
    private final AlipayCallbackProcessor alipayProcessor;

    @PostMapping("/callback/wechat")
    public String wechat(@RequestBody String rawBody) {
        log.info("收到微信回调");
        return wechatProcessor.process(rawBody);
    }
    @PostMapping(value = "/callback/alipay", produces = "text/plain;charset=UTF-8")
    public String alipay(HttpServletRequest request) {
        // 直接获取参数 Map，Spring 自动完成 URLDecode
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = entry.getValue();
            params.put(entry.getKey(), values.length > 0 ? values[0] : "");
        }

        log.info("收到支付宝回调，out_trade_no={}, trade_status={}",
                params.get("out_trade_no"), params.get("trade_status"));

        // 直接传入 Map，无需手动解析
        return alipayProcessor.process(params);
    }
    /**
     * 查询支付状态（前端轮询）
     */
    @Log("查询支付状态")
    @GetMapping("/status/{paymentId}")
    public Result<PaymentStatusResponse> getPaymentStatus(@PathVariable String paymentId) {
        PaymentStatusResponse response = paymentOrderService.getPaymentStatus(paymentId);
        return Result.success(response);
    }
//    @GetMapping("/qrcode/{paymentId}")
//    public void getQrCode(@PathVariable String paymentId,
//                          @RequestParam(defaultValue = "300") int width,
//                          @RequestParam(defaultValue = "300") int height,
//                          HttpServletResponse response) throws IOException {
//        // ...
//        QrCodeUtil.generate(qrCode, width, height, "png", response.getOutputStream());
//    }
    /**
     * 获取支付二维码图片
     * 前端使用：<img src="/api/payment/qrcode/{paymentId}" />
     */
    @GetMapping(value = "/qrcode/{paymentId}", produces = MediaType.IMAGE_PNG_VALUE)
    public void getQrCode(@PathVariable String paymentId,
                          @RequestParam(defaultValue = "300") int width,
                          @RequestParam(defaultValue = "300") int height,
                          HttpServletResponse response) throws IOException {
        paymentOrderService.getQrCode(paymentId,width,height,response);

    }
}
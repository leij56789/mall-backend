package com.mall.controller;

import com.mall.pay.facade.PayFacade;
import com.mall.pay.facade.dto.PayOrderDTO;
import com.mall.pay.facade.dto.PayResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/test/pay-facade")
@RequiredArgsConstructor
public class PayFacadeTestController {

    private final PayFacade payFacade;

    @GetMapping("/pay")
    public PayResult pay(@RequestParam String bizType,
                         @RequestParam String bizId,
                         @RequestParam Long userId,      // ★ 新增
                         @RequestParam BigDecimal amount) {
        PayOrderDTO dto = PayOrderDTO.builder()
                                     .bizType(bizType)
                                     .bizId(bizId)
                                     .userId(userId)                    // ★ 新增
                                     .amount(amount)
                                     .subject("测试支付")
                                     .paymentMethod("ALIPAY_WAP")
                                     .build();
        return payFacade.pay(dto);
    }

    @GetMapping("/query/{paymentId}")
    public Object query(@PathVariable String paymentId) {
        return payFacade.query(paymentId);
    }
}
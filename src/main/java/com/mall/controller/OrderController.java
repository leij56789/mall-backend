package com.mall.controller;

import com.mall.annotation.Log;
import com.mall.common.Result;
import com.mall.dto.request.CreateArticleRequest;
import com.mall.dto.request.CreateOrderRequest;
import com.mall.dto.response.CreateArticleResponse;
import com.mall.dto.response.CreateOrderResponse;
import com.mall.service.OrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jiaolei
 * @date 2026-06-21 13:33
 * @description TODO
 */
@RestController
@RequestMapping("api/orders")
public class OrderController {
    @Autowired
    OrdersService ordersService;
    @Log("创建订单")
    @PostMapping("")
    public Result<CreateOrderResponse> createOrder(@RequestBody CreateOrderRequest createOrderRequest){
        CreateOrderResponse createOrderResponse=ordersService.createOrder(createOrderRequest);
        return Result.success(createOrderResponse);
    }

}
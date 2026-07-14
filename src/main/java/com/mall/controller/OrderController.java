package com.mall.controller;

import com.mall.annotation.Log;
import com.mall.common.Result;
import com.mall.dto.request.CreateOrderRequest;
import com.mall.dto.request.OrderListRequest;
import com.mall.dto.response.CreateOrderResponse;
import com.mall.dto.response.OrderListResponse;
import com.mall.dto.response.PageResult;
import com.mall.service.OrdersService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    @Log("订单列表")
    @GetMapping("")
    public Result<PageResult> listOrders(@Valid OrderListRequest orderListRequest){

        PageResult<OrderListResponse> pageResult=ordersService.listOrders(orderListRequest);
        return Result.success(pageResult);

    }

}
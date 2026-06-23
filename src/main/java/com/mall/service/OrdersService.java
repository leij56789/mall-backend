package com.mall.service;

import com.mall.dto.request.CreateOrderRequest;
import com.mall.dto.response.CreateOrderResponse;
import com.mall.entity.Orders;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author jiaolei
* @description 针对表【orders】的数据库操作Service
* @createDate 2026-06-21 17:15:02
*/
public interface OrdersService extends IService<Orders> {

    CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest);

    void cancelExpireOrder(Long orderId);
}

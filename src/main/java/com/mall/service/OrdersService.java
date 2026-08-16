package com.mall.service;

import com.mall.dto.request.CreateOrderRequest;
import com.mall.dto.request.OrderListRequest;
import com.mall.dto.response.CreateOrderResponse;
import com.mall.dto.response.OrderListResponse;
import com.mall.dto.response.PageResult;
import com.mall.entity.Orders;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.enums.OrderStatus;
import com.mall.mq.message.OrderTimeoutMessage;

/**
* @author jiaolei
* @description 针对表【orders】的数据库操作Service
* @createDate 2026-06-21 17:15:02
*/
public interface OrdersService extends IService<Orders> {

    CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest);

    void cancelExpireOrder(Long orderId);

    void cancelExpireOrderByBrokerMessageLog(OrderTimeoutMessage orderTimeoutMessage);

    void cancelExpireOrderByOrderTimeMessage(OrderTimeoutMessage orderTimeoutMessage);

    PageResult<OrderListResponse> listOrders(OrderListRequest orderListRequest);

    void cancelSeckillExpireOrderByOrderTimeMessage(OrderTimeoutMessage orderTimeoutMessage);
    void alterOrderStatus(Orders order, Long bookId, Integer quantity, Long orderId, OrderStatus orderStatus);
    void cancelSeckillExpireOrderByOrder(Orders order);
}

package com.mall.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.request.OrderListRequest;
import com.mall.dto.response.OrderListResponse;
import com.mall.entity.Orders;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
* @author jiaolei
* @description 针对表【orders】的数据库操作Mapper
* @createDate 2026-06-21 17:15:02
* @Entity com.mall.entity.Orders
*/
public interface OrdersMapper extends BaseMapper<Orders> {

    IPage<OrderListResponse> selectOrderList(
            Page<OrderListResponse> pageParam,@Param("req")OrderListRequest orderListRequest, String currentUsername);

    @Select("SELECT * FROM orders WHERE id = #{orderId} AND status = #{status} FOR UPDATE")
    Orders selectForUpdateIfPending(@Param("orderId") Long orderId,@Param("status")Integer status);
}
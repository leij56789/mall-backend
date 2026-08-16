package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.PaymentOrder;
import com.mall.enums.PaymentStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author jiaolei
* @description 针对表【payment_order】的数据库操作Mapper
* @createDate 2026-07-18 14:37:12
* @Entity generator.entity.PaymentOrder
*/
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("SELECT * FROM payment_order WHERE order_id = #{orderId} AND status IN #{statuses} FOR UPDATE")
    List<PaymentOrder> selectActiveByOrderIdForUpdate(@Param("orderId") Long orderId,
                                                      @Param("statuses") List<String> statuses);


    // 改造你的 Mapper
    @Select("SELECT * FROM payment_order WHERE order_id = #{orderId} AND payment_order.status=#{status} FOR UPDATE")
    PaymentOrder selectByOrderIdForUpdate(@Param("orderId") Long orderId, @Param("status")String status);

    @Select("SELECT * FROM payment_order WHERE status = #{status} AND created_at < #{time}")
    List<PaymentOrder> selectByStatusAndCreatedBefore(@Param("status") String status,
                                                      @Param("time") LocalDateTime time);
    /**
     * 查询超时（expire_at < now）且状态在指定列表中的支付单
     */
//    @Select("<script>" +
//            "SELECT * FROM payment_order " +
//            "WHERE status IN " +
//            "<foreach collection='statusList' item='status' open='(' separator=',' close=')'>" +
//            "   #{status}" +
//            "</foreach>" +
//            " AND expired_at &lt; NOW() " +
//            " LIMIT #{limit}" +
//            "</script>")
    List<PaymentOrder> selectTimeoutNonFinal(@Param("statusList") List<String> statusList,
                                             @Param("limit") Integer limit);

    @Update("UPDATE payment_order SET status = #{newStatus}, third_party_trade_no = #{tradeNo}, " +
            "callback_time = NOW(), updated_at = NOW() " +
            "WHERE payment_id = #{paymentId} AND status = #{oldStatus}")
    int updateStatusIfMatch(@Param("paymentId") String paymentId,
                            @Param("newStatus") String newStatus,
                            @Param("tradeNo") String tradeNo,
                            @Param("oldStatus") String oldStatus);

    @Select("SELECT * FROM payment_order WHERE payment_id = #{paymentId} FOR UPDATE")
    PaymentOrder selectByPaymentIdForUpdate(@Param("paymentId")String outTradeNo);
}





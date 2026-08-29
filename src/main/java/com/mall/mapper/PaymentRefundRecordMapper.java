package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.PaymentRefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentRefundRecordMapper extends BaseMapper<PaymentRefundRecord> {

    /**
     * 根据退款请求号查询（唯一索引）
     */
    @Select("SELECT * FROM payment_refund_record WHERE out_request_no = #{outRequestNo}")
    PaymentRefundRecord selectByOutRequestNo(@Param("outRequestNo") String outRequestNo);

    /**
     * 查询超时未完成的退款记录（PROCESSING 且 next_query_time < now）
     */
    @Select("SELECT * FROM payment_refund_record " +
            "WHERE status = 'PROCESSING' " +
            "AND next_query_time IS NOT NULL " +
            "AND next_query_time < NOW() " +
            "ORDER BY next_query_time ASC " +
            "LIMIT #{limit}")
    List<PaymentRefundRecord> selectProcessingTimeout(@Param("limit") int limit);

    /**
     * 根据支付单号查询所有退款记录
     */
    @Select("SELECT * FROM payment_refund_record WHERE payment_id = #{paymentId} ORDER BY created_at DESC")
    List<PaymentRefundRecord> selectByPaymentId(@Param("paymentId") String paymentId);

    /**
     * 更新退款状态为成功
     */
    @Update("UPDATE payment_refund_record " +
            "SET status = 'SUCCESS', " +
            "    third_party_refund_no = #{thirdPartyRefundNo}, " +
            "    updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'PROCESSING'")
    int updateSuccess(@Param("id") Long id, @Param("thirdPartyRefundNo") String thirdPartyRefundNo);

    /**
     * 更新退款状态为失败
     */
    @Update("UPDATE payment_refund_record " +
            "SET status = 'FAILED', " +
            "    fail_reason = #{failReason}, " +
            "    updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'PROCESSING'")
    int updateFailed(@Param("id") Long id, @Param("failReason") String failReason);

    /**
     * 更新下次查询时间（带乐观锁）
     *
     * @param id               退款记录ID
     * @param oldRetryCount    期望的旧重试次数（乐观锁条件）
     * @param nextQueryTime    下次查询时间
     * @return 更新行数（1=成功，0=乐观锁冲突）
     */
    @Update("UPDATE payment_refund_record " +
            "SET next_query_time = #{nextQueryTime}, " +
            "    retry_count = retry_count + 1, " +
            "    updated_at = NOW() " +
            "WHERE id = #{id} " +
            "  AND retry_count = #{oldRetryCount}")
    int updateNextQueryTime(@Param("id") Long id,
                            @Param("oldRetryCount") Integer oldRetryCount,
                            @Param("nextQueryTime") LocalDateTime nextQueryTime);

    /**
     * 查询指定支付单的累计退款金额
     */
    @Select("SELECT COALESCE(SUM(refund_amount), 0) FROM payment_refund_record " +
            "WHERE payment_id = #{paymentId} AND status = 'SUCCESS'")
    BigDecimal sumRefundAmountByPaymentId(@Param("paymentId") String paymentId);
}
package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.PaymentAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentAuditLogMapper extends BaseMapper<PaymentAuditLog> {
    /*
    * 场景 建议
审计日志写入是同步且高频的 行锁可能够用
审计日志写入跨多个服务（微服务） 需要分布式锁
锁需要跨 Redis 缓存和数据库 需要分布式锁
需要精细控制锁的超时和重试 分布式锁更灵活
不想让事务持有锁太长时间 分布式锁可以脱离事务
    * */
    /**
     * 查询最近一条审计日志（带行锁，防止并发写入）
     */
    @Select("SELECT * FROM payment_audit_log " +
            "WHERE payment_id = #{paymentId} " +
            "ORDER BY created_at DESC " +
            "LIMIT 1 " +
            "FOR UPDATE")
    PaymentAuditLog selectLastByPaymentIdForUpdate(@Param("paymentId") String paymentId);

    /**
     * 查询最近一条审计日志（全局，带行锁）
     */
    @Select("SELECT * FROM payment_audit_log " +
            "ORDER BY created_at DESC " +
            "LIMIT 1 " +
            "FOR UPDATE")
    PaymentAuditLog selectLastForUpdate();

    @Select("SELECT * FROM payment_audit_log " +
            "WHERE payment_id = #{paymentId} " +
            "ORDER BY created_at DESC " +
            "LIMIT 1 ")
    PaymentAuditLog selectLastByPaymentId(String paymentId);

    @Select("SELECT * FROM payment_audit_log WHERE payment_id = #{paymentId} ORDER BY created_at ASC")
    List<PaymentAuditLog> selectByPaymentIdOrderByTime(@Param("paymentId") String paymentId);
}
package com.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.common.BusinessException;
import com.mall.config.MessageProperties;
import com.mall.entity.Orders;
import com.mall.entity.PaymentOrder;
import com.mall.entity.SeckillBook;
import com.mall.enums.OrderStatus;
import com.mall.enums.OrderType;
import com.mall.enums.ResultCode;
import com.mall.mapper.OrdersMapper;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.mapper.SeckillBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
/**
 * 订单-支付状态协调服务
 * 职责：处理订单状态与支付状态之间的联动转换
 * 例如：支付成功 → 订单变为 PAID；订单超时取消 → 检查活跃支付单
 */
@Service
public class OrderPaymentOrchestrationService {
    @Autowired
    PaymentOrderMapper paymentOrderMapper;
    @Autowired
    @Lazy
    OrdersService ordersService;
    @Autowired
    OrdersMapper ordersMapper;
    @Autowired
    MessageProperties messageProperties;
    @Autowired
    SeckillBookMapper seckillBookMapper;
    @Autowired
    RedisRollbackService redisRollbackService;
    @Autowired
    @Lazy
    OrderPaymentOrchestrationService orderPaymentOrchestrationService;
    /**
     * 更新支付单状态（必须在外层事务中调用）
     *
     * @param orderId 支付单ID
     * @throws IllegalTransactionStateException 如果调用方未开启事务
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelOrderIfNoActivePayment(Long orderId) {
//         主动校验当前线程是否绑定了事务
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("该方法必须在事务中调用！");
        }
        // 1. 🔥 先锁定该订单下的所有活跃支付单（阻止并发支付）
        List<PaymentOrder> payments = paymentOrderMapper.selectActiveByOrderIdForUpdate(
                orderId,
                Arrays.asList("INIT", "WAITING", "PENDING_CONFIRM")
        );

        if (!payments.isEmpty()) {
            log.info("订单存在活跃支付单，跳过取消: orderId={}, count={}", orderId, payments.size());
            throw new BusinessException(ResultCode.ORDER_HAS_ACTIVE_PAYMENT);
        }
        log.info("订单无活跃支付单，可以取消: orderId={}", orderId);
    }

    /**
     * 更新支付单状态（必须在外层事务中调用）
     *
     * @param orderId 支付单ID
     * @param status 新状态
     * @throws IllegalTransactionStateException 如果调用方未开启事务
     */
    @Transactional(propagation=Propagation.MANDATORY)
    public Orders selectOrderStatus(Long orderId, Integer status) {
        // 主动校验当前线程是否绑定了事务
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("该方法必须在事务中调用！");
        }
        // ?? 1. 先锁定订单行，确认状态
        Orders order = ordersMapper.selectForUpdateIfPending(orderId,status);
//        if (!OrderStatus.PENDING.getValue().equals(order.getStatus())) {
//            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_PAYABLE);
//        }
        return order;
    }
    //关闭支付单的同时取消订单
    @Transactional(propagation=Propagation.MANDATORY)
    public void cancelSeckillExpireOrderByOrder(Orders order) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("该方法必须在事务中调用！");
        }
        if(order==null){
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        Integer orderType = order.getOrderType();
        Long orderId = order.getId();
        Long bookId = order.getBookId();
        Long userId = order.getUserId();
        Integer quantity = order.getQuantity();
        if(orderType==null){
            log.error("orderType={}", OrderType.getDescByCode(orderType));
            throw new BusinessException(ResultCode.ORDER_TYPE_ERROR);
        }
        if(order.getStatus() !=OrderStatus.PENDING.getValue()){
            log.info("订单已处理，跳过：orderId={},status={}"
                    ,orderId,OrderStatus.getDescByValue(order.getStatus()));
            return;
        }

        if(order.getExpireTime().isAfter(LocalDateTime.now().plus(messageProperties.getTimeToleranceMs()))){
            throw new BusinessException(ResultCode.ORDER_NOT_EXPIRE);
        }
        orderPaymentOrchestrationService.cancelOrderIfNoActivePayment(orderId);
        boolean updated = ordersService.lambdaUpdate()
                              .set(Orders::getStatus, OrderStatus.EXPIRED.getValue())
                              .eq(Orders::getStatus, OrderStatus.PENDING.getValue())
                              .eq(Orders::getId, orderId).update();
        if(!updated){
            throw new BusinessException(ResultCode.ORDER_UPDATE_FAIL);
        }
        SeckillBook seckillBook = seckillBookMapper.selectOne(new LambdaQueryWrapper<SeckillBook>()
                .eq((SeckillBook::getBookId), bookId));
        if(seckillBook==null){
            log.error("秒杀活动不存在：bookId={}",bookId);
            throw new BusinessException(ResultCode.SECKILL_NOT_EXIST);
        }
        seckillBook.setStock(seckillBook.getStock()+quantity);
        int bookRows = seckillBookMapper.updateById(seckillBook);
        if(bookRows==0){
            throw new BusinessException(ResultCode.STOCK_RECOVER_FAIL);
        }
        log.info("库存恢复成功：seckillBookId={},quantity={},newStock={}",
                seckillBook.getId(),quantity,seckillBook.getStock());

        redisRollbackService.rollbackRedisSeckillOrThrow(bookId,userId,orderId);
        log.info("秒杀回滚成功");
        log.info("秒杀订单超时取消成功：orderId={}, bookId={}, quantity={}",
                orderId, bookId, quantity);
    }

    //支付成功的同时更新订单到终态
    @Transactional(propagation=Propagation.MANDATORY)
    public void updateOrderToPaidFromStatus(Long paymentId,OrderStatus orderStatus){
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("该方法必须在事务中调用！");
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getPaymentId, paymentId)
        );
        if (paymentOrder == null || paymentOrder.getOrderId() == null) {
            log.error("支付单不存在: paymentId={}", paymentId);
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }
        int updated = ordersMapper.update(new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, OrderStatus.PAID.getValue())
                .eq(Orders::getStatus, orderStatus.getValue())
                .eq(Orders::getId, paymentOrder.getOrderId()));
        if (updated != 1) {
            log.error("订单状态更新失败: paymentId={}, orderId={}", paymentId, paymentOrder.getOrderId());
            throw new BusinessException(ResultCode.ORDER_UPDATE_FAIL);
        }
    }

//    public void applyOrderCancelled(Long orderId) {
//        int updated = orderMapper.updateStatus(orderId, "CANCELLED", "PENDING");
//        if (updated == 0) return;
//        // 后置逻辑...
//    }
}
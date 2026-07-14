package com.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.config.MessageProperties;
import com.mall.dto.request.OrderListRequest;
import com.mall.dto.response.OrderListResponse;
import com.mall.dto.response.PageResult;
import com.mall.entity.SeckillBook;
import com.mall.enums.OrderStatus;
import com.mall.enums.OrderType;
import com.mall.enums.ResultCode;
import com.mall.dto.request.CreateOrderRequest;
import com.mall.dto.response.CreateOrderResponse;
import com.mall.entity.Book;
import com.mall.entity.Orders;
import com.mall.entity.User;
import com.mall.interceptor.JwtInterceptor;
import com.mall.mapper.BookMapper;
import com.mall.mapper.SeckillBookMapper;
import com.mall.mq.message.OrderTimeoutMessage;
import com.mall.mq.producer.OrderMessageProducer;
import com.mall.service.BookService;
import com.mall.service.OrdersService;
import com.mall.mapper.OrdersMapper;
import com.mall.service.RedisRollbackService;
import com.mall.service.UserService;
import com.mall.utils.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
* @author jiaolei
* @description 针对表【orders】的数据库操作Service实现
 * 订单超时
 * 需要准备的框架：
 * 1建一张本地事务表，mybatisPlus生成相应的类
 * 2objectMapper和MessageProperties解析json和注入MQ相关数据
 * 3MessageStatus和ResultCode补充创建
 * 4Config,Consumer,Producer
 * 5AlertService生产者端达最大重试次数之后的人工处理
 * 6CompensateJob用于定时扫描本地消息表
 * 订单超时重试逻辑：
 * service调动生产者发消息
 * 生产者包装快照，本地事务表写入，异步发消息，根据异常维护本地事务表，重试由CompensateJob触发，达最大重试触发AlterService
 * 消费者解析快照，重试主要由异常触发，异常包含业务异常(数据库写，其他)，系统异常，达最大重试死信处理并触发AlterService
 * 注意：消费者端的业务处理将代码把数据库写操作的业务逻辑写在最后
 *rabbitmqctl delete_queue delay.queue
 * rabbitmqctl delete_queue orderTimeout.queue
 * rabbitmqctl delete_queue order.timeout.dlq
* @createDate 2026-06-21 17:15:02
*/
@Slf4j
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders>
    implements OrdersService{
    @Autowired
    BookService bookService;
    @Autowired
    UserService userService;
    @Autowired
    BookMapper bookMapper;
    @Autowired
    OrderMessageProducer orderMessageProducer;
    @Autowired
    OrdersMapper ordersMapper;
    @Autowired
    SnowflakeIdGenerator snowflakeIdGenerator;
    @Autowired
    MessageProperties messageProperties;
    @Autowired
    SeckillBookMapper seckillBookMapper;
    @Autowired
    RedisRollbackService redisRollbackService;
    private static final Set<String> ALLOWED_COLUMNS=Set.of("createdAt","totalAmount");
    private static final Set<String> ALLOWED_ORDERS=Set.of("asc","desc");


    @Transactional
    @Override
    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
        //select name,price from book where id=
        //insert into order orderNo,bookId,bookName,quantity,totalAmount
        // ,status,statusDesc,address,expireTime,createdAt
        //select * from order
        //+statusDesc
        CreateOrderResponse createOrderResponse = new CreateOrderResponse();
        String currentUsername = JwtInterceptor.getCurrentUser();
        if(StrUtil.isBlank(currentUsername)){
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long bookId = createOrderRequest.getBookId();
        String address = createOrderRequest.getAddress();
        Integer quantity = createOrderRequest.getQuantity();
        if(bookId==null||address==null||quantity==null){
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        User currentUser = userService.getByUsernameOrThrow(currentUsername);
        Book book = bookService.getById(bookId);
        if(book==null){
            throw new BusinessException(ResultCode.BOOK_NOT_FOUND);
        }
        //扣减库存
        if(book.getStock()<quantity){
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }
        book.setStock(book.getStock()-quantity);
        int rows = bookMapper.updateById(book);
        if(rows==0){
            log.warn("乐观锁冲突：bookId={},version={}",book.getId(),book.getVersion());
            throw new BusinessException(ResultCode.SYSTEM_BUSY);
        }
        Orders orders = new Orders();
        orders.setAddress(address);
        orders.setQuantity(quantity);
        orders.setBookId(bookId);
        orders.setStatus(OrderStatus.PENDING.getValue());
        BigDecimal totalAmount = book.getPrice().multiply(BigDecimal.valueOf(quantity));
        orders.setTotalAmount(totalAmount);
        orders.setUserId(currentUser.getId());
        orders.setExpireTime(LocalDateTime.now().plusSeconds(messageProperties.getDelayTime()/1000));
        orders.setOrderNo(snowflakeIdGenerator.nextIdStr());
        orders.setOrderType(OrderType.NORMAL.getCode());
        if(!this.save(orders)){
            throw new BusinessException(ResultCode.ORDER_CREATE_FAIL);
        }
        Orders ordersRes = this.getById(orders.getId());
        if(ordersRes==null){
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        //消息队列处理订单超时
        orderMessageProducer.sendOrderTimeoutMessage(ordersRes);

        BeanUtils.copyProperties(ordersRes,createOrderResponse);
        createOrderResponse.setCreatedAt(ordersRes.getCreatedAt());
        createOrderResponse.setOrderId(ordersRes.getId());
        createOrderResponse.setOrderNo(ordersRes.getOrderNo());
        createOrderResponse.setStatus(ordersRes.getStatus());
        createOrderResponse.setAddress(ordersRes.getAddress());
        createOrderResponse.setQuantity(ordersRes.getQuantity());
        createOrderResponse.setBookId(ordersRes.getBookId());
        createOrderResponse.setExpireTime(ordersRes.getExpireTime());
        createOrderResponse.setTotalAmount(ordersRes.getTotalAmount());
        createOrderResponse.setBookName(book.getName());
        createOrderResponse.setStatusDesc(OrderStatus.getDescByValue(ordersRes.getStatus()));
        return createOrderResponse;
    }

    @Transactional
    @Override
    public void cancelExpireOrder(Long orderId) {
        if(orderId==null){
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }


    }

    @Log("订单超时自动取消")
    @Transactional
    @Override
    public void cancelExpireOrderByBrokerMessageLog(OrderTimeoutMessage brokerMessageLog) {
        if(brokerMessageLog==null){
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
    }

    @Log("订单超时自动取消")
    @Transactional(rollbackFor =Exception.class)
    @Override
    public void cancelExpireOrderByOrderTimeMessage(OrderTimeoutMessage orderTimeoutMessage) {
        if(orderTimeoutMessage==null){
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        Long orderId = orderTimeoutMessage.getOrderId();
        Long bookId = orderTimeoutMessage.getBookId();
        Integer quantity = orderTimeoutMessage.getQuantity();
        Long expireTimestamp = orderTimeoutMessage.getExpireTimestamp();
        Orders order = ordersMapper.selectById(orderId);
        if(order==null){
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if(order.getStatus() !=OrderStatus.PENDING.getValue()){
            log.info("订单已处理，跳过：orderId={},status={}"
                    ,orderId,OrderStatus.getDescByValue(order.getStatus()));
            return;
        }
        if(expireTimestamp>System.currentTimeMillis()){
            log.error("消息提前送达，expireTimestamp={}",expireTimestamp);
            throw new BusinessException(ResultCode.PREMATURE_DELIVERY);
        }
        if(order.getExpireTime().isAfter(LocalDateTime.now())){
            throw new BusinessException(ResultCode.ORDER_NOT_EXPIRE);
        }
        Book book = bookMapper.selectById(bookId);
        if(book==null){
            throw new BusinessException(ResultCode.BOOK_NOT_FOUND);
        }
        book.setStock(book.getStock()+quantity);
        int bookRows = bookMapper.updateById(book);
        if(bookRows==0){
            throw new BusinessException(ResultCode.STOCK_RECOVER_FAIL);
        }
        log.info("库存恢复成功：bookId={},quantity={},newStock={}",
                book.getId(),quantity,book.getStock());
        order.setStatus(OrderStatus.CANCELLED.getValue());
        boolean updated = this.lambdaUpdate()
                .set(Orders::getStatus, OrderStatus.CANCELLED.getValue())
                .eq(Orders::getStatus, OrderStatus.PENDING.getValue())
                .eq(Orders::getId, order.getId()).update();
        if(!updated){
            throw new BusinessException(ResultCode.ORDER_UPDATE_FAIL);
        }
    }

    @Log
    @Override
    public PageResult<OrderListResponse> listOrders(OrderListRequest orderListRequest) {
        String currentUsername = JwtInterceptor.getCurrentUser();
        int page = orderListRequest.getPage() == null ? 1 : orderListRequest.getPage();
        int size = orderListRequest.getSize() == null ? 10 : orderListRequest.getSize();
        if(currentUsername==null){
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        if(!ALLOWED_COLUMNS.contains(orderListRequest.getSortBy())){
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        if(!ALLOWED_ORDERS.contains(orderListRequest.getSortOrder())){
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }


        Page<OrderListResponse> pageParam = new Page<>(page, size);
        IPage<OrderListResponse> pageResult=ordersMapper.selectOrderList(pageParam,orderListRequest,currentUsername);
        if(pageResult==null){
            throw new BusinessException(ResultCode.ORDER_SELECT_FAIL);
        }
        List<OrderListResponse> records = pageResult.getRecords();
        PageResult<OrderListResponse> result=null;

        if(records==null||records.isEmpty()){
            result = PageResult.of(new ArrayList<>(), pageResult.getTotal(), page, size);
        }else{
            records.forEach(item -> {
                        Integer status = item.getStatus();
                        if (status == null) {
                            log.error("状态码为空，status={}", status);
                            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID);
                        }
                        item.setStatusDesc(OrderStatus.getDescByValue(status));
                    });
            result = PageResult.of(records, pageResult.getTotal(), page, size);
        }
        return result;
    }

    @Log
    @Override
    @Transactional
    public void cancelSeckillExpireOrderByOrderTimeMessage(OrderTimeoutMessage orderTimeoutMessage) {
        if(orderTimeoutMessage==null){
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        Long orderId = orderTimeoutMessage.getOrderId();
        Long bookId = orderTimeoutMessage.getBookId();
        Integer quantity = orderTimeoutMessage.getQuantity();
        Long expireTimestamp = orderTimeoutMessage.getExpireTimestamp();
        Orders order = ordersMapper.selectById(orderId);
        if(order==null){
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        Integer orderType = order.getOrderType();
        Integer messageOrderType = orderTimeoutMessage.getOrderType();
        if(orderType==null||messageOrderType==null||
                orderType != OrderType.SECKILL.getCode()||
                messageOrderType !=OrderType.SECKILL.getCode()){
            log.error("orderType={}",OrderType.getDescByCode(orderType));
            throw new BusinessException(ResultCode.ORDER_TYPE_ERROR);
        }
        if(order.getStatus() !=OrderStatus.PENDING.getValue()){
            log.info("订单已处理，跳过：orderId={},status={}"
                    ,orderId,OrderStatus.getDescByValue(order.getStatus()));
            return;
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expireTimestamp),
                ZoneId.systemDefault() // 用系统默认时区
        );
        long now = System.currentTimeMillis();
        if(expireTimestamp> now + messageProperties.getTimeToleranceMs().toMillis()){
            log.error("消息提前送达，expireTimestamp={}",expireTimestamp);
            LocalDateTime expireTimestampToTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(expireTimestamp),
                    ZoneId.systemDefault() // 用系统默认时区
            );
            LocalDateTime nowToTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(expireTimestamp),
                    ZoneId.systemDefault() // 用系统默认时区
            );
            log.info("expireTimestampToTime={},expireTime={},now={}",expireTimestampToTime,order.getExpireTime(),nowToTime);
            throw new BusinessException(ResultCode.PREMATURE_DELIVERY);
        }
        if(order.getExpireTime().isAfter(LocalDateTime.now().plus(messageProperties.getTimeToleranceMs()))){
            throw new BusinessException(ResultCode.ORDER_NOT_EXPIRE);
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
        boolean updated = this.lambdaUpdate()
                .set(Orders::getStatus, OrderStatus.EXPIRED.getValue())
                .eq(Orders::getStatus, OrderStatus.PENDING.getValue())
                .eq(Orders::getId, order.getId()).update();
        if(!updated){
            throw new BusinessException(ResultCode.ORDER_UPDATE_FAIL);
        }
        redisRollbackService.rollbackRedisSeckillOrThrow(orderTimeoutMessage.getBookId(),orderTimeoutMessage.getUserId(),orderId);
        log.info("秒杀订单超时取消成功：orderId={}, bookId={}, quantity={}",
                orderId, bookId, quantity);
    }
    @Log
    @Override
    @Transactional
    public void cancelSeckillExpireOrderByOrder(Orders order) {
        if(order==null){
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        Integer orderType = order.getOrderType();
        Long orderId = order.getId();
        Long bookId = order.getBookId();
        Long userId = order.getUserId();
        Integer quantity = order.getQuantity();
        if(orderType==null){
            log.error("orderType={}",OrderType.getDescByCode(orderType));
            throw new BusinessException(ResultCode.ORDER_TYPE_ERROR);
        }
        if(order.getStatus() !=OrderStatus.PENDING.getValue()){
            log.info("订单已处理，跳过：orderId={},status={}"
                    ,orderId,OrderStatus.getDescByValue(order.getStatus()));
            return;
        }
        if(order.getExpireTime().isAfter(LocalDateTime.now())){
            throw new BusinessException(ResultCode.ORDER_NOT_EXPIRE);
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
        boolean updated = this.lambdaUpdate()
                .set(Orders::getStatus, OrderStatus.EXPIRED.getValue())
                .eq(Orders::getStatus, OrderStatus.PENDING.getValue())
                .eq(Orders::getId, orderId).update();
        if(!updated){
            throw new BusinessException(ResultCode.ORDER_UPDATE_FAIL);
        }
        redisRollbackService.rollbackRedisSeckillOrThrow(bookId,userId,orderId);
        log.info("秒杀回滚成功");
        log.info("秒杀订单超时取消成功：orderId={}, bookId={}, quantity={}",
                orderId, bookId, quantity);
    }

    /**
     * 生成订单号
     * 格式：yyyyMMddHHmmss + 4位随机数
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 9000) + 1000;
        return timestamp + random;
    }
}





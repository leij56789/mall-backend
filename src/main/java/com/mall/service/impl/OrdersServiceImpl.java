package com.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.enums.OrderStatus;
import com.mall.enums.ResultCode;
import com.mall.dto.request.CreateOrderRequest;
import com.mall.dto.response.CreateOrderResponse;
import com.mall.entity.Book;
import com.mall.entity.Orders;
import com.mall.entity.User;
import com.mall.interceptor.JwtInterceptor;
import com.mall.mapper.BookMapper;
import com.mall.mq.producer.OrderMessageProducer;
import com.mall.service.BookService;
import com.mall.service.OrdersService;
import com.mall.mapper.OrdersMapper;
import com.mall.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
* @author jiaolei
* @description 针对表【orders】的数据库操作Service实现
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
        orders.setExpireTime(LocalDateTime.now().plusMinutes(30));
        orders.setOrderNo(generateOrderNo());
        if(!this.save(orders)){
            throw new BusinessException(ResultCode.ORDER_CREATE_FAIL);
        }
        Orders ordersRes = this.getById(orders.getId());
        if(ordersRes==null){
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        //消息队列处理订单超时
        orderMessageProducer.sendOrderTimeoutMessage(ordersRes.getId());

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

    @Log("订单超时自动取消")
    @Transactional
    @Override
    public void cancelExpireOrder(Long orderId) {
        if(orderId==null){
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        Orders order = ordersMapper.selectById(orderId);
        if(order==null){
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if(order.getStatus()!=OrderStatus.PENDING.getValue()){
            log.info("订单已处理，跳过：orderId={},status={}"
                    ,orderId,OrderStatus.getDescByValue(order.getStatus()));
            return;
        }
        if(order.getExpireTime().isAfter(LocalDateTime.now())){
            throw new BusinessException(ResultCode.ORDER_NOT_EXPIRE);
        }
        Book book = bookMapper.selectById(order.getBookId());
        if(book==null){
            throw new BusinessException(ResultCode.BOOK_NOT_FOUND);
        }
        book.setStock(book.getStock()+order.getQuantity());
        int bookRows = bookMapper.updateById(book);
        if(bookRows==0){
            throw new BusinessException(ResultCode.STOCK_RECOVER_FAIL);
        }
        log.info("库存恢复成功：bookId={},quantity={},newStock={}",
                book.getId(),order.getQuantity(),book.getStock());
        order.setStatus(OrderStatus.CANCELLED.getValue());
        int rows = ordersMapper.updateById(order);
        if(rows==0){
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID);
        }

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





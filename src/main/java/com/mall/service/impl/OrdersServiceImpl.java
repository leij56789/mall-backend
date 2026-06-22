package com.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.BusinessException;
import com.mall.common.OrderStatus;
import com.mall.common.ResultCode;
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
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }
        User currentUser = userService.getByUsernameOrThrow(currentUsername);
        Book book = bookService.getById(bookId);
        if(book==null){
            throw new BusinessException(ResultCode.BOOK_NOT_FOUND);
        }
        //扣减库存
        if(book.getStock()<quantity){
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT);
        }
        book.setStock(book.getStock()-quantity);
        int rows = bookMapper.updateById(book);
        if(rows==0){
            throw new BusinessException(ResultCode.STOCK_DEDUCT_FAILED);
        }
        Orders orders = new Orders();
        orders.setAddress(address);
        orders.setQuantity(quantity);
        orders.setBookId(bookId);
        orders.setStatus(0);
        BigDecimal totalAmount = book.getPrice().multiply(BigDecimal.valueOf(quantity));
        orders.setTotalAmount(totalAmount);
        orders.setUserId(currentUser.getId());
        orders.setExpireTime(LocalDateTime.now().plusMinutes(30));
        orders.setOrderNo(generateOrderNo());
        if(!this.save(orders)){
            throw new BusinessException(ResultCode.ORDER_CREATE_FAILED);
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
        createOrderResponse.setStatusDesc(OrderStatus.getDescByCode(ordersRes.getStatus()));
        return createOrderResponse;
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





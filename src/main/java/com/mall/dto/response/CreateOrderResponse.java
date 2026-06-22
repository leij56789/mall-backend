package com.mall.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jiaolei
 * @date 2026-06-21 14:49
 * @description TODO
 */
@Data
public class CreateOrderResponse {

    /**
     * orderId : 1
     * orderNo : 20260621143025
     * bookId : 1
     * bookName : Java核心技术 卷I
     * quantity : 2
     * totalAmount : 198
     * status : 0
     * statusDesc : 待支付
     * address : 北京市朝阳区xxx街道
     * expireTime : 2026-06-21 15:00:00
     * createdAt : 2026-06-21 14:30:00
     */

    private Long orderId;
    private String orderNo;
    private Long bookId;
    private String bookName;
    private Integer quantity;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private String address;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;


}
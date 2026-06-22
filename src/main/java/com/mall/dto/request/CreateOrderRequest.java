package com.mall.dto.request;

import lombok.Data;

/**
 * @author jiaolei
 * @date 2026-06-21 13:42
 * @description TODO
 */
@Data
public class CreateOrderRequest {

    /**
     * bookId : 1
     * quantity : 2
     * address : 北京市朝阳区xxx街道
     */

    private Long bookId;
    private Integer quantity;
    private String address;
}
package com.mall.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderListResponse {

    private Long id;
    private String orderNo;
    private Long bookId;
    private String bookName;
    private String bookCover;
    private Integer quantity;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private LocalDateTime expireTime;
    private String address;
    private LocalDateTime createdAt;
}
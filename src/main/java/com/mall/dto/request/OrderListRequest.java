package com.mall.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@Data
public class OrderListRequest {

    @Min(1)
    @Max(100)
    private Integer page = 1;

    @Min(1)
    @Max(100)
    private Integer size = 10;

    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String orderNo;

    private String sortBy = "createdAt";

    private String sortOrder = "desc";
}
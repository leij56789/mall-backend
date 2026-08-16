package com.mall.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PageResult<T> {

    private List<T> records;
    private Long total;
    private Integer page;
    private Integer size;
    private Integer pages;

    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
        return PageResult.<T>builder()
                .records(records)
                .total(total)
                .page(page)
                .size(size)
                .pages((int) Math.ceil((double) total / size))
                .build();
    }
}
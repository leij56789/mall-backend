package com.mall.common;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;      // 数据列表
    private Long total;           // 总记录数
    private Integer pageNum;      // 当前页码
    private Integer pageSize;     // 每页条数
    private Integer pages;        // 总页数

    public PageResult() {}

    public PageResult(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = (int) Math.ceil((double) total / pageSize);
    }

    public static <T> PageResult<T> of(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(List.of(), 0L, 1, 10);
    }
}
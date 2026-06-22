package com.mall.dto.request;

/**
 * @author jiaolei
 * @date 2026-06-11 14:11
 * @description TODO
 */

public class ListFeedArticlesRequest {

    /**
     * limit : 10
     * offset : 0
     */

    private int limit;
    private int offset;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
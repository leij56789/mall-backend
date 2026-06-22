package com.mall.dto.request;

/**
 * @author jiaolei
 * @date 2026-06-11 13:54
 * @description TODO
 */

public class ListArticlesRequest {

    /**
     * limit : 20
     * offset : 0
     * tag :
     * author :
     * favorited :
     */

    private int limit;
    private int offset;
    private String tag;
    private String author;
    private String favorited;

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

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getFavorited() {
        return favorited;
    }

    public void setFavorited(String favorited) {
        this.favorited = favorited;
    }
}
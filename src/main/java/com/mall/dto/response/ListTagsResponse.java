package com.mall.dto.response;

import java.util.List;

/**
 * @author jiaolei
 * @date 2026-06-14 16:05
 * @description TODO
 */

public class ListTagsResponse {

    private List<String> tags;

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
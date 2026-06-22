package com.mall.controller;

import com.mall.annotation.Log;
import com.mall.dto.response.ListTagsResponse;
import com.mall.service.ArticleService;
import com.mall.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jiaolei
 * @date 2026-06-14 15:45
 * @description TODO
 */

@RestController
@RequestMapping("/api/tags")
public class TabController {
    @Autowired
    ArticleService articleService;
    @Autowired
    TagService tagService;
    //    @Auth
    @Log("获取标签列表")
    @GetMapping("")
    public ListTagsResponse listTags(){
        return tagService.tagList();
    }
}
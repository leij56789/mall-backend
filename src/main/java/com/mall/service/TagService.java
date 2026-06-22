package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.response.ListTagsResponse;
import com.mall.entity.Tag;

/**
* @author jiaolei
* @description 针对表【tag】的数据库操作Service
* @createDate 2026-06-08 19:43:30
*/
public interface TagService extends IService<Tag> {


    ListTagsResponse tagList();
}

package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.request.CreateArticleRequest;
import com.mall.dto.request.ListArticlesRequest;
import com.mall.dto.request.ListFeedArticlesRequest;
import com.mall.dto.request.UpdateArticleRequest;
import com.mall.dto.response.*;
import com.mall.entity.Article;
import com.mall.mq.message.SeckillMessage;

/**
* @author jiaolei
* @description redis回滚Service
* @createDate 2026-06-08 19:43:30
*/
public interface RedisRollbackService {

    void rollbackRedisSeckill(Long bookId, Long userId);
    void rollbackRedisSeckillOrThrow(Long bookId, Long userId,Long orderId);

    void rollbackRedis(SeckillMessage msg);
}

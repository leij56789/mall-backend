package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.response.SeckillResponse;
import com.mall.entity.SeckillBook;
import com.mall.mq.message.SeckillMessage;

/**
* @author jiaolei
* @description 针对表【seckill_book】的数据库操作Service
* @createDate 2026-06-30 15:42:20
*/
public interface SeckillBookService extends IService<SeckillBook> {

    SeckillResponse seckill(Long bookId, Integer quantity);
    void processSeckillOrder(SeckillMessage msg);
//    void rollbackRedisSeckill(Long bookId, Long userId);
}

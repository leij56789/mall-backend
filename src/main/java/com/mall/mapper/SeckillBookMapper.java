package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.dto.response.SeckillResponse;
import com.mall.entity.SeckillBook;

/**
* @author jiaolei
* @description 针对表【seckill_book】的数据库操作Mapper
* @createDate 2026-06-30 15:42:20
* @Entity generator.entity.SeckillBook
*/
public interface SeckillBookMapper extends BaseMapper<SeckillBook> {

    SeckillResponse selectSeckillBookByBookId(Long bookId);
}





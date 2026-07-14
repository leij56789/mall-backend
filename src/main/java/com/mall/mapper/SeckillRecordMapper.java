package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.SeckillRecord;

/**
* @author jiaolei
* @description 针对表【seckill_record】的数据库操作Mapper
* @createDate 2026-06-30 15:42:20
* @Entity generator.entity.SeckillRecord
*/
public interface SeckillRecordMapper extends BaseMapper<SeckillRecord> {

    Integer insertByUsername(String currentUsername, Long bookId, Integer code);
}





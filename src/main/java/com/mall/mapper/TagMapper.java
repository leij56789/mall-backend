package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @author jiaolei
* @description 针对表【tag】的数据库操作Mapper
* @createDate 2026-06-08 19:43:30
* @Entity com.realworld.blog.entity.Tag
*/
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    List<String> selectTagnameByarticleId(Long articlesDTOId);
}





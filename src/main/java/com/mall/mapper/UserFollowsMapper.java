package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.UserFollows;
import org.apache.ibatis.annotations.Mapper;

/**
* @author jiaolei
* @description 针对表【user_follows】的数据库操作Mapper
* @createDate 2026-06-08 19:43:30
* @Entity com.realworld.blog.entity.UserFollows
*/
@Mapper
public interface UserFollowsMapper extends BaseMapper<UserFollows> {

    Integer selectfollowingCountBycurrentUsernameAndAuthorId(String currentUsername, Long authorId);
}





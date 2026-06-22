package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.UserFavorites;
import org.apache.ibatis.annotations.Mapper;

/**
* @author jiaolei
* @description 针对表【user_favorites】的数据库操作Mapper
* @createDate 2026-06-08 19:43:30
* @Entity com.realworld.blog.entity.UserFavorites
*/
@Mapper
public interface UserFavoritesMapper extends BaseMapper<UserFavorites> {

    Integer selectFavoritedBycurrentUsernameAndarticleId(Long articlesDTOId, String currentUsername);

    Boolean deleteByUsernameAndSlug(String currentUsername, String slug);

    Boolean selectFavoritedBycurrentUsernameAndSlug(String currentUsername, String slug);
}





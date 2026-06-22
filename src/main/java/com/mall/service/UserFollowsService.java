package com.mall.service;

import com.mall.dto.response.UnfollowUserResponse;
import com.mall.dto.response.FollowUserResponse;
import com.mall.dto.response.GetProfileResponse;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.UserFollows;

/**
* @author jiaolei
* @description 针对表【user_follows】的数据库操作Service
* @createDate 2026-06-08 19:43:30
*/
public interface UserFollowsService extends IService<UserFollows> {

    GetProfileResponse getProfile(String username);

    FollowUserResponse followUser(String username);

    UnfollowUserResponse unfollowUser(String username);
}

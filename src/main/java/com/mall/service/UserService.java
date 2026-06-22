package com.mall.service;

import com.mall.dto.request.LoginUserRequest;
import com.mall.dto.request.RegisterUser;
import com.mall.dto.request.UpdateUserRequest;
import com.mall.dto.response.GetUserResponse;
import com.mall.dto.response.LoginUserResponse;
import com.mall.dto.response.RegisterUserResponse;
import com.mall.dto.response.UpdateUserResponse;
import com.mall.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author jiaolei
* @description 针对表【user】的数据库操作Service
* @createDate 2026-06-21 17:15:02
*/
public interface UserService extends IService<User> {
    User getByUsernameOrThrow(String username);

    LoginUserResponse loginUser(LoginUserRequest loginUserRequest);

    RegisterUserResponse registerUser(RegisterUser registerUser);

    GetUserResponse getUser();

    UpdateUserResponse updateUser(UpdateUserRequest updateUserRequest);
}

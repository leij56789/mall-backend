package com.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.BusinessException;
import com.mall.enums.ResultCode;
import com.mall.dto.request.LoginUserRequest;
import com.mall.dto.request.RegisterUser;
import com.mall.dto.request.UpdateUserRequest;
import com.mall.dto.response.GetUserResponse;
import com.mall.dto.response.LoginUserResponse;
import com.mall.dto.response.RegisterUserResponse;
import com.mall.dto.response.UpdateUserResponse;
import com.mall.entity.User;
import com.mall.interceptor.JwtInterceptor;
import com.mall.service.UserService;
import com.mall.mapper.UserMapper;
import com.mall.utils.CacheHelper;
import com.mall.utils.JwtUtil;
import com.mall.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
* @author jiaolei
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-06-21 17:15:02
*/
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{


    @Override
    public User getByUsernameOrThrow(String username) {
        User user = this.lambdaQuery().eq(User::getUsername, username).one();
        if(user==null){
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        return user;
    }

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    private RedisUtil redisUtil;


    @Override
    public LoginUserResponse loginUser(LoginUserRequest loginUserRequest) {
        LoginUserResponse loginUserResponse = new LoginUserResponse();
        loginUserResponse.setUser(new LoginUserResponse.UserBean());

        String email = loginUserRequest.getUser().getEmail();
        String password = loginUserRequest.getUser().getPassword();

        log.info(bCryptPasswordEncoder.encode(password));
        if (StrUtil.isBlank(email) || StrUtil.isBlank(password)) {
            throw new BusinessException("email or password is empty");
        }


        User user = this.lambdaQuery().eq(User::getEmail, email).one();
        if(user==null){
            throw new BusinessException("user not found");
        }
        if(user==null||!bCryptPasswordEncoder.matches(password, user.getPassword())){
            log.info(bCryptPasswordEncoder.encode(password));
            log.info(user.getPassword());
            throw new BusinessException("email or password is invalid");
        }

        //
        String username = user.getUsername();
        String token = jwtUtil.generateToken(username);
        String cacheKey="user:"+username;
        CacheHelper.put(cacheKey,user);


        BeanUtils.copyProperties(user, loginUserResponse.getUser());
        loginUserResponse.getUser().setToken(token);
        return loginUserResponse;
    }

    @Override
    public RegisterUserResponse registerUser(RegisterUser registerUser) {
        String username = registerUser.getUser().getUsername();
        String email = registerUser.getUser().getEmail();
        String password = registerUser.getUser().getPassword();
        if(StrUtil.isBlank(username)||StrUtil.isBlank(email)||StrUtil.isBlank(password)){
            throw new BusinessException("username or email or password is empty");
        }

        if(this.lambdaQuery().eq(User::getUsername,username).exists()){
            throw new BusinessException("username already exists");
        }
        if(this.lambdaQuery().eq(User::getEmail,email).exists()){
            throw new BusinessException("email already exists");
        }
        String encodePassword = bCryptPasswordEncoder.encode(password);
        User user = new User();
        BeanUtils.copyProperties(registerUser.getUser(),user);
        user.setPassword(encodePassword);
        int inserted = userMapper.insert(user);
        if(inserted==0){
            throw new BusinessException("insert failed");
        }
        user=this.lambdaQuery().eq(User::getUsername,username).one();
        CacheHelper.put("user:"+username,user);

        String token = jwtUtil.generateToken(registerUser.getUser().getUsername());
        RegisterUserResponse registerUserResponse = new RegisterUserResponse();
        registerUserResponse.setUser(new RegisterUserResponse.UserBean());
        BeanUtils.copyProperties(user, registerUserResponse.getUser());
        registerUserResponse.getUser().setToken(token);
        return registerUserResponse;
    }


    @Override
    public GetUserResponse getUser() {
        String username = JwtInterceptor.getCurrentUser();
        String token = JwtInterceptor.getCurrentToken();
        if(username==null){
            throw new BusinessException("user not logged in");
        }
        User user = CacheHelper.getOrLoad("user:" + username, () ->
                this.lambdaQuery().eq(User::getUsername, username).one(), User.class);
        if(user==null){
            throw new BusinessException("user not found");
        }

        GetUserResponse getUserResponse = new GetUserResponse();
        getUserResponse.setUser(new GetUserResponse.UserBean());
        BeanUtils.copyProperties(user, getUserResponse.getUser());
        getUserResponse.getUser().setToken(token);

        return getUserResponse;
    }

    @Override
    public UpdateUserResponse updateUser(UpdateUserRequest updateUserRequest) {
        String bio = updateUserRequest.getUser().getBio();
        String image = updateUserRequest.getUser().getImage();
        String email = updateUserRequest.getUser().getEmail();
        String username = JwtInterceptor.getCurrentUser();
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(StrUtil.isNotBlank(bio),User::getBio,bio);
        wrapper.set(StrUtil.isNotBlank(image),User::getImage,image);

        if(StrUtil.isBlank(username)){
            throw new BusinessException("user not logged in");
        }
        User oldUser = this.lambdaQuery().eq(User::getUsername, username).one();
        if(oldUser==null){
            throw new BusinessException("user not found");
        }
        if(StrUtil.isNotBlank(email)){
            if(this.lambdaQuery().eq(User::getEmail,email).ne(User::getUsername,username).exists()){
                throw new BusinessException("email already exist");
            }
            wrapper.set(User::getEmail,email);
        }
        wrapper.eq(User::getUsername,username);
        boolean updated = this.update(wrapper);
        if(!updated){
            throw new BusinessException("update failed");
        }
        User user = this.lambdaQuery().eq(User::getUsername, username).one();
        if(user==null){
            throw new BusinessException("user not found");
        }
        CacheHelper.put("user:"+user.getUsername(),user);

        UpdateUserResponse updateUserResponse = new UpdateUserResponse();
        updateUserResponse.setUser(new UpdateUserResponse.UserBean());
        BeanUtils.copyProperties(user, updateUserResponse.getUser());
        updateUserResponse.getUser().setToken(JwtInterceptor.getCurrentToken());
        return updateUserResponse;
    }
}





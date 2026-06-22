package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.Cart;
import com.mall.service.CartService;
import com.mall.mapper.CartMapper;
import org.springframework.stereotype.Service;

/**
* @author jiaolei
* @description 针对表【cart】的数据库操作Service实现
* @createDate 2026-06-21 17:15:02
*/
@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart>
    implements CartService{

}





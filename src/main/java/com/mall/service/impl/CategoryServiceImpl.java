package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.Category;
import com.mall.service.CategoryService;
import com.mall.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

/**
* @author jiaolei
* @description 针对表【category】的数据库操作Service实现
* @createDate 2026-06-21 17:15:02
*/
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category>
    implements CategoryService{

}





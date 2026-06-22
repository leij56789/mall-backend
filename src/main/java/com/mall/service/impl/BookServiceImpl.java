package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.Book;
import com.mall.service.BookService;
import com.mall.mapper.BookMapper;
import org.springframework.stereotype.Service;

/**
* @author jiaolei
* @description 针对表【book】的数据库操作Service实现
* @createDate 2026-06-21 17:15:02
*/
@Service
public class BookServiceImpl extends ServiceImpl<BookMapper, Book>
    implements BookService{

}





package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.dto.response.CreateCommentResponse;
import com.mall.dto.response.ListCommentsResponse;
import com.mall.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @author jiaolei
* @description 针对表【comment】的数据库操作Mapper
* @createDate 2026-06-08 19:43:30
* @Entity com.realworld.blog.entity.Comment
*/
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    CreateCommentResponse.CommentBean getCommentBean(String currentUsername, String slug);

    Boolean insertCommentBySlugAndUsernameAndBody(String slug, String currentUsername, String body);

    CreateCommentResponse.CommentBean getCommentBeanByCommentId(Long id);

    List<ListCommentsResponse.CommentsBean> getCommentBeanBySlug(String slug);

    List<ListCommentsResponse.CommentsBean> getCommentBeanByArticleId(Long id);
}





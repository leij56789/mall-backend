package com.mall.controller;

import com.mall.annotation.Log;
import com.mall.dto.request.CreateCommentRequest;
import com.mall.dto.response.DeleteCommentResponse;
import com.mall.dto.response.CreateCommentResponse;
import com.mall.dto.response.ListCommentsResponse;
import com.mall.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * @author jiaolei
 * @date 2026-06-14 16:31
 * @description TODO
 */
@RestController
@RequestMapping("/api")
public class CommentController {

    @Autowired
    CommentService commentService;

    @Log("获取文章评论列表")
    @GetMapping("/articles/{slug}/comments")
    public ListCommentsResponse listComments(@PathVariable String slug){
        return commentService.listComments(slug);
    }
    @Log("添加评论")
    @PostMapping("/articles/{slug}/comments")
    public CreateCommentResponse createComment(@RequestBody CreateCommentRequest createCommentRequest, @PathVariable  String slug){
        return commentService.createComment(createCommentRequest,slug);
    }
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Log("删除评论")
    @DeleteMapping("/articles/{slug}/comments/{id}")
    public DeleteCommentResponse deleteComment(@PathVariable String slug, @PathVariable Long id){
        return commentService.deleteComment(slug,id);
    }
}
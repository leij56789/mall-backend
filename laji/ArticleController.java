package com.mall.controller;

import com.mall.annotation.Auth;
import com.mall.annotation.Log;
import com.mall.dto.request.ListFeedArticlesRequest;
import com.mall.dto.request.ListArticlesRequest;
import com.mall.dto.request.CreateArticleRequest;
import com.mall.dto.request.UpdateArticleRequest;
import com.mall.dto.response.*;
import com.mall.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * @author jiaolei
 * @date 2026-06-11 13:06
 * @description TODO
 */
@RestController
@RequestMapping("api/articles")
public class ArticleController {
    @Autowired
    ArticleService articleService;

    @Auth
    @Log("创建文章")
    @PostMapping("")
    public CreateArticleResponse createArticle(@RequestBody CreateArticleRequest articlesPostRequest){
        return articleService.createArticle(articlesPostRequest);
    }
//    @Auth
    @Log("获取单片文章")
    @GetMapping("/{slug}")
    public GetArticleResponse getArticle(@PathVariable String slug){
        return articleService.getArticle(slug);
    }
//    @Auth
    @Log("更新文章")
    @PutMapping("/{slug}")
    public UpdateArticleResponse updateArticle(@RequestBody UpdateArticleRequest updateArticleRequest
    , @PathVariable String slug){
        return articleService.updateArticle(updateArticleRequest,slug);
    }
//    @Auth
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Log("删除文章")
    @DeleteMapping("/{slug}")
    public DeleteArticleResponse deleteArticle(@PathVariable String slug){
        return articleService.deleteArticle(slug);
    }
//    @Auth
    @Log("获取文章列表")
    @GetMapping("")
    public ListArticlesResponse listArticles(ListArticlesRequest listArticlesRequest){
        return articleService.listArticles(listArticlesRequest);
    }
//    @Auth
    @Log("获取Feed文章")
    @GetMapping("/feed")
    public ListFeedArticlesResponse listFeedArticles(ListFeedArticlesRequest listFeedArticlesRequest){
        return articleService.listFeedArticles(listFeedArticlesRequest);
    }
//    @Auth
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Log("点赞文章")
    @PostMapping("/{slug}/favorite")
    public FavoriteArticleResponse favoriteArticle(@PathVariable String slug){
        return articleService.favoriteArticle(slug);
    }
//    @Auth
    @Log("取消点赞")
    @DeleteMapping("/{slug}/favorite")
    public UnfavoriteArticleResponse unfavoriteArticle(@PathVariable String slug){
        return articleService.unfavoriteArticle(slug);
    }

}
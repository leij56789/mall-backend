package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.request.ListFeedArticlesRequest;
import com.mall.dto.request.ListArticlesRequest;
import com.mall.dto.request.CreateArticleRequest;
import com.mall.dto.request.UpdateArticleRequest;
import com.mall.dto.response.*;
import com.mall.entity.Article;

/**
* @author jiaolei
* @description 针对表【article】的数据库操作Service
* @createDate 2026-06-08 19:43:30
*/
public interface ArticleService extends IService<Article> {

    CreateArticleResponse createArticle(CreateArticleRequest articlesPostRequest);

    GetArticleResponse getArticle(String slug);

    ListArticlesResponse listArticles(ListArticlesRequest listArticlesRequest);

    UpdateArticleResponse updateArticle(UpdateArticleRequest updateArticleRequest, String slug);

    DeleteArticleResponse deleteArticle(String slug);

    FavoriteArticleResponse favoriteArticle(String slug);

    UnfavoriteArticleResponse unfavoriteArticle(String slug);

    ListFeedArticlesResponse listFeedArticles(ListFeedArticlesRequest listFeedArticlesRequest);
}

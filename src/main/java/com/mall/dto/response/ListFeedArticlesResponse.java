package com.mall.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jiaolei
 * @date 2026-06-11 14:13
 * @description TODO
 */
@Data
public class ListFeedArticlesResponse {
        private List<ListFeedArticlesResponse.ArticleBean> articles;
        private Integer articlesCount;

        @Data
        public static class ArticleBean {
            private Long id;

            private String slug;
            private String title;
            private String description;
            private String body;
            private List<String> tagList;
            private LocalDateTime createdAt;
            private LocalDateTime updatedAt;
            private Boolean favorited=false;
            private Integer favoritesCount;
            private AuthorBean author;

            @Data
            public static class AuthorBean {
                private String username;
                private String bio;
                private String image;
                private Boolean following=true;
            }
        }

}
package com.mall.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jiaolei
 * @date 2026-06-11 14:16
 * @description TODO
 */

@Data
public class FavoriteArticleResponse {

    private FavoriteArticleResponse.ArticleDTO article;

    @Data
    public static class ArticleDTO {
        private String slug;
        private String title;
        private String description;
        private String body;
        private List<String> tagList;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Boolean favorited;
        private Integer favoritesCount;
        private ArticleDTO.AuthorDTO author;

        @Data
        public static class AuthorDTO {
            private String username;
            private String bio;
            private String image;
            private Boolean following;
        }
    }
}
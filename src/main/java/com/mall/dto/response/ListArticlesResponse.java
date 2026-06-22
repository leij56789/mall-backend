package com.mall.dto.response;

/**
 * @author jiaolei
 * @date 2026-06-11 13:59
 * @description TODO
 */

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表响应实体
 */
@Data
public class ListArticlesResponse {
    private List<ListArticlesResponse.ArticlesDTO> articles;
    private Integer articlesCount;

    @Data
    public static class ArticlesDTO {
        private Long id;
        private Long authorId;

        private String slug;
        private String title;
        private String description;
        private String body;
        private List<String> tagList;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Boolean favorited=false;
        private Integer favoritesCount;
        private ArticlesDTO.AuthorDTO author;

        @Data
        public static class AuthorDTO {
            private String username;
            private String bio;
            private String image;
            private Boolean following=false;
        }
    }
}
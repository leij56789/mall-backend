package com.mall.dto.request;

/**
 * @author jiaolei
 * @date 2026-06-11 13:51
 * @description TODO
 */

public class UpdateArticleRequest {

    /**
     * article : {"title":"How to train your dragon 2","description":"The sequel","body":"New content here"}
     */

    private ArticleBean article;

    public ArticleBean getArticle() {
        return article;
    }

    public void setArticle(ArticleBean article) {
        this.article = article;
    }

    public static class ArticleBean {
        /**
         * title : How to train your dragon 2
         * description : The sequel
         * body : New content here
         */

        private String title;
        private String description;
        private String body;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
package com.mall.dto.request;

import java.util.List;

/**
 * @author jiaolei
 * @date 2026-06-11 13:49
 * @description TODO
 */

public class CreateArticleRequest {

    /**
     * article : {"slug":"how-to-train-your-dragon","title":"How to train your dragon","description":"Ever wonder how?","body":"You have to believe","tagList":["dragons","training"],"createdAt":"2026-06-11T10:00:00.000Z","updatedAt":"2026-06-11T10:00:00.000Z","favorited":false,"favoritesCount":0,"author":{"username":"johnjacob1","bio":"I love coding!","image":null,"following":false}}
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
         * slug : how-to-train-your-dragon
         * title : How to train your dragon
         * description : Ever wonder how?
         * body : You have to believe
         * tagList : ["dragons","training"]
         * createdAt : 2026-06-11T10:00:00.000Z
         * updatedAt : 2026-06-11T10:00:00.000Z
         * favorited : false
         * favoritesCount : 0
         * author : {"username":"johnjacob1","bio":"I love coding!","image":null,"following":false}
         */

        private String slug;
        private String title;
        private String description;
        private String body;
        private String createdAt;
        private String updatedAt;
        private boolean favorited;
        private int favoritesCount;
        private AuthorBean author;
        private List<String> tagList;

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

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

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }

        public boolean isFavorited() {
            return favorited;
        }

        public void setFavorited(boolean favorited) {
            this.favorited = favorited;
        }

        public int getFavoritesCount() {
            return favoritesCount;
        }

        public void setFavoritesCount(int favoritesCount) {
            this.favoritesCount = favoritesCount;
        }

        public AuthorBean getAuthor() {
            return author;
        }

        public void setAuthor(AuthorBean author) {
            this.author = author;
        }

        public List<String> getTagList() {
            return tagList;
        }

        public void setTagList(List<String> tagList) {
            this.tagList = tagList;
        }

        public static class AuthorBean {
            /**
             * username : johnjacob1
             * bio : I love coding!
             * image : null
             * following : false
             */

            private String username;
            private String bio;
            private Object image;
            private boolean following;

            public String getUsername() {
                return username;
            }

            public void setUsername(String username) {
                this.username = username;
            }

            public String getBio() {
                return bio;
            }

            public void setBio(String bio) {
                this.bio = bio;
            }

            public Object getImage() {
                return image;
            }

            public void setImage(Object image) {
                this.image = image;
            }

            public boolean isFollowing() {
                return following;
            }

            public void setFollowing(boolean following) {
                this.following = following;
            }
        }
    }
}
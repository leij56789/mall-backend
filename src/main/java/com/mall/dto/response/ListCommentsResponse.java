package com.mall.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jiaolei
 * @date 2026-06-14 16:41
 * @description TODO
 */
@Data
public class ListCommentsResponse {

    private List<CommentsBean> comments;


    @Data
    public static class CommentsBean {
        /**
         * id : 1
         * createdAt : 2026-06-14T10:00:00.000Z
         * updatedAt : 2026-06-14T10:00:00.000Z
         * body : Great article!
         * author : {"username":"alice","bio":"Frontend developer","image":null,"following":false}
         */

        private Long articleAuthorId;

        private Long id;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String body;
        private AuthorBean author;

//        public int getId() {
//            return id;
//        }
//
//        public void setId(int id) {
//            this.id = id;
//        }
//
//        public String getCreatedAt() {
//            return createdAt;
//        }
//
//        public void setCreatedAt(String createdAt) {
//            this.createdAt = createdAt;
//        }
//
//        public String getUpdatedAt() {
//            return updatedAt;
//        }
//
//        public void setUpdatedAt(String updatedAt) {
//            this.updatedAt = updatedAt;
//        }
//
//        public String getBody() {
//            return body;
//        }
//
//        public void setBody(String body) {
//            this.body = body;
//        }
//
//        public AuthorBean getAuthor() {
//            return author;
//        }
//
//        public void setAuthor(AuthorBean author) {
//            this.author = author;
//        }

        @Data
        public static class AuthorBean {
            /**
             * username : alice
             * bio : Frontend developer
             * image : null
             * following : false
             */

            private Long userId;

            private String username;
            private String bio;
            private Object image;
            private boolean following=false;

//            public String getUsername() {
//                return username;
//            }
//
//            public void setUsername(String username) {
//                this.username = username;
//            }
//
//            public String getBio() {
//                return bio;
//            }
//
//            public void setBio(String bio) {
//                this.bio = bio;
//            }
//
//            public Object getImage() {
//                return image;
//            }
//
//            public void setImage(Object image) {
//                this.image = image;
//            }
//
//            public boolean isFollowing() {
//                return following;
//            }
//
//            public void setFollowing(boolean following) {
//                this.following = following;
//            }
        }
    }
}
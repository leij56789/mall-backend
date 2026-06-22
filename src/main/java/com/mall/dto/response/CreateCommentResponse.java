package com.mall.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author jiaolei
 * @date 2026-06-14 16:58
 * @description TODO
 */
@Data
public class CreateCommentResponse {

    /**
     * comment : {"id":3,"createdAt":"2026-06-14T12:00:00.000Z","updatedAt":"2026-06-14T12:00:00.000Z","body":"This is a great article!","author":{"username":"alice","bio":"Frontend developer","image":null,"following":false}}
     */

    private CommentBean comment;

    @Data
    public static class CommentBean {
        /**
         * id : 3
         * createdAt : 2026-06-14T12:00:00.000Z
         * updatedAt : 2026-06-14T12:00:00.000Z
         * body : This is a great article!
         * author : {"username":"alice","bio":"Frontend developer","image":null,"following":false}
         */

        private Long articleAuthorId;

        private Long id;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String body;
        private CommentBean.AuthorBean author;


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
            private String image;
            private Boolean following;


        }
    }
}
package com.mall.dto.request;

/**
 * @author jiaolei
 * @date 2026-06-14 16:59
 * @description TODO
 */

public class CreateCommentRequest {

    /**
     * comment : {"body":"This is a great article!"}
     */

    private CommentBean comment;

    public CommentBean getComment() {
        return comment;
    }

    public void setComment(CommentBean comment) {
        this.comment = comment;
    }

    public static class CommentBean {
        /**
         * body : This is a great article!
         */

        private String body;

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
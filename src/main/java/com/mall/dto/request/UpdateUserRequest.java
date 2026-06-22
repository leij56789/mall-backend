package com.mall.dto.request;

/**
 * @author jiaolei
 * @date 2026-06-10 11:27
 * @description TODO
 */

public class UpdateUserRequest {

    /**
     * user : {"email":"jake@jake.jake","bio":"I like to skateboard","image":"https://i.stack.imgur.com/xHWG8.jpg"}
     */

    private UserBean user;

    public UserBean getUser() {
        return user;
    }

    public void setUser(UserBean user) {
        this.user = user;
    }

    public static class UserBean {
        /**
         * email : jake@jake.jake
         * bio : I like to skateboard
         * image : https://i.stack.imgur.com/xHWG8.jpg
         */

        private String email;
        private String bio;
        private String image;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getBio() {
            return bio;
        }

        public void setBio(String bio) {
            this.bio = bio;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }
    }
}
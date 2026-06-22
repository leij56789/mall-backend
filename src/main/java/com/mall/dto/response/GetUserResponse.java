package com.mall.dto.response;

/**
 * @author jiaolei
 * @date 2026-06-10 10:39
 * @description TODO
 */

public class GetUserResponse {

    /**
     * user : {"email":"...","username":"...","bio":"...","image":"...","token":"..."}
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
         * email : ...
         * username : ...
         * bio : ...
         * image : ...
         * token : ...
         */

        private String email;
        private String username;
        private String bio;
        private String image;
        private String token;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

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

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
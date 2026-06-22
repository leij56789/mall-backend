package com.mall.dto.response;

/**
 * @author jiaolei
 * @date 2026-06-09 16:08
 * @description TODO
 */

public class RegisterUserResponse extends Response{

    /**
     * user : {"email":"string","token":"string","username":"string","bio":"string | null","image":"string | null"}
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
         * email : string
         * token : string
         * username : string
         * bio : string | null
         * image : string | null
         */

        private String email;
        private String token;
        private String username;
        private String bio;
        private String image;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
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
    }
}
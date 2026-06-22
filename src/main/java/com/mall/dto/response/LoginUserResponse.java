package com.mall.dto.response;


/**
 * @author jiaolei
 * @date 2026-06-08 19:03
 * @description TODO
 */

public class LoginUserResponse extends Response{

    /**
     * user : {"email":"john@example.com","username":"johnjacob","bio":"I like to skateboard","image":"https://...","token":"eyJhbGciOiJIUzI1NiJ9..."}
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
         * email : john@example.com
         * username : johnjacob
         * bio : I like to skateboard
         * image : https://...
         * token : eyJhbGciOiJIUzI1NiJ9...
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
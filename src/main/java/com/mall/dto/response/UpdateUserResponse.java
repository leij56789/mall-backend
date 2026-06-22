package com.mall.dto.response;

/**
 * @author jiaolei
 * @date 2026-06-10 11:27
 * @description TODO
 */

public class UpdateUserResponse {

    /**
     * user : {"email":"john@example.com","username":"johnjacob","bio":null,"image":null,"token":"eyJhbGciOiJIUzI1NiJ9..."}
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
         * bio : null
         * image : null
         * token : eyJhbGciOiJIUzI1NiJ9...
         */

        private String email;
        private String username;
        private Object bio;
        private Object image;
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

        public Object getBio() {
            return bio;
        }

        public void setBio(Object bio) {
            this.bio = bio;
        }

        public Object getImage() {
            return image;
        }

        public void setImage(Object image) {
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
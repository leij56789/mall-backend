package com.mall.dto.request;

/**
 * @author jiaolei
 * @date 2026-06-09 16:07
 * @description TODO
 */

public class RegisterUser extends Request{

    /**
     * user : {"username":"Jacob","email":"jake@jake.jake","password":"jakejake"}
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
         * username : Jacob
         * email : jake@jake.jake
         * password : jakejake
         */

        private String username;
        private String email;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
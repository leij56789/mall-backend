package com.mall.dto.request;

/**
 * @author jiaolei
 * @date 2026-06-08 19:00
 * @description TODO
 */

public class LoginUserRequest extends Request{

    /**
     * user : {"email":"jake@jake.jake","password":"jakejake"}
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
         * password : jakejake
         */

        private String email;
        private String password;

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
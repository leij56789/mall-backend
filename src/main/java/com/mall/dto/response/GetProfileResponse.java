package com.mall.dto.response;

/**
 * @author jiaolei
 * @date 2026-06-10 15:38
 * @description TODO
 */

public class GetProfileResponse {

    /**
     * profile : {"username":"johnjacob1","bio":"I love coding!","image":null,"following":false}
     */

    private ProfileBean profile;

    public ProfileBean getProfile() {
        return profile;
    }

    public void setProfile(ProfileBean profile) {
        this.profile = profile;
    }

    public static class ProfileBean {
        /**
         * username : johnjacob1
         * bio : I love coding!
         * image : null
         * following : false
         */

        private String username;
        private String bio;
        private Object image;
        private boolean following;

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

        public Object getImage() {
            return image;
        }

        public void setImage(Object image) {
            this.image = image;
        }

        public boolean isFollowing() {
            return following;
        }

        public void setFollowing(boolean following) {
            this.following = following;
        }
    }
}
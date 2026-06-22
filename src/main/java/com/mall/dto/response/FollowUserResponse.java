package com.mall.dto.response;

/**
 * @author jiaolei
 * @date 2026-06-10 18:08
 * @description TODO
 */

public class FollowUserResponse {

    /**
     * profile : {"username":"alice","bio":"Frontend developer, love React","image":"https://api.realworld.io/images/alice.jpg","following":true}
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
         * username : alice
         * bio : Frontend developer, love React
         * image : https://api.realworld.io/images/alice.jpg
         * following : true
         */

        private String username;
        private String bio;
        private String image;
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

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
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
package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 
 * @TableName user_follows
 */
@TableName(value ="user_follows")
@Data
public class UserFollows {
    /**
     * 
     */
    private Long followerId;

    /**
     * 
     */
    private Long followeeId;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        UserFollows other = (UserFollows) that;
        return (this.getFollowerId() == null ? other.getFollowerId() == null : this.getFollowerId().equals(other.getFollowerId()))
            && (this.getFolloweeId() == null ? other.getFolloweeId() == null : this.getFolloweeId().equals(other.getFolloweeId()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getFollowerId() == null) ? 0 : getFollowerId().hashCode());
        result = prime * result + ((getFolloweeId() == null) ? 0 : getFolloweeId().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", followerId=").append(followerId);
        sb.append(", followeeId=").append(followeeId);
        sb.append("]");
        return sb.toString();
    }
}
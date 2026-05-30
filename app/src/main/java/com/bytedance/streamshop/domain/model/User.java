package com.bytedance.streamshop.domain.model;

import java.io.Serializable;

public class User implements Serializable {
    private String id;
    private String username;
    private String avatarUrl;
    private String role;
    private String createdAt;
    private int followers;
    private int following;

    public User() {}

    public User(String id, String username, String avatarUrl, String role) {
        this.id = id;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public int getFollowers() { return followers; }
    public void setFollowers(int followers) { this.followers = followers; }
    public int getFollowing() { return following; }
    public void setFollowing(int following) { this.following = following; }
}

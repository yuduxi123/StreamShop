package com.bytedance.streamshop.domain.model;

import java.io.Serializable;

public class Danmaku implements Serializable {
    private String id;
    private String videoId;
    private String userId;
    private String username;
    private String content;
    private String color;
    private long timestampMs;
    private String createdAt;

    public Danmaku() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

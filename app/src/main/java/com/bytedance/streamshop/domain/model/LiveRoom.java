package com.bytedance.streamshop.domain.model;

import java.util.List;

public class LiveRoom {
    private String id;
    private String title;
    private String coverUrl;
    private String anchorId;
    private String status;
    private String currentProductId;
    private int onlineCount;
    private int likeCount;
    private int viewerCount;
    private String createdAt;
    private User anchor;
    private List<Product> products;

    public LiveRoom() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getAnchorId() { return anchorId; }
    public void setAnchorId(String anchorId) { this.anchorId = anchorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentProductId() { return currentProductId; }
    public void setCurrentProductId(String currentProductId) { this.currentProductId = currentProductId; }
    public int getOnlineCount() { return onlineCount; }
    public void setOnlineCount(int onlineCount) { this.onlineCount = onlineCount; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public int getViewerCount() { return viewerCount; }
    public void setViewerCount(int viewerCount) { this.viewerCount = viewerCount; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public User getAnchor() { return anchor; }
    public void setAnchor(User anchor) { this.anchor = anchor; }
    public List<Product> getProducts() { return products; }
    public void setProducts(List<Product> products) { this.products = products; }
}

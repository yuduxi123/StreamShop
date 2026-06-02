package com.bytedance.streamshop.domain.model;

import java.io.Serializable;
import java.util.Map;

public class FeedItem implements Serializable {
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_LIVE = "live";

    private String type;
    private String id;
    private String createdAt;
    private Video video;
    private Map<String, Object> liveRoom;

    public FeedItem() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Video getVideo() { return video; }
    public void setVideo(Video video) { this.video = video; }

    public Map<String, Object> getLiveRoom() { return liveRoom; }
    public void setLiveRoom(Map<String, Object> liveRoom) { this.liveRoom = liveRoom; }

    public boolean isVideo() { return TYPE_VIDEO.equals(type) && video != null; }
    public boolean isLive() { return TYPE_LIVE.equals(type) && liveRoom != null; }
}

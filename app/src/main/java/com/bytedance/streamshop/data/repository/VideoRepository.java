package com.bytedance.streamshop.data.repository;

import com.bytedance.streamshop.data.remote.ApiResponse;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.Video;

import java.util.ArrayList;
import java.util.List;

public class VideoRepository {
    private final ApiService apiService;
    private final List<Video> videoCache = new ArrayList<>();
    private int currentPage = 0;
    private int totalVideos = 0;
    private boolean hasMore = true;

    public VideoRepository() {
        this.apiService = new ApiService();
    }

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    public void loadVideos(int page, int limit, Callback<List<Video>> callback) {
        new Thread(() -> {
            try {
                ApiResponse<Video> response = apiService.getVideos(page, limit);
                if (page == 1) {
                    videoCache.clear();
                }
                videoCache.addAll(response.getData());
                currentPage = page;
                totalVideos = response.getTotal();
                hasMore = videoCache.size() < totalVideos;
                callback.onSuccess(new ArrayList<>(response.getData()));
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void loadMore(Callback<List<Video>> callback) {
        if (!hasMore) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        loadVideos(currentPage + 1, 10, callback);
    }

    public void refresh(Callback<List<Video>> callback) {
        loadVideos(1, 10, callback);
    }

    public List<Video> getVideoCache() {
        return videoCache;
    }

    public boolean hasMore() {
        return hasMore;
    }
}

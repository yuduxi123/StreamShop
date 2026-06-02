package com.bytedance.streamshop.data.repository;

import com.bytedance.streamshop.data.remote.ApiResponse;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.FeedItem;

import java.util.ArrayList;
import java.util.List;

public class FeedRepository {
    private final ApiService apiService;
    private final List<FeedItem> itemCache = new ArrayList<>();
    private int currentPage = 0;
    private int totalItems = 0;
    private boolean hasMore = true;

    public FeedRepository() {
        this.apiService = new ApiService();
    }

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    public void loadFeed(int page, int limit, Callback<List<FeedItem>> callback) {
        new Thread(() -> {
            try {
                ApiResponse<FeedItem> response = apiService.getFeed(page, limit);
                if (page == 1) {
                    itemCache.clear();
                }
                List<FeedItem> responseData = response.getData() != null ? response.getData() : new ArrayList<>();
                itemCache.addAll(responseData);
                currentPage = page;
                totalItems = response.getTotal();
                hasMore = itemCache.size() < totalItems;
                callback.onSuccess(new ArrayList<>(responseData));
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void loadMore(Callback<List<FeedItem>> callback) {
        if (!hasMore) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        loadFeed(currentPage + 1, 10, callback);
    }

    public void refresh(Callback<List<FeedItem>> callback) {
        loadFeed(1, 10, callback);
    }

    public List<FeedItem> getItemCache() { return itemCache; }

    public boolean hasMore() { return hasMore; }
}

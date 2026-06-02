package com.bytedance.streamshop.ui.feed;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bytedance.streamshop.data.repository.FeedRepository;
import com.bytedance.streamshop.domain.model.FeedItem;

import java.util.List;

public class FeedViewModel extends ViewModel {
    private final MutableLiveData<List<FeedItem>> feedItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final FeedRepository repository;

    public FeedViewModel() {
        this.repository = new FeedRepository();
    }

    public LiveData<List<FeedItem>> getFeedItems() { return feedItems; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadVideos() {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        loading.setValue(true);
        error.setValue(null);
        repository.refresh(new FeedRepository.Callback<List<FeedItem>>() {
            @Override
            public void onSuccess(List<FeedItem> data) {
                loading.postValue(false);
                feedItems.postValue(repository.getItemCache());
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void loadMore() {
        if (Boolean.TRUE.equals(loading.getValue()) || !repository.hasMore()) return;
        loading.setValue(true);
        repository.loadMore(new FeedRepository.Callback<List<FeedItem>>() {
            @Override
            public void onSuccess(List<FeedItem> data) {
                loading.postValue(false);
                feedItems.postValue(repository.getItemCache());
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void retry() {
        loadVideos();
    }
}

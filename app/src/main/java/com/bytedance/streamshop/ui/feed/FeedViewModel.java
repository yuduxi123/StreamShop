package com.bytedance.streamshop.ui.feed;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.data.repository.VideoRepository;
import com.bytedance.streamshop.domain.model.Video;

import java.util.List;

public class FeedViewModel extends ViewModel {
    private final MutableLiveData<List<Video>> videos = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final VideoRepository repository;
    private final ApiService apiService;

    public FeedViewModel() {
        this.repository = new VideoRepository();
        this.apiService = new ApiService();
    }

    public LiveData<List<Video>> getVideos() { return videos; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadVideos() {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        loading.setValue(true);
        error.setValue(null);
        repository.refresh(new VideoRepository.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> data) {
                loading.postValue(false);
                videos.postValue(repository.getVideoCache());
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
        repository.loadMore(new VideoRepository.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> data) {
                loading.postValue(false);
                videos.postValue(repository.getVideoCache());
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

package com.bytedance.streamshop.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.domain.model.Video;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class FeedFragment extends Fragment {
    private ViewPager2 viewPager;
    private RecyclerView recyclerView;
    private CircularProgressIndicator loadingView;
    private TextView errorView;
    private FeedViewModel viewModel;
    private FeedPagerAdapter adapter;
    private List<Video> videos = new ArrayList<>();
    private int currentPosition = 0;
    private int lastPlayingPosition = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupViewModel();
    }

    @Override
    public void onDestroyView() {
        if (adapter != null) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                VideoViewHolder holder = adapter.getViewHolderAt(recyclerView, i);
                if (holder != null) holder.release();
            }
        }
        if (viewPager != null) {
            viewPager.setAdapter(null);
        }
        adapter = null;
        recyclerView = null;
        super.onDestroyView();
    }

    private void initViews(View view) {
        viewPager = view.findViewById(R.id.feed_viewpager);
        loadingView = view.findViewById(R.id.feed_loading);
        errorView = view.findViewById(R.id.feed_error);

        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager.setOffscreenPageLimit(2);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                onFeedPageSelected(position);
            }
        });

        recyclerView = (RecyclerView) viewPager.getChildAt(0);

        if (errorView != null) errorView.setOnClickListener(v -> {
            if (viewModel != null) viewModel.retry();
        });
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(FeedViewModel.class);
        viewModel.getVideos().observe(getViewLifecycleOwner(), videoList -> {
            if (videoList == null || videoList.isEmpty()) return;
            if (adapter == null) {
                videos = videoList;
                adapter = new FeedPagerAdapter();
                adapter.setFragmentManager(getChildFragmentManager());
                viewPager.setAdapter(adapter);
                adapter.setVideos(videos);
            } else if (videoList.size() > videos.size()) {
                List<Video> newVideos = videoList.subList(videos.size(), videoList.size());
                videos = videoList;
                adapter.addVideos(newVideos);
                return;
            } else {
                videos = videoList;
                adapter.setVideos(videos);
            }

            currentPosition = Math.min(currentPosition, videos.size() - 1);
            viewPager.setCurrentItem(currentPosition, false);

            loadingView.setVisibility(View.GONE);
            errorView.setVisibility(View.GONE);

            viewPager.post(() -> {
                VideoViewHolder holder = adapter.getViewHolderAt(recyclerView, currentPosition);
                if (holder != null) holder.play();
                lastPlayingPosition = currentPosition;
            });
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loadingView == null) return;
            if (loading != null && loading && videos.isEmpty()) {
                loadingView.setVisibility(View.VISIBLE);
            } else {
                loadingView.setVisibility(View.GONE);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (loadingView != null) loadingView.setVisibility(View.GONE);
            if (errorView == null) return;
            if (error != null && videos.isEmpty()) {
                errorView.setVisibility(View.VISIBLE);
                errorView.setText("Load failed, tap to retry");
            } else {
                errorView.setVisibility(View.GONE);
            }
        });

        viewModel.loadVideos();
    }

    private void onFeedPageSelected(int position) {
        // Pause previous video
        if (lastPlayingPosition >= 0 && lastPlayingPosition != position) {
            VideoViewHolder prev = adapter.getViewHolderAt(recyclerView, lastPlayingPosition);
            if (prev != null) prev.pause();
        }
        // Play current video
        VideoViewHolder current = adapter.getViewHolderAt(recyclerView, position);
        if (current != null) current.play();
        lastPlayingPosition = position;
        currentPosition = position;

        if (position >= videos.size() - 2) {
            viewModel.loadMore();
        }
    }

    private void pauseCurrentVideo() {
        if (adapter == null || recyclerView == null) return;
        VideoViewHolder holder = adapter.getViewHolderAt(recyclerView, currentPosition);
        if (holder != null) holder.pause();
    }

    private void resumeCurrentVideo() {
        if (adapter == null || recyclerView == null) return;
        VideoViewHolder holder = adapter.getViewHolderAt(recyclerView, currentPosition);
        if (holder != null) holder.play();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (videos.isEmpty()) {
            viewModel.loadVideos();
        } else {
            resumeCurrentVideo();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseCurrentVideo();
    }
}

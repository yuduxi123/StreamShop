package com.bytedance.streamshop.ui.feed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.domain.model.FeedItem;
import com.bytedance.streamshop.ui.live.LiveRoomActivity;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FeedFragment extends Fragment {
    private ViewPager2 viewPager;
    private RecyclerView recyclerView;
    private CircularProgressIndicator loadingView;
    private TextView errorView;
    private FeedViewModel viewModel;
    private HomeStateViewModel homeStateViewModel;
    private FeedPagerAdapter adapter;
    private List<FeedItem> feedItems = new ArrayList<>();
    private int currentPosition = 0;
    private int lastPlayingPosition = -1;
    private boolean wasPlayingBeforePause = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        homeStateViewModel = new ViewModelProvider(requireActivity()).get(HomeStateViewModel.class);
        initViews(view);
        setupViewModel();
    }

    @Override
    public void onDestroyView() {
        if (homeStateViewModel != null) {
            homeStateViewModel.setFeedPosition(currentPosition);
        }
        // Phase 2: save current player, release the rest
        if (homeStateViewModel != null && adapter != null && recyclerView != null
                && currentPosition >= 0 && currentPosition < feedItems.size()) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                RecyclerView.ViewHolder holder = adapter.getViewHolderAt(recyclerView, i);
                if (holder instanceof VideoViewHolder) {
                    VideoViewHolder vvh = (VideoViewHolder) holder;
                    if (i == currentPosition && vvh.getVideo() != null) {
                        long pos = vvh.getCurrentPositionMs();
                        boolean wasPlaying = vvh.isCurrentlyPlaying();
                        if (wasPlaying) {
                            wasPlayingBeforePause = true;
                        }
                        ExoPlayer saved = vvh.takePlayer();
                        homeStateViewModel.savePlayer(saved, vvh.getVideo().getId(), pos);
                        homeStateViewModel.saveVideoPlaybackState(
                                vvh.getVideo().getId(), pos, wasPlayingBeforePause);
                    } else {
                        vvh.release();
                    }
                }
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
        if (recyclerView != null) {
            recyclerView.setClipChildren(true);
        }

        if (errorView != null) errorView.setOnClickListener(v -> {
            if (viewModel != null) viewModel.retry();
        });
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(FeedViewModel.class);
        viewModel.getFeedItems().observe(getViewLifecycleOwner(), itemList -> {
            if (itemList == null || itemList.isEmpty()) return;
            if (adapter == null) {
                feedItems = itemList;
                adapter = new FeedPagerAdapter();
                adapter.setFragmentManager(getChildFragmentManager());
                adapter.setPlaybackModeListener(() -> {
                    int nextPos = currentPosition + 1;
                    if (nextPos < feedItems.size()) {
                        viewPager.setCurrentItem(nextPos, true);
                    }
                });
                adapter.setLiveCardClickListener(new LiveFeedCardViewHolder.OnLiveCardClickListener() {
                    @Override
                    public void onLiveCardClick(Map<String, Object> room) {
                        openLiveRoom(room);
                    }

                    @Override
                    public void onProductReviewClick(String productId) {
                        ProductReviewsBottomSheet.newInstance(productId)
                                .show(getChildFragmentManager(), "product_reviews");
                    }
                });
                viewPager.setAdapter(adapter);

                // Wire restore info from ViewModel before binding
                int savedPosition = homeStateViewModel != null
                        ? homeStateViewModel.getFeedPosition()
                        : currentPosition;
                currentPosition = Math.min(savedPosition, feedItems.size() - 1);
                FeedItem restoreItem = feedItems.get(currentPosition);
                if (restoreItem != null && restoreItem.getVideo() != null
                        && homeStateViewModel != null) {
                    // Phase 2: reattach saved player (no cover flash)
                    if (homeStateViewModel.hasSavedPlayer()
                            && restoreItem.getVideo().getId().equals(
                                    homeStateViewModel.getSavedPlayerVideoId())) {
                        adapter.setSavedPlayer(
                                homeStateViewModel.takePlayer(restoreItem.getVideo().getId()),
                                restoreItem.getVideo().getId(),
                                homeStateViewModel.getSavedPlayerPositionMs());
                    }
                    // Phase 1 fallback: seek-based recovery
                    else if (restoreItem.getVideo().getId().equals(
                            homeStateViewModel.getSavedVideoId())) {
                        adapter.setRestoreInfo(
                                restoreItem.getVideo().getId(),
                                homeStateViewModel.getSavedPlaybackPositionMs());
                    }
                }

                adapter.setItems(feedItems);
            } else if (itemList.size() > feedItems.size()) {
                List<FeedItem> newItems = itemList.subList(feedItems.size(), itemList.size());
                feedItems = itemList;
                adapter.addItems(newItems);
                return;
            } else if (itemList.size() == feedItems.size()) {
                // Same size = same feed, skip rebind to preserve external player
                return;
            } else {
                feedItems = itemList;
                adapter.setItems(feedItems);
            }

            viewPager.setCurrentItem(currentPosition, false);

            loadingView.setVisibility(View.GONE);
            errorView.setVisibility(View.GONE);

            viewPager.post(() -> {
                if (homeStateViewModel != null && !homeStateViewModel.isSavedPlaying()) {
                    lastPlayingPosition = currentPosition;
                    return;
                }
                playHolderAt(currentPosition);
                lastPlayingPosition = currentPosition;
            });
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loadingView == null) return;
            if (loading != null && loading && feedItems.isEmpty()) {
                loadingView.setVisibility(View.VISIBLE);
            } else {
                loadingView.setVisibility(View.GONE);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (loadingView != null) loadingView.setVisibility(View.GONE);
            if (errorView == null) return;
            if (error != null && feedItems.isEmpty()) {
                errorView.setVisibility(View.VISIBLE);
                errorView.setText("Load failed, tap to retry");
            } else {
                errorView.setVisibility(View.GONE);
            }
        });

        viewModel.loadVideos();
    }

    private void openLiveRoom(Map<String, Object> room) {
        if (room == null || getActivity() == null) return;
        Object id = room.get("id");
        String roomId = id != null ? String.valueOf(id) : "";
        if (roomId.isEmpty()) return;

        Intent intent = new Intent(getActivity(), LiveRoomActivity.class);
        intent.putExtra("room_id", roomId);
        startActivity(intent);
    }

    private void onFeedPageSelected(int position) {
        if (adapter == null) return;
        if (lastPlayingPosition >= 0 && lastPlayingPosition != position) {
            pauseHolderAt(lastPlayingPosition);
        }
        playHolderAt(position);
        lastPlayingPosition = position;
        currentPosition = position;
        if (homeStateViewModel != null) {
            homeStateViewModel.setFeedPosition(position);
        }

        if (position >= feedItems.size() - 2) {
            viewModel.loadMore();
        }
    }

    private void pauseCurrentVideo() {
        if (adapter == null || recyclerView == null) return;
        pauseHolderAt(currentPosition);
    }

    private void resumeCurrentVideo() {
        if (adapter == null || recyclerView == null) return;
        playHolderAt(currentPosition);
    }

    private void playHolderAt(int position) {
        if (adapter == null || recyclerView == null) return;
        RecyclerView.ViewHolder holder = adapter.getViewHolderAt(recyclerView, position);
        if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).play();
        }
    }

    private void pauseHolderAt(int position) {
        if (adapter == null || recyclerView == null) return;
        RecyclerView.ViewHolder holder = adapter.getViewHolderAt(recyclerView, position);
        if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        wasPlayingBeforePause = true;
        if (feedItems.isEmpty()) {
            viewModel.loadVideos();
        } else {
            resumeCurrentVideo();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        capturePlayStateBeforePause();
        pauseCurrentVideo();
    }

    private void capturePlayStateBeforePause() {
        if (adapter != null && recyclerView != null
                && currentPosition >= 0 && currentPosition < feedItems.size()) {
            RecyclerView.ViewHolder holder = adapter.getViewHolderAt(recyclerView, currentPosition);
            if (holder instanceof VideoViewHolder) {
                wasPlayingBeforePause = ((VideoViewHolder) holder).isCurrentlyPlaying();
            }
        }
    }
}

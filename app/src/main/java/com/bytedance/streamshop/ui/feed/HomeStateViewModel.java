package com.bytedance.streamshop.ui.feed;

import androidx.lifecycle.ViewModel;
import androidx.media3.exoplayer.ExoPlayer;

public class HomeStateViewModel extends ViewModel {
    private int feedPosition = 0;
    private int homeTabPosition = 0;
    private long savedPlaybackPositionMs = 0;
    private boolean savedIsPlaying = true;
    private String savedVideoId = null;
    private ExoPlayer savedPlayer;
    private String savedPlayerVideoId;
    private long savedPlayerPositionMs;

    public int getFeedPosition() {
        return feedPosition;
    }

    public void setFeedPosition(int feedPosition) {
        this.feedPosition = Math.max(0, feedPosition);
    }

    public int getHomeTabPosition() {
        return homeTabPosition;
    }

    public void setHomeTabPosition(int homeTabPosition) {
        this.homeTabPosition = Math.max(0, homeTabPosition);
    }

    public void saveVideoPlaybackState(String videoId, long positionMs, boolean isPlaying) {
        this.savedVideoId = videoId;
        this.savedPlaybackPositionMs = Math.max(0, positionMs);
        this.savedIsPlaying = isPlaying;
    }

    public long getSavedPlaybackPositionMs() {
        return savedPlaybackPositionMs;
    }

    public boolean isSavedPlaying() {
        return savedIsPlaying;
    }

    public String getSavedVideoId() {
        return savedVideoId;
    }

    public void savePlayer(ExoPlayer player, String videoId, long positionMs) {
        releaseSavedPlayer();
        this.savedPlayer = player;
        this.savedPlayerVideoId = videoId;
        this.savedPlayerPositionMs = positionMs;
    }

    public ExoPlayer takePlayer(String expectedVideoId) {
        if (savedPlayer == null) return null;
        if (expectedVideoId == null || !expectedVideoId.equals(savedPlayerVideoId)) {
            releaseSavedPlayer();
            return null;
        }
        ExoPlayer p = savedPlayer;
        savedPlayer = null;
        savedPlayerVideoId = null;
        savedPlayerPositionMs = 0;
        return p;
    }

    public long getSavedPlayerPositionMs() {
        return savedPlayerPositionMs;
    }

    public String getSavedPlayerVideoId() {
        return savedPlayerVideoId;
    }

    public boolean hasSavedPlayer() {
        return savedPlayer != null;
    }

    private void releaseSavedPlayer() {
        if (savedPlayer != null) {
            savedPlayer.stop();
            savedPlayer.release();
            savedPlayer = null;
        }
    }

    @Override
    protected void onCleared() {
        releaseSavedPlayer();
    }
}

package com.bytedance.streamshop.ui.feed;

import androidx.lifecycle.ViewModel;

public class HomeStateViewModel extends ViewModel {
    private int feedPosition = 0;
    private int homeTabPosition = 0;

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
}

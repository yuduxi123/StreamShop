package com.bytedance.streamshop.ui.messages;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.domain.model.Video;
import com.bytedance.streamshop.ui.feed.VideoViewHolder;

import java.util.ArrayList;
import java.util.List;

public class VideoSearchFeedActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private final List<Video> videos = new ArrayList<>();
    private FeedAdapter adapter;
    private int currentPosition = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author_video_feed);

        List<Video> data = (List<Video>) getIntent().getSerializableExtra("videos");
        int startPos = getIntent().getIntExtra("start_position", 0);
        if (data != null) videos.addAll(data);

        viewPager = findViewById(R.id.author_feed_pager);
        findViewById(R.id.author_feed_back).setOnClickListener(v -> finish());

        adapter = new FeedAdapter();
        viewPager.setAdapter(adapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                pauseCurrent();
                currentPosition = position;
                playCurrent();
            }
        });

        if (!videos.isEmpty()) {
            adapter.notifyDataSetChanged();
            int pos = Math.min(startPos, videos.size() - 1);
            viewPager.setCurrentItem(pos, false);
            viewPager.post(() -> playCurrent());
        }
    }

    private void playCurrent() {
        VideoViewHolder holder = getCurrentHolder();
        if (holder != null) holder.play();
    }

    private void pauseCurrent() {
        VideoViewHolder holder = getCurrentHolder();
        if (holder != null) holder.pause();
    }

    private VideoViewHolder getCurrentHolder() {
        RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
        if (rv == null) return null;
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(currentPosition);
        return vh instanceof VideoViewHolder ? (VideoViewHolder) vh : null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseCurrent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < videos.size(); i++) {
            RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
            if (rv == null) break;
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(i);
            if (vh instanceof VideoViewHolder) {
                ((VideoViewHolder) vh).release();
            }
        }
    }

    private class FeedAdapter extends RecyclerView.Adapter<VideoViewHolder> {
        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_video_page, parent, false);
            return new VideoViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            holder.bind(videos.get(position), VideoSearchFeedActivity.this, getSupportFragmentManager());
            holder.setOnPlaybackModeListener(() -> {
                int nextPos = currentPosition + 1;
                if (nextPos < videos.size()) {
                    viewPager.setCurrentItem(nextPos, true);
                }
            });
        }

        @Override
        public void onViewRecycled(@NonNull VideoViewHolder holder) {
            holder.release();
        }

        @Override
        public int getItemCount() {
            return videos.size();
        }
    }
}

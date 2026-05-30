package com.bytedance.streamshop.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.Video;
import com.bytedance.streamshop.ui.feed.VideoViewHolder;

import java.util.ArrayList;
import java.util.List;

public class AuthorVideoFeedActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private final List<Video> videos = new ArrayList<>();
    private FeedAdapter adapter;
    private int currentPosition = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author_video_feed);

        String authorId = getIntent().getStringExtra("author_id");
        int startPos = getIntent().getIntExtra("start_position", 0);

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

        loadAndStart(authorId, startPos);
    }

    private void loadAndStart(String authorId, int startPos) {
        new Thread(() -> {
            try {
                List<Video> data = new ApiService().getUserVideos(authorId, 1, 100).getData();
                videos.clear();
                if (data != null) {
                    videos.addAll(data);
                }
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (!videos.isEmpty()) {
                        int pos = Math.min(startPos, videos.size() - 1);
                        viewPager.setCurrentItem(pos, false);
                        viewPager.post(() -> playCurrent());
                    } else {
                        Toast.makeText(this, "该作者暂无作品", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
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
            holder.bind(videos.get(position), AuthorVideoFeedActivity.this, getSupportFragmentManager());
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

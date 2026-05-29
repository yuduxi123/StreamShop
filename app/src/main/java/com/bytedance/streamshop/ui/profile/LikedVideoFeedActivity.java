package com.bytedance.streamshop.ui.profile;

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
import com.bytedance.streamshop.domain.model.Product;
import com.bytedance.streamshop.domain.model.User;
import com.bytedance.streamshop.domain.model.Video;
import com.bytedance.streamshop.ui.feed.VideoViewHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LikedVideoFeedActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private final List<Video> videos = new ArrayList<>();
    private FeedAdapter adapter;
    private int currentPosition = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_liked_video_feed);

        int startPos = getIntent().getIntExtra("start_position", 0);

        viewPager = findViewById(R.id.liked_feed_pager);
        findViewById(R.id.liked_feed_back).setOnClickListener(v -> finish());

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

        loadAndStart(startPos);
    }

    private void loadAndStart(int startPos) {
        boolean isCollections = "collections".equals(getIntent().getStringExtra("source"));
        String emptyMsg = isCollections ? "暂无收藏视频" : "暂无点赞视频";
        new Thread(() -> {
            try {
                List<Map<String, Object>> items;
                if (isCollections) {
                    items = new ApiService().getCollections("video");
                } else {
                    items = new ApiService().getLikedVideos();
                }
                videos.clear();
                for (Map<String, Object> item : items) {
                    Map<String, Object> target = (Map<String, Object>) item.get("target");
                    if (target != null && target.get("videoUrl") != null) {
                        videos.add(parseVideo(target));
                    }
                }
                String finalEmptyMsg = emptyMsg;
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (!videos.isEmpty()) {
                        viewPager.setCurrentItem(Math.min(startPos, videos.size() - 1), false);
                        viewPager.post(() -> playCurrent());
                    } else {
                        Toast.makeText(this, finalEmptyMsg, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private Video parseVideo(Map<String, Object> map) {
        Video video = new Video();
        video.setId((String) map.get("id"));
        video.setTitle((String) map.get("title"));
        video.setCoverUrl((String) map.get("coverUrl"));
        video.setVideoUrl((String) map.get("videoUrl"));
        video.setAuthorId((String) map.get("authorId"));
        video.setViewCount(((Number) map.getOrDefault("viewCount", 0)).intValue());
        video.setLikeCount(((Number) map.getOrDefault("likeCount", 0)).intValue());
        video.setCommentCount(((Number) map.getOrDefault("commentCount", 0)).intValue());

        Map<String, Object> authorMap = (Map<String, Object>) map.get("author");
        if (authorMap != null) {
            User author = new User();
            author.setId((String) authorMap.get("id"));
            author.setUsername((String) authorMap.get("username"));
            author.setAvatarUrl((String) authorMap.get("avatarUrl"));
            video.setAuthor(author);
        }

        List<Map<String, Object>> productList = (List<Map<String, Object>>) map.get("products");
        if (productList != null) {
            List<Product> products = new ArrayList<>();
            for (Map<String, Object> pm : productList) {
                Product p = new Product();
                p.setId((String) pm.get("id"));
                p.setTitle((String) pm.get("title"));
                p.setPrice(((Number) pm.getOrDefault("price", 0)).doubleValue());
                p.setOriginalPrice(((Number) pm.getOrDefault("originalPrice", 0)).doubleValue());
                p.setCoverUrl((String) pm.get("coverUrl"));
                p.setStock(((Number) pm.getOrDefault("stock", 0)).intValue());
                p.setSalesCount(((Number) pm.getOrDefault("salesCount", 0)).intValue());
                products.add(p);
            }
            video.setProducts(products);
        }

        return video;
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
            holder.bind(videos.get(position), LikedVideoFeedActivity.this, getSupportFragmentManager());
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

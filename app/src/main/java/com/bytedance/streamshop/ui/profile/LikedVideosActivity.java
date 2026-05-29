package com.bytedance.streamshop.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LikedVideosActivity extends AppCompatActivity {
    private RecyclerView gridView;
    private View emptyView;
    private final List<Map<String, Object>> likedVideos = new ArrayList<>();
    private GridAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_liked_videos);

        gridView = findViewById(R.id.liked_grid);
        emptyView = findViewById(R.id.liked_empty);

        findViewById(R.id.liked_back).setOnClickListener(v -> finish());

        int spanCount = 3;
        gridView.setLayoutManager(new GridLayoutManager(this, spanCount));
        adapter = new GridAdapter();
        gridView.setAdapter(adapter);

        loadLikedVideos();
    }

    private void loadLikedVideos() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> items = new ApiService().getLikedVideos();
                likedVideos.clear();
                likedVideos.addAll(items);
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    gridView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void onVideoClick(int position) {
        Intent intent = new Intent(this, LikedVideoFeedActivity.class);
        intent.putExtra("start_position", position);
        startActivity(intent);
    }

    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_liked_video, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = likedVideos.get(position);
            Map<String, Object> video = (Map<String, Object>) item.get("target");
            if (video != null) {
                Glide.with(h.thumb).load((String) video.get("coverUrl")).into(h.thumb);
                Object likeCount = video.get("likeCount");
                h.likeCount.setText(likeCount != null ? formatCount(((Number) likeCount).intValue()) : "0");
            }
            h.itemView.setOnClickListener(v -> onVideoClick(h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return likedVideos.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView likeCount;

            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.liked_thumb);
                likeCount = v.findViewById(R.id.liked_like_count);
            }
        }
    }

    private String formatCount(int count) {
        if (count >= 10000) {
            return String.format("%.1fw", count / 10000.0);
        }
        return String.valueOf(count);
    }
}

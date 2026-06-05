package com.bytedance.streamshop.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.Video;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class MyVideosActivity extends AppCompatActivity {
    private RecyclerView listView;
    private View emptyView;
    private View loadingView;
    private final List<Video> videos = new ArrayList<>();
    private VideoAdapter adapter;

    private static final String[] STATUS_OPTIONS = {"draft", "published", "taken_down"};
    private static final String[] STATUS_LABELS = {"草稿", "已发布", "已下架"};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_videos);

        listView = findViewById(R.id.my_videos_list);
        emptyView = findViewById(R.id.my_videos_empty);
        loadingView = findViewById(R.id.my_videos_loading);

        findViewById(R.id.my_videos_back).setOnClickListener(v -> finish());
        findViewById(R.id.my_videos_create).setOnClickListener(v -> {
            startActivity(new Intent(this, VideoEditActivity.class));
        });

        listView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter();
        listView.setAdapter(adapter);

        loadVideos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVideos();
    }

    private void loadVideos() {
        loadingView.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                List<Video> data = new ApiService().getMyVideos(1, 100).getData();
                videos.clear();
                if (data != null) videos.addAll(data);
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    loadingView.setVisibility(View.GONE);
                    boolean empty = videos.isEmpty();
                    emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                    listView.setVisibility(empty ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingView.setVisibility(View.GONE);
                    Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void deleteVideo(int position) {
        Video video = videos.get(position);
        new Thread(() -> {
            try {
                new ApiService().deleteVideo(video.getId());
                runOnUiThread(() -> {
                    videos.remove(position);
                    adapter.notifyItemRemoved(position);
                    if (videos.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        listView.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String getStatusLabel(String status) {
        for (int i = 0; i < STATUS_OPTIONS.length; i++) {
            if (STATUS_OPTIONS[i].equals(status)) return STATUS_LABELS[i];
        }
        return status;
    }

    private class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {
        private int openPosition = -1;
        private float buttonPanelWidth;

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_video, parent, false);
            buttonPanelWidth = 128 * parent.getContext().getResources().getDisplayMetrics().density;
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Video video = videos.get(position);
            h.titleText.setText(video.getTitle());
            h.statusText.setText(getStatusLabel(video.getStatus()));
            h.viewCountText.setText("播放 " + video.getViewCount());
            h.likeCountText.setText("点赞 " + video.getLikeCount());
            if (video.getCoverUrl() != null && !video.getCoverUrl().isEmpty()) {
                Glide.with(h.coverView).load(video.getCoverUrl()).into(h.coverView);
            } else {
                h.coverView.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            // --- Swipe touch handling ---
            h.foreground.setTranslationX(0f);
            h.swipeStartX = 0;
            h.swipeStartTrans = 0;

            h.foreground.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (openPosition != -1 && openPosition != position) {
                        closeOpenItem();
                    }
                    h.swipeStartX = event.getRawX();
                    h.swipeStartTrans = v.getTranslationX();
                    return true;
                }

                float dx = event.getRawX() - h.swipeStartX;
                float newTrans = h.swipeStartTrans + dx;

                if (newTrans > 0) newTrans = 0;
                if (newTrans < -buttonPanelWidth) newTrans = -buttonPanelWidth;

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(dx) > 5) {
                        listView.requestDisallowInterceptTouchEvent(true);
                    }
                    v.setTranslationX(newTrans);
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    listView.requestDisallowInterceptTouchEvent(false);
                    float currentTrans = v.getTranslationX();
                    float threshold = -buttonPanelWidth * 0.4f;

                    if (Math.abs(currentTrans - h.swipeStartTrans) < 10) {
                        v.animate().translationX(0).setDuration(100).start();
                        openPosition = -1;
                    } else if (currentTrans < threshold) {
                        v.animate().translationX(-buttonPanelWidth).setDuration(150).start();
                        openPosition = position;
                    } else {
                        v.animate().translationX(0).setDuration(150).start();
                        openPosition = -1;
                    }
                    return true;
                }
                return false;
            });

            h.actionEdit.setOnClickListener(v -> {
                openPosition = -1;
                Intent intent = new Intent(MyVideosActivity.this, VideoEditActivity.class);
                intent.putExtra("video_id", video.getId());
                intent.putExtra("video_title", video.getTitle());
                intent.putExtra("video_cover", video.getCoverUrl());
                intent.putExtra("video_url", video.getVideoUrl());
                intent.putExtra("video_tags", video.getTags() != null
                        ? android.text.TextUtils.join(",", video.getTags()) : "");
                intent.putExtra("video_status", video.getStatus());
                startActivity(intent);
            });

            h.actionDelete.setOnClickListener(v -> {
                openPosition = -1;
                new androidx.appcompat.app.AlertDialog.Builder(MyVideosActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除视频 \"" + video.getTitle() + "\" 吗？")
                        .setPositiveButton("删除", (d, w) -> deleteVideo(h.getBindingAdapterPosition()))
                        .setNegativeButton("取消", null)
                        .show();
            });
        }

        private void closeOpenItem() {
            if (openPosition != -1) {
                int old = openPosition;
                openPosition = -1;
                notifyItemChanged(old);
            }
        }

        @Override
        public int getItemCount() {
            return videos.size();
        }

        class VH extends RecyclerView.ViewHolder {
            View foreground;
            ImageView coverView;
            TextView titleText, statusText, viewCountText, likeCountText;
            TextView actionEdit, actionDelete;
            float swipeStartX, swipeStartTrans;

            VH(View v) {
                super(v);
                foreground = v.findViewById(R.id.my_video_foreground);
                coverView = v.findViewById(R.id.my_video_cover);
                titleText = v.findViewById(R.id.my_video_title);
                statusText = v.findViewById(R.id.my_video_status);
                viewCountText = v.findViewById(R.id.my_video_views);
                likeCountText = v.findViewById(R.id.my_video_likes);
                actionEdit = v.findViewById(R.id.my_video_action_edit);
                actionDelete = v.findViewById(R.id.my_video_action_delete);
            }
        }
    }
}

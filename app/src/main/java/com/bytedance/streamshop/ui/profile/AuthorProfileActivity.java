package com.bytedance.streamshop.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.User;
import com.bytedance.streamshop.domain.model.Video;
import com.bytedance.streamshop.ui.messages.ChatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class AuthorProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private String userId;
    private User currentUser;
    private boolean isFollowing;
    private final List<Video> videoList = new ArrayList<>();
    private VideoGridAdapter gridAdapter;
    private ApiService apiService;

    private ShapeableImageView avatarView;
    private TextView usernameText;
    private TextView likesCountText;
    private TextView followingCountText;
    private TextView followersCountText;
    private MaterialButton followBtn;
    private RecyclerView videoGrid;
    private View emptyVideos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author_profile);

        apiService = new ApiService();
        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userId == null) {
            finish();
            return;
        }

        initViews();
    }

    private void initViews() {
        avatarView = findViewById(R.id.author_avatar);
        usernameText = findViewById(R.id.author_username);
        likesCountText = findViewById(R.id.author_likes_count);
        followingCountText = findViewById(R.id.author_following_count);
        followersCountText = findViewById(R.id.author_followers_count);
        followBtn = findViewById(R.id.author_follow_btn);
        videoGrid = findViewById(R.id.author_video_grid);
        emptyVideos = findViewById(R.id.author_empty_videos);

        findViewById(R.id.author_back).setOnClickListener(v -> finish());
        followBtn.setOnClickListener(v -> toggleFollow());

        MaterialButton messageBtn = findViewById(R.id.author_message_btn);
        messageBtn.setOnClickListener(v -> openChat());

        gridAdapter = new VideoGridAdapter();
        videoGrid.setLayoutManager(new GridLayoutManager(this, 2));
        videoGrid.setAdapter(gridAdapter);

        loadUserProfile();
        loadUserVideos();
    }

    private void loadUserProfile() {
        new Thread(() -> {
            try {
                currentUser = apiService.getUserProfile(userId);
                isFollowing = apiService.isFollowing(userId);
                runOnUiThread(() -> {
                    usernameText.setText(currentUser.getUsername());
                    followingCountText.setText(formatCount(currentUser.getFollowing()));
                    followersCountText.setText(formatCount(currentUser.getFollowers()));
                    updateFollowButton();

                    Glide.with(AuthorProfileActivity.this)
                            .load(currentUser.getAvatarUrl())
                            .circleCrop()
                            .skipMemoryCache(true)
                            .placeholder(R.drawable.ic_avatar_placeholder)
                            .into(avatarView);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(AuthorProfileActivity.this, "加载用户信息失败", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void loadUserVideos() {
        new Thread(() -> {
            try {
                List<Video> data = apiService.getUserVideos(userId, 1, 100).getData();
                int totalLikes = 0;
                videoList.clear();
                if (data != null) {
                    videoList.addAll(data);
                    for (Video v : data) {
                        totalLikes += v.getLikeCount();
                    }
                }
                int finalTotalLikes = totalLikes;
                runOnUiThread(() -> {
                    gridAdapter.notifyDataSetChanged();
                    emptyVideos.setVisibility(videoList.isEmpty() ? View.VISIBLE : View.GONE);
                    videoGrid.setVisibility(videoList.isEmpty() ? View.GONE : View.VISIBLE);
                    if (likesCountText != null) {
                        likesCountText.setText(formatCount(finalTotalLikes));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    emptyVideos.setVisibility(View.VISIBLE);
                    videoGrid.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void openChat() {
        if (currentUser == null) return;
        String myId = ApiClient.getInstance().getCurrentUserId();
        if (myId == null) return;
        String convId = myId.compareTo(currentUser.getId()) < 0
                ? myId + "_" + currentUser.getId()
                : currentUser.getId() + "_" + myId;
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("conversation_id", convId);
        intent.putExtra("other_user_id", currentUser.getId());
        intent.putExtra("other_username", currentUser.getUsername());
        intent.putExtra("other_avatar_url", currentUser.getAvatarUrl());
        startActivity(intent);
    }

    private void toggleFollow() {
        if (currentUser == null || followBtn == null) return;
        followBtn.setEnabled(false);
        new Thread(() -> {
            try {
                isFollowing = apiService.toggleFollow(currentUser.getId());
                runOnUiThread(() -> {
                    updateFollowButton();
                    followBtn.setEnabled(true);
                    loadUserProfile();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (followBtn != null) followBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private void updateFollowButton() {
        if (followBtn == null) return;
        if (isFollowing) {
            followBtn.setText("已关注");
            followBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF999999));
        } else {
            followBtn.setText("关注");
            followBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF3B30));
        }
    }

    private String formatCount(Number num) {
        long n = num.longValue();
        if (n >= 10000) return (n / 1000 / 10.0) + "w";
        if (n >= 1000) return (n / 100 / 10.0) + "k";
        return String.valueOf(n);
    }

    private class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_profile_video_grid, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Video v = videoList.get(pos);
            Glide.with(h.itemView).load(v.getCoverUrl()).into(h.cover);
            h.likes.setText(formatCount(v.getLikeCount()));
            h.itemView.setOnClickListener(view ->
                    startActivity(new Intent(AuthorProfileActivity.this, AuthorVideoFeedActivity.class)
                            .putExtra("author_id", userId)
                            .putExtra("start_position", pos)));
        }

        @Override public int getItemCount() { return videoList.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView likes;
            VH(View v) {
                super(v);
                cover = v.findViewById(R.id.grid_video_cover);
                likes = v.findViewById(R.id.grid_video_likes);
            }
        }
    }
}

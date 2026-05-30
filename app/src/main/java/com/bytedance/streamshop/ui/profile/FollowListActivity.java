package com.bytedance.streamshop.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FollowListActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "follow_mode";
    public static final String MODE_FOLLOWING = "following";
    public static final String MODE_FOLLOWERS = "followers";

    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView titleText;
    private String mode;
    private final List<Map<String, Object>> userList = new ArrayList<>();
    private FollowUserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_follow_list);

        mode = getIntent().getStringExtra(EXTRA_MODE);

        ImageButton backBtn = findViewById(R.id.follow_list_back);
        titleText = findViewById(R.id.follow_list_title);
        recyclerView = findViewById(R.id.follow_list_recycler);
        emptyView = findViewById(R.id.follow_list_empty);

        if (MODE_FOLLOWERS.equals(mode)) {
            titleText.setText("我的粉丝");
        } else {
            titleText.setText("我关注的人");
        }

        backBtn.setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FollowUserAdapter();
        recyclerView.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            try {
                ApiService api = new ApiService();
                List<Map<String, Object>> items = MODE_FOLLOWERS.equals(mode)
                        ? api.getFollowers() : api.getFollowing();
                userList.clear();
                userList.addAll(items);
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(FollowListActivity.this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private class FollowUserAdapter extends RecyclerView.Adapter<FollowUserAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_follow_user, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = userList.get(position);
            Map<String, Object> userMap = (Map<String, Object>) item.get("user");
            if (userMap == null) return;

            String userId = (String) userMap.get("id");
            String username = (String) userMap.get("username");
            String avatarUrl = (String) userMap.get("avatarUrl");

            h.usernameText.setText(username != null ? username : "未知用户");

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(h.avatarView.getContext())
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .into(h.avatarView);
            } else {
                h.avatarView.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            h.timeText.setText(formatFollowTime((String) item.get("createdAt")));

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(FollowListActivity.this, AuthorProfileActivity.class);
                intent.putExtra(AuthorProfileActivity.EXTRA_USER_ID, userId);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return userList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView avatarView;
            TextView usernameText;
            TextView timeText;

            VH(View v) {
                super(v);
                avatarView = v.findViewById(R.id.follow_user_avatar);
                usernameText = v.findViewById(R.id.follow_user_username);
                timeText = v.findViewById(R.id.follow_user_time);
            }
        }
    }

    private String formatFollowTime(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            isoFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date d = isoFmt.parse(isoDate);
            if (d == null) return "";
            long diff = System.currentTimeMillis() - d.getTime();
            long days = diff / (1000 * 60 * 60 * 24);
            if (days < 1) return "今天";
            if (days < 2) return "昨天";
            if (days < 30) return days + "天前";
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return "";
        }
    }
}

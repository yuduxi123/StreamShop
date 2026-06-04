package com.bytedance.streamshop.ui.messages;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
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

import com.bumptech.glide.Glide;
import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AddFriendSearchActivity extends AppCompatActivity {
    private ApiService apiService;
    private RecyclerView userList;
    private TextView emptyText;
    private UserAdapter adapter;
    private final List<Map<String, Object>> users = new ArrayList<>();
    private final Set<String> followingIds = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend_search);

        apiService = new ApiService();
        userList = findViewById(R.id.add_friend_search_list);
        emptyText = findViewById(R.id.add_friend_search_empty);

        findViewById(R.id.add_friend_back).setOnClickListener(v -> finish());

        userList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter();
        userList.setAdapter(adapter);

        android.widget.EditText searchInput = findViewById(R.id.add_friend_search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> performSearch(s.toString().trim());
                handler.postDelayed(searchRunnable, 300);
            }
        });
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            users.clear();
            adapter.notifyDataSetChanged();
            emptyText.setVisibility(View.VISIBLE);
            userList.setVisibility(View.GONE);
            return;
        }
        new Thread(() -> {
            try {
                List<Map<String, Object>> results = apiService.searchUsers(query);
                String selfId = com.bytedance.streamshop.data.remote.ApiClient.getInstance().getCurrentUserId();
                List<Map<String, Object>> filtered = new ArrayList<>();
                if (results != null) {
                    for (Map<String, Object> u : results) {
                        if (selfId == null || !selfId.equals(u.get("id"))) {
                            filtered.add(u);
                        }
                    }
                }
                runOnUiThread(() -> {
                    users.clear();
                    users.addAll(filtered);
                    adapter.notifyDataSetChanged();
                    emptyText.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
                    userList.setVisibility(users.isEmpty() ? View.GONE : View.VISIBLE);
                    checkFollowStatuses();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "搜索失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void checkFollowStatuses() {
        new Thread(() -> {
            for (Map<String, Object> user : users) {
                String uid = (String) user.get("id");
                try {
                    boolean following = apiService.isFollowing(uid);
                    if (following) followingIds.add(uid);
                    else followingIds.remove(uid);
                } catch (Exception ignored) {}
            }
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_user, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> user = users.get(pos);
            String uid = (String) user.get("id");
            if (h.name != null) h.name.setText((String) user.get("username"));
            if (h.account != null) {
                String account = (String) user.get("account");
                h.account.setText(account != null ? account : "");
            }
            if (h.avatar != null) {
                Glide.with(AddFriendSearchActivity.this)
                        .load((String) user.get("avatarUrl"))
                        .circleCrop()
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .into(h.avatar);
            }
            boolean following = followingIds.contains(uid);
            if (h.followBtn != null) {
                h.followBtn.setText(following ? "已关注" : "关注");
                h.followBtn.setBackgroundResource(following
                        ? R.drawable.bg_follow_btn_following
                        : R.drawable.bg_follow_btn);
                h.followBtn.setTextColor(following ? 0xFF999999 : 0xFFFFFFFF);
            }
            h.followBtn.setOnClickListener(v -> toggleFollow(uid, pos));
        }

        @Override
        public int getItemCount() { return users.size(); }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView avatar;
            TextView name, account;
            TextView followBtn;
            VH(View v) {
                super(v);
                avatar = v.findViewById(R.id.search_user_avatar);
                name = v.findViewById(R.id.search_user_name);
                account = v.findViewById(R.id.search_user_account);
                followBtn = v.findViewById(R.id.search_user_follow_btn);
            }
        }
    }

    private void toggleFollow(String userId, int pos) {
        new Thread(() -> {
            try {
                boolean nowFollowing = apiService.toggleFollow(userId);
                if (nowFollowing) followingIds.add(userId);
                else followingIds.remove(userId);
                runOnUiThread(() -> adapter.notifyItemChanged(pos));
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}

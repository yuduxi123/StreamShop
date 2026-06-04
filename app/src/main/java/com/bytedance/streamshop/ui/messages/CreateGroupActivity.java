package com.bytedance.streamshop.ui.messages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
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

public class CreateGroupActivity extends AppCompatActivity {
    private ApiService apiService;
    private RecyclerView userList;
    private TextView emptyText;
    private TextView doneBtn;
    private EditText nameInput;
    private UserAdapter adapter;
    private final List<Map<String, Object>> users = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        apiService = new ApiService();
        userList = findViewById(R.id.create_group_user_list);
        emptyText = findViewById(R.id.create_group_empty);
        doneBtn = findViewById(R.id.create_group_done_btn);
        nameInput = findViewById(R.id.create_group_name_input);

        findViewById(R.id.create_group_back).setOnClickListener(v -> finish());

        userList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter();
        userList.setAdapter(adapter);

        doneBtn.setOnClickListener(v -> createGroup());

        loadFollowing();
    }

    private void loadFollowing() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> following = apiService.getFollowing();
                runOnUiThread(() -> {
                    if (following != null) users.addAll(following);
                    adapter.notifyDataSetChanged();
                    emptyText.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
                    userList.setVisibility(users.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "加载关注列表失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void toggleSelection(int pos, String userId) {
        if (selectedIds.contains(userId)) {
            selectedIds.remove(userId);
        } else {
            selectedIds.add(userId);
        }
        adapter.notifyItemChanged(pos);
        updateDoneButton();
    }

    private void updateDoneButton() {
        boolean enabled = !selectedIds.isEmpty();
        doneBtn.setEnabled(enabled);
        doneBtn.setTextColor(enabled ? 0xFF007AFF : 0xFF999999);
    }

    private void createGroup() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            // Auto-generate group name from selected users
            List<String> names = new ArrayList<>();
            for (Map<String, Object> u : users) {
                Map<String, Object> userData = (Map<String, Object>) u.get("user");
                if (userData != null && selectedIds.contains(userData.get("id"))) {
                    names.add((String) userData.get("username"));
                }
            }
            name = String.join(", ", names);
            if (name.length() > 30) name = name.substring(0, 30) + "...";
        }

        String finalName = name;
        doneBtn.setEnabled(false);
        new Thread(() -> {
            try {
                Map<String, Object> group = apiService.createGroupChat(finalName, new ArrayList<>(selectedIds));
                String groupId = (String) group.get("id");
                runOnUiThread(() -> {
                    Toast.makeText(this, "群聊创建成功", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra("conversation_id", groupId);
                    intent.putExtra("is_group", true);
                    intent.putExtra("group_name", finalName);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    doneBtn.setEnabled(true);
                    updateDoneButton();
                    Toast.makeText(this, "创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_selectable_user, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> item = users.get(pos);
            Map<String, Object> user = (Map<String, Object>) item.get("user");
            if (user == null) return;
            String uid = (String) user.get("id");
            if (h.name != null) h.name.setText((String) user.get("username"));
            if (h.avatar != null) {
                Glide.with(CreateGroupActivity.this)
                        .load((String) user.get("avatarUrl"))
                        .circleCrop()
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .into(h.avatar);
            }
            if (h.checkBox != null) {
                h.checkBox.setChecked(selectedIds.contains(uid));
            }
            h.itemView.setOnClickListener(v -> {
                if (h.checkBox != null) {
                    h.checkBox.setChecked(!h.checkBox.isChecked());
                }
                toggleSelection(pos, uid);
            });
            h.checkBox.setOnCheckedChangeListener(null);
            h.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedIds.add(uid);
                else selectedIds.remove(uid);
                updateDoneButton();
            });
        }

        @Override
        public int getItemCount() { return users.size(); }

        class VH extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            ShapeableImageView avatar;
            TextView name;
            VH(View v) {
                super(v);
                checkBox = v.findViewById(R.id.selectable_user_checkbox);
                avatar = v.findViewById(R.id.selectable_user_avatar);
                name = v.findViewById(R.id.selectable_user_name);
            }
        }
    }
}

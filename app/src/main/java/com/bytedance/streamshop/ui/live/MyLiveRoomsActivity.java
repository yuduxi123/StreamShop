package com.bytedance.streamshop.ui.live;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bumptech.glide.Glide;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyLiveRoomsActivity extends AppCompatActivity {
    private static final int REQ_PERMISSIONS = 2001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private RecyclerView listView;
    private TextView emptyView;
    private View loadingView;
    private final List<Map<String, Object>> rooms = new ArrayList<>();
    private RoomAdapter adapter;
    private ApiService apiService;

    private String pendingRoomId;
    private String pendingRoomTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_live_rooms);

        apiService = new ApiService();
        listView = findViewById(R.id.my_liverooms_list);
        emptyView = findViewById(R.id.my_liverooms_empty);
        loadingView = findViewById(R.id.my_liverooms_list);

        findViewById(R.id.my_liverooms_back).setOnClickListener(v -> finish());
        findViewById(R.id.my_liverooms_create).setOnClickListener(v -> {
            startActivity(new Intent(this, LiveRoomEditActivity.class));
        });

        listView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RoomAdapter();
        listView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRooms();
    }

    private void loadRooms() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> data = apiService.getMyLiveRooms(1, 100).getData();
                rooms.clear();
                if (data != null) rooms.addAll(data);
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    boolean empty = rooms.isEmpty();
                    emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                    listView.setVisibility(empty ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    emptyView.setVisibility(View.VISIBLE);
                    listView.setVisibility(View.GONE);
                    Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void deleteRoom(int position) {
        Map<String, Object> room = rooms.get(position);
        String roomId = (String) room.get("id");
        new Thread(() -> {
            try {
                apiService.deleteLiveRoom(roomId);
                runOnUiThread(() -> {
                    rooms.remove(position);
                    adapter.notifyItemRemoved(position);
                    if (rooms.isEmpty()) {
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

    private class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_live_room, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> room = rooms.get(position);
            String title = (String) room.get("title");
            String coverUrl = (String) room.get("coverUrl");
            String status = (String) room.get("status");
            Object onlineCount = room.get("onlineCount");

            h.titleText.setText(title != null ? title : "未命名直播间");
            h.statusText.setText(getStatusLabel(status));
            h.statusText.setBackgroundResource(statusBg(status));

            if (coverUrl != null && !coverUrl.isEmpty()) {
                Glide.with(h.coverView).load(coverUrl).into(h.coverView);
            } else {
                h.coverView.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            int online = onlineCount instanceof Number ? ((Number) onlineCount).intValue() : 0;
            h.onlineText.setText(online + "人在线");
            h.onlineText.setVisibility("live".equals(status) ? View.VISIBLE : View.GONE);

            // Start/End buttons visibility
            boolean isLive = "live".equals(status);
            boolean canStart = "offline".equals(status) || "ended".equals(status);
            h.startBtn.setVisibility(canStart ? View.VISIBLE : View.GONE);
            h.endBtn.setVisibility(isLive ? View.VISIBLE : View.GONE);

            h.startBtn.setOnClickListener(v -> {
                String roomId = (String) room.get("id");
                launchLiveRoom(roomId, title);
            });

            h.endBtn.setOnClickListener(v -> {
                String roomId = (String) room.get("id");
                new Thread(() -> {
                    try {
                        apiService.endLiveRoom(roomId);
                        runOnUiThread(() -> loadRooms());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(MyLiveRoomsActivity.this, "结束失败", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            });

            // Click card to edit
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MyLiveRoomsActivity.this, LiveRoomEditActivity.class);
                intent.putExtra("room_id", (String) room.get("id"));
                intent.putExtra("room_title", title);
                intent.putExtra("room_cover", coverUrl);
                startActivity(intent);
            });

            h.itemView.setOnLongClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(MyLiveRoomsActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除直播间 \"" + title + "\" 吗？")
                        .setPositiveButton("删除", (d, w) -> deleteRoom(h.getBindingAdapterPosition()))
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView coverView;
            TextView titleText, statusText, onlineText;
            TextView startBtn, endBtn;

            VH(View v) {
                super(v);
                coverView = v.findViewById(R.id.liveroom_item_cover);
                titleText = v.findViewById(R.id.liveroom_item_title);
                statusText = v.findViewById(R.id.liveroom_item_status);
                onlineText = v.findViewById(R.id.liveroom_item_online);
                startBtn = v.findViewById(R.id.liveroom_item_start_btn);
                endBtn = v.findViewById(R.id.liveroom_item_end_btn);
            }
        }
    }

    private String getStatusLabel(String status) {
        if ("live".equals(status)) return "直播中";
        if ("offline".equals(status)) return "未开播";
        if ("ended".equals(status)) return "已结束";
        return status;
    }

    private int statusBg(String status) {
        if ("live".equals(status)) return R.drawable.bg_live_feed_button;
        if ("ended".equals(status)) return R.drawable.bg_circle_black_transparent;
        return R.drawable.bg_circle_black_transparent;
    }

    private void launchLiveRoom(String roomId, String roomTitle) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAnchorActivity(roomId, roomTitle);
        } else {
            pendingRoomId = roomId;
            pendingRoomTitle = roomTitle;
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && pendingRoomId != null) {
                startAnchorActivity(pendingRoomId, pendingRoomTitle);
            } else if (!allGranted) {
                Toast.makeText(this, "需要摄像头和录音权限才能开播", Toast.LENGTH_LONG).show();
            }
            pendingRoomId = null;
            pendingRoomTitle = null;
        }
    }

    private void startAnchorActivity(String roomId, String roomTitle) {
        Intent intent = new Intent(MyLiveRoomsActivity.this, AnchorLiveActivity.class);
        intent.putExtra("room_id", roomId);
        intent.putExtra("room_title", roomTitle);
        startActivity(intent);
    }
}

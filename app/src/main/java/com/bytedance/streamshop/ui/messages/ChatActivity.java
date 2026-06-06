package com.bytedance.streamshop.ui.messages;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.ui.feed.ForwardedVideoActivity;
import com.bytedance.streamshop.ui.order.OrderDetailActivity;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {
    private static final int VIEW_TYPE_SENT = 0;
    private static final int VIEW_TYPE_RECEIVED = 1;
    private static final int VIEW_TYPE_SENT_FORWARD = 2;
    private static final int VIEW_TYPE_RECEIVED_FORWARD = 3;
    private static final int VIEW_TYPE_SENT_ORDER = 4;
    private static final int VIEW_TYPE_RECEIVED_ORDER = 5;

    private String conversationId;
    private String otherUserId;
    private String currentUserId;
    private boolean isGroup;
    private String groupName;

    private RecyclerView messageList;
    private EditText inputView;
    private ImageButton sendBtn;
    private MessageAdapter adapter;
    private ApiService apiService;
    private final List<Map<String, Object>> messages = new ArrayList<>();
    private boolean loadingMore;
    private boolean hasMore = true;
    private int currentPage = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversationId = getIntent().getStringExtra("conversation_id");
        otherUserId = getIntent().getStringExtra("other_user_id");
        isGroup = getIntent().getBooleanExtra("is_group", false);
        groupName = getIntent().getStringExtra("group_name");
        currentUserId = ApiClient.getInstance().getCurrentUserId();

        if (conversationId == null || (!isGroup && otherUserId == null)) {
            finish();
            return;
        }

        apiService = new ApiService();
        initViews();
        loadMessages();
    }

    private void initViews() {
        TextView titleText = findViewById(R.id.chat_title);
        if (isGroup && groupName != null) {
            titleText.setText(groupName);
        } else {
            String otherName = getIntent().getStringExtra("other_username");
            titleText.setText(otherName != null ? otherName : "聊天");
        }

        findViewById(R.id.chat_back).setOnClickListener(v -> finish());

        messageList = findViewById(R.id.chat_message_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messageList.setLayoutManager(layoutManager);
        adapter = new MessageAdapter();
        messageList.setAdapter(adapter);

        inputView = findViewById(R.id.chat_input);
        sendBtn = findViewById(R.id.chat_send);
        sendBtn.setOnClickListener(v -> sendMessage());
    }

    private void loadMessages() {
        loadingMore = true;
        new Thread(() -> {
            try {
                var response = apiService.getMessages(conversationId, currentPage, 50);
                List<Map<String, Object>> data = response.getData();
                int total = response.getTotal();
                int limit = response.getLimit();
                int totalPages = limit > 0 ? (int) Math.ceil((double) total / limit) : 1;
                hasMore = currentPage < totalPages;

                if (data != null) {
                    messages.addAll(0, data);
                }
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (currentPage == 1 && !messages.isEmpty()) {
                        messageList.scrollToPosition(messages.size() - 1);
                    }
                    loadingMore = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingMore = false;
                    if (messages.isEmpty()) {
                        Toast.makeText(this, "加载消息失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void loadMore() {
        if (loadingMore || !hasMore) return;
        currentPage++;
        loadMessages();
    }

    private void sendMessage() {
        String content = inputView.getText().toString().trim();
        if (content.isEmpty()) return;

        inputView.setText("");
        sendBtn.setEnabled(false);

        new Thread(() -> {
            try {
                Map<String, Object> msg;
                if (isGroup) {
                    msg = apiService.sendGroupMessage(conversationId, content);
                } else {
                    msg = apiService.sendMessage(otherUserId, content);
                }
                messages.add(msg);
                runOnUiThread(() -> {
                    adapter.notifyItemInserted(messages.size() - 1);
                    messageList.smoothScrollToPosition(messages.size() - 1);
                    sendBtn.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    sendBtn.setEnabled(true);
                });
            }
        }).start();
    }

    // --- Adapter ---

    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        @Override
        public int getItemViewType(int position) {
            Map<String, Object> msg = messages.get(position);
            String senderId = (String) msg.get("senderId");
            String type = (String) msg.get("type");
            boolean isSent = currentUserId != null && currentUserId.equals(senderId);
            if ("forward".equals(type)) {
                return isSent ? VIEW_TYPE_SENT_FORWARD : VIEW_TYPE_RECEIVED_FORWARD;
            }
            if ("order_remind".equals(type)) {
                return isSent ? VIEW_TYPE_SENT_ORDER : VIEW_TYPE_RECEIVED_ORDER;
            }
            return isSent ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int layout;
            if (type == VIEW_TYPE_SENT_FORWARD || type == VIEW_TYPE_RECEIVED_FORWARD) {
                layout = R.layout.item_message_forward;
            } else if (type == VIEW_TYPE_SENT_ORDER || type == VIEW_TYPE_RECEIVED_ORDER) {
                layout = R.layout.item_message_order;
            } else if (type == VIEW_TYPE_SENT) {
                layout = R.layout.item_message_sent;
            } else {
                layout = R.layout.item_message_received;
            }
            return new VH(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> msg = messages.get(pos);
            String type = (String) msg.get("type");
            if ("forward".equals(type)) {
                String title = (String) msg.get("videoTitle");
                if (title == null) {
                    String content = (String) msg.get("content");
                    title = content != null ? content.replace("[转发视频] ", "") : "";
                }
                if (h.forwardTitle != null) h.forwardTitle.setText(title != null ? title : "");
                String coverUrl = (String) msg.get("videoCoverUrl");
                if (h.forwardCover != null && coverUrl != null && !coverUrl.isEmpty()) {
                    com.bumptech.glide.Glide.with(ChatActivity.this)
                            .load(coverUrl)
                            .centerCrop()
                            .into(h.forwardCover);
                }
                h.itemView.setOnClickListener(v -> {
                    String videoId = (String) msg.get("videoId");
                    if (videoId != null) {
                        Intent intent = new Intent(ChatActivity.this, ForwardedVideoActivity.class);
                        intent.putExtra("video_id", videoId);
                        startActivity(intent);
                    }
                });
            } else if ("order_remind".equals(type)) {
                String content = (String) msg.get("content");
                if (h.orderContent != null) h.orderContent.setText(content != null ? content : "");
                h.itemView.setOnClickListener(v -> {
                    String orderId = (String) msg.get("orderId");
                    if (orderId != null) {
                        Intent intent = new Intent(ChatActivity.this, OrderDetailActivity.class);
                        intent.putExtra("order_id", orderId);
                        startActivity(intent);
                    }
                });
            } else {
                String text = (String) msg.get("content");
                if (isGroup && h.senderName != null) {
                    String senderName = (String) msg.get("senderUsername");
                    boolean isSelf = currentUserId != null && currentUserId.equals(msg.get("senderId"));
                    if (!isSelf && senderName != null) {
                        h.senderName.setVisibility(View.VISIBLE);
                        h.senderName.setText(senderName);
                    } else {
                        h.senderName.setVisibility(View.GONE);
                    }
                }
                if (h.content != null) h.content.setText(text != null ? text : "");
            }
        }

        @Override
        public int getItemCount() { return messages.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView content;
            TextView forwardTitle;
            ImageView forwardCover;
            TextView senderName;
            TextView orderContent;
            VH(View v) {
                super(v);
                content = v.findViewById(R.id.msg_content);
                forwardTitle = v.findViewById(R.id.msg_forward_title);
                forwardCover = v.findViewById(R.id.msg_forward_cover);
                senderName = v.findViewById(R.id.msg_sender_name);
                orderContent = v.findViewById(R.id.msg_order_content);
            }
        }
    }
}

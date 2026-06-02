package com.bytedance.streamshop.ui.messages;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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

    private String conversationId;
    private String otherUserId;
    private String currentUserId;

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
        currentUserId = ApiClient.getInstance().getCurrentUserId();

        if (conversationId == null || otherUserId == null) {
            finish();
            return;
        }

        apiService = new ApiService();
        initViews();
        loadMessages();
    }

    private void initViews() {
        TextView titleText = findViewById(R.id.chat_title);
        String otherName = getIntent().getStringExtra("other_username");
        titleText.setText(otherName != null ? otherName : "聊天");

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
                Map<String, Object> msg = apiService.sendMessage(otherUserId, content);
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
            return currentUserId != null && currentUserId.equals(senderId) ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int layout = type == VIEW_TYPE_SENT ? R.layout.item_message_sent : R.layout.item_message_received;
            return new VH(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> msg = messages.get(pos);
            h.content.setText((String) msg.get("content"));
        }

        @Override
        public int getItemCount() { return messages.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView content;
            VH(View v) {
                super(v);
                content = v.findViewById(R.id.msg_content);
            }
        }
    }
}

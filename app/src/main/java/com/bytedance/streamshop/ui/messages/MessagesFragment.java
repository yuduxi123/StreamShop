package com.bytedance.streamshop.ui.messages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessagesFragment extends Fragment {
    private RecyclerView messageList;
    private TextView emptyView;
    private ConversationAdapter adapter;
    private ApiService apiService;
    private final List<Map<String, Object>> conversations = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        messageList = view.findViewById(R.id.messages_list);
        emptyView = view.findViewById(R.id.messages_empty);
        apiService = new ApiService();

        messageList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationAdapter();
        messageList.setAdapter(adapter);

        if (ApiClient.getInstance().isAuthenticated()) {
            loadConversations();
        } else {
            emptyView.setText("请先登录");
            emptyView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ApiClient.getInstance().isAuthenticated()) {
            loadConversations();
        }
    }

    private void loadConversations() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> data = apiService.getConversations();
                conversations.clear();
                if (data != null) conversations.addAll(data);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        emptyView.setVisibility(conversations.isEmpty() ? View.VISIBLE : View.GONE);
                        messageList.setVisibility(conversations.isEmpty() ? View.GONE : View.VISIBLE);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        emptyView.setVisibility(View.VISIBLE);
                        messageList.setVisibility(View.GONE);
                    });
                }
            }
        }).start();
    }

    private class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_conversation, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> conv = conversations.get(pos);
            Map<String, Object> other = (Map<String, Object>) conv.get("otherUser");
            if (other != null) {
                h.username.setText((String) other.get("username"));
                String avatar = (String) other.get("avatarUrl");
                if (avatar != null && !avatar.isEmpty()) {
                    Glide.with(h.avatar).load(avatar).circleCrop().into(h.avatar);
                } else {
                    h.avatar.setImageResource(R.drawable.ic_avatar_placeholder);
                }
            }

            h.lastMsg.setText((String) conv.get("lastMessage"));

            String timeStr = (String) conv.get("lastMessageAt");
            if (timeStr != null) {
                try {
                    Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(timeStr);
                    if (d != null) h.time.setText(sdf.format(d));
                } catch (Exception e) {
                    h.time.setText("");
                }
            }

            Object unreadObj = conv.get("unreadCount");
            int unread = unreadObj instanceof Number ? ((Number) unreadObj).intValue() : 0;
            if (unread > 0) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            } else {
                h.badge.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                String convId = (String) conv.get("conversationId");
                if (other != null && convId != null) {
                    Intent intent = new Intent(getContext(), ChatActivity.class);
                    intent.putExtra("conversation_id", convId);
                    intent.putExtra("other_user_id", (String) other.get("id"));
                    intent.putExtra("other_username", (String) other.get("username"));
                    intent.putExtra("other_avatar_url", (String) other.get("avatarUrl"));
                    startActivity(intent);
                }
            });
        }

        @Override public int getItemCount() { return conversations.size(); }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView avatar;
            TextView username, lastMsg, time, badge;
            VH(View v) {
                super(v);
                avatar = v.findViewById(R.id.conv_avatar);
                username = v.findViewById(R.id.conv_username);
                lastMsg = v.findViewById(R.id.conv_last_msg);
                time = v.findViewById(R.id.conv_time);
                badge = v.findViewById(R.id.conv_unread_badge);
            }
        }
    }
}

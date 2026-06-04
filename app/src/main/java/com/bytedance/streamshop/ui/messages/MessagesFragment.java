package com.bytedance.streamshop.ui.messages;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MessagesFragment extends Fragment {
    private static final String PREFS_NAME = "messages_prefs";
    private static final String KEY_PINNED = "pinned_ids";
    private static final String KEY_HIDDEN = "hidden_ids";

    private RecyclerView messageList;
    private TextView emptyView;
    private ConversationAdapter adapter;
    private ApiService apiService;
    private final List<Map<String, Object>> conversations = new ArrayList<>();
    private Set<String> pinnedIds = new HashSet<>();
    private Set<String> hiddenIds = new HashSet<>();

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

        loadPrefs();

        messageList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationAdapter();
        messageList.setAdapter(adapter);

        ImageButton searchBtn = view.findViewById(R.id.messages_search_btn);
        if (searchBtn != null) {
            searchBtn.setOnClickListener(v ->
                    startActivity(new Intent(getContext(), VideoSearchActivity.class)));
        }

        ImageButton addBtn = view.findViewById(R.id.messages_add_btn);
        if (addBtn != null) {
            addBtn.setOnClickListener(v -> {
                AddContactBottomSheetFragment sheet = AddContactBottomSheetFragment.newInstance();
                sheet.show(getParentFragmentManager(), "add_contact_sheet");
            });
        }

        if (ApiClient.getInstance().isAuthenticated()) {
            loadConversations();
        } else {
            emptyView.setText("请先登录");
            emptyView.setVisibility(View.VISIBLE);
        }
    }

    private void loadPrefs() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        pinnedIds = new HashSet<>(prefs.getStringSet(KEY_PINNED, new HashSet<>()));
        hiddenIds = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));
    }

    private void savePrefs() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_PINNED, new HashSet<>(pinnedIds)).apply();
        prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hiddenIds)).apply();
    }

    private void sortConversations() {
        Collections.sort(conversations, (a, b) -> {
            String aId = (String) a.get("conversationId");
            String bId = (String) b.get("conversationId");
            boolean aPin = pinnedIds.contains(aId);
            boolean bPin = pinnedIds.contains(bId);
            if (aPin && !bPin) return -1;
            if (!aPin && bPin) return 1;
            String aTime = (String) a.get("lastMessageAt");
            String bTime = (String) b.get("lastMessageAt");
            if (aTime == null) return 1;
            if (bTime == null) return -1;
            return bTime.compareTo(aTime);
        });
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
                if (data != null) {
                    for (Map<String, Object> conv : data) {
                        String convId = (String) conv.get("conversationId");
                        if (convId != null && hiddenIds.contains(convId)) continue;
                        conversations.add(conv);
                    }
                }
                sortConversations();
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
        private int openPosition = -1;
        private float buttonPanelWidth; // dp converted in onCreateViewHolder

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_conversation, parent, false);
            buttonPanelWidth = 120 * parent.getContext().getResources().getDisplayMetrics().density;
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> conv = conversations.get(pos);
            Boolean isGroup = (Boolean) conv.get("isGroup");
            String convId = (String) conv.get("conversationId");

            if (isGroup != null && isGroup) {
                h.username.setText((String) conv.get("groupName"));
                h.avatar.setVisibility(View.GONE);
                h.groupAvatarRoot.setVisibility(View.VISIBLE);

                List<Object> memberAvatars = (List<Object>) conv.get("memberAvatars");
                if (memberAvatars != null) {
                    for (int i = 0; i < 4; i++) {
                        if (i < memberAvatars.size()) {
                            String url = memberAvatars.get(i) instanceof String
                                    ? (String) memberAvatars.get(i) : null;
                            if (url != null && !url.isEmpty()) {
                                Glide.with(h.gridAvatars[i]).load(url).circleCrop().into(h.gridAvatars[i]);
                            } else {
                                h.gridAvatars[i].setImageResource(R.drawable.ic_avatar_placeholder);
                            }
                        } else {
                            h.gridAvatars[i].setImageResource(R.drawable.ic_avatar_placeholder);
                        }
                    }
                }
            } else {
                h.avatar.setVisibility(View.VISIBLE);
                h.groupAvatarRoot.setVisibility(View.GONE);
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
            }

            String msgType = (String) conv.get("lastMessageType");
            if ("forward".equals(msgType)) {
                h.lastMsg.setText("[视频转发]");
            } else {
                h.lastMsg.setText((String) conv.get("lastMessage"));
            }

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

            // Pin indicator & action button label
            boolean isPinned = pinnedIds.contains(convId);
            h.pinIndicator.setVisibility(isPinned ? View.VISIBLE : View.GONE);
            h.actionPin.setText(isPinned ? "取消置顶" : "置顶");

            // Reset swipe position on rebind
            h.foreground.setTranslationX(0f);

            // --- Swipe touch handling ---
            h.swipeStartX = 0;
            h.swipeStartTrans = 0;

            h.foreground.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (openPosition != -1 && openPosition != pos) {
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
                        messageList.requestDisallowInterceptTouchEvent(true);
                    }
                    v.setTranslationX(newTrans);
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    messageList.requestDisallowInterceptTouchEvent(false);
                    float currentTrans = v.getTranslationX();
                    float threshold = -buttonPanelWidth * 0.4f;

                    if (Math.abs(currentTrans - h.swipeStartTrans) < 10) {
                        if (openPosition == pos) {
                            v.animate().translationX(0).setDuration(150).start();
                            openPosition = -1;
                        } else {
                            v.animate().translationX(0).setDuration(100).start();
                            openPosition = -1;
                            openConversation(conv, convId, isGroup);
                        }
                    } else if (currentTrans < threshold) {
                        v.animate().translationX(-buttonPanelWidth).setDuration(150).start();
                        openPosition = pos;
                    } else {
                        v.animate().translationX(0).setDuration(150).start();
                        openPosition = -1;
                    }
                    return true;
                }
                return false;
            });

            // Action button clicks
            h.actionPin.setOnClickListener(v -> {
                closeOpenItem();
                if (pinnedIds.contains(convId)) {
                    pinnedIds.remove(convId);
                } else {
                    pinnedIds.add(convId);
                }
                savePrefs();
                sortConversations();
                notifyDataSetChanged();
            });

            h.actionDelete.setOnClickListener(v -> {
                openPosition = -1;
                hiddenIds.add(convId);
                savePrefs();
                conversations.remove(pos);
                notifyItemRemoved(pos);
                // Update empty view when last item is deleted
                if (conversations.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    messageList.setVisibility(View.GONE);
                }
            });
        }

        private void closeOpenItem() {
            if (openPosition != -1) {
                int old = openPosition;
                openPosition = -1;
                notifyItemChanged(old);
            }
        }

        private void openConversation(Map<String, Object> conv, String convId, Boolean isGroup) {
            if (convId == null) return;
            if (isGroup != null && isGroup) {
                Intent intent = new Intent(getContext(), ChatActivity.class);
                intent.putExtra("conversation_id", convId);
                intent.putExtra("is_group", true);
                intent.putExtra("group_name", (String) conv.get("groupName"));
                startActivity(intent);
            } else {
                Map<String, Object> other = (Map<String, Object>) conv.get("otherUser");
                if (other != null) {
                    Intent intent = new Intent(getContext(), ChatActivity.class);
                    intent.putExtra("conversation_id", convId);
                    intent.putExtra("other_user_id", (String) other.get("id"));
                    intent.putExtra("other_username", (String) other.get("username"));
                    intent.putExtra("other_avatar_url", (String) other.get("avatarUrl"));
                    startActivity(intent);
                }
            }
        }

        @Override public int getItemCount() { return conversations.size(); }

        class VH extends RecyclerView.ViewHolder {
            View foreground, groupAvatarRoot;
            TextView actionPin, actionDelete;
            ShapeableImageView avatar;
            ShapeableImageView[] gridAvatars = new ShapeableImageView[4];
            TextView username, lastMsg, time, badge, pinIndicator;
            float swipeStartX, swipeStartTrans;

            VH(View v) {
                super(v);
                foreground = v.findViewById(R.id.conv_foreground);
                actionPin = v.findViewById(R.id.conv_action_pin);
                actionDelete = v.findViewById(R.id.conv_action_delete);
                avatar = v.findViewById(R.id.conv_avatar);
                groupAvatarRoot = v.findViewById(R.id.conv_group_avatar);
                gridAvatars[0] = v.findViewById(R.id.grid_avatar_0);
                gridAvatars[1] = v.findViewById(R.id.grid_avatar_1);
                gridAvatars[2] = v.findViewById(R.id.grid_avatar_2);
                gridAvatars[3] = v.findViewById(R.id.grid_avatar_3);
                username = v.findViewById(R.id.conv_username);
                lastMsg = v.findViewById(R.id.conv_last_msg);
                time = v.findViewById(R.id.conv_time);
                badge = v.findViewById(R.id.conv_unread_badge);
                pinIndicator = v.findViewById(R.id.conv_pin_indicator);
            }
        }
    }
}

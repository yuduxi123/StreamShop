package com.bytedance.streamshop.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ForwardUserBottomSheetFragment extends BottomSheetDialogFragment {
    private static final String ARG_VIDEO_ID = "videoId";

    public interface OnForwardListener {
        void onForwarded(int newShareCount);
    }

    private String videoId;
    private ApiService apiService;
    private RecyclerView userList;
    private TextView emptyText;
    private UserAdapter adapter;
    private final List<Map<String, Object>> users = new ArrayList<>();
    private OnForwardListener forwardListener;

    public static ForwardUserBottomSheetFragment newInstance(String videoId) {
        ForwardUserBottomSheetFragment fragment = new ForwardUserBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_ID, videoId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnForwardListener(OnForwardListener listener) {
        this.forwardListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            videoId = getArguments().getString(ARG_VIDEO_ID);
        }
        apiService = new ApiService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forward_user_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        userList = view.findViewById(R.id.forward_user_list);
        emptyText = view.findViewById(R.id.forward_empty_text);

        userList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UserAdapter();
        userList.setAdapter(adapter);

        loadFollowing();
    }

    private void loadFollowing() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> following = apiService.getFollowing();
                users.clear();
                if (following != null) users.addAll(following);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        if (users.isEmpty()) {
                            emptyText.setVisibility(View.VISIBLE);
                            userList.setVisibility(View.GONE);
                        } else {
                            emptyText.setVisibility(View.GONE);
                            userList.setVisibility(View.VISIBLE);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        emptyText.setText("加载失败");
                        emptyText.setVisibility(View.VISIBLE);
                        userList.setVisibility(View.GONE);
                    });
                }
            }
        }).start();
    }

    private void forwardTo(int position) {
        Map<String, Object> item = users.get(position);
        Map<String, Object> user = (Map<String, Object>) item.get("user");
        if (user == null) return;
        String targetUserId = (String) user.get("id");

        new Thread(() -> {
            try {
                Map<String, Object> result = apiService.forwardVideo(videoId, targetUserId);
                Object shareCountObj = result.get("shareCount");
                int newShareCount = shareCountObj instanceof Number ? ((Number) shareCountObj).intValue() : 0;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "已转发", Toast.LENGTH_SHORT).show();
                        if (forwardListener != null) forwardListener.onForwarded(newShareCount);
                        dismiss();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "转发失败", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_forward_user, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> item = users.get(pos);
            Map<String, Object> user = (Map<String, Object>) item.get("user");
            if (user != null) {
                h.username.setText((String) user.get("username"));
                String avatar = (String) user.get("avatarUrl");
                if (avatar != null && !avatar.isEmpty()) {
                    Glide.with(h.avatar).load(avatar).circleCrop().into(h.avatar);
                } else {
                    h.avatar.setImageResource(R.drawable.ic_avatar_placeholder);
                }
            }
            h.itemView.setOnClickListener(v -> forwardTo(pos));
        }

        @Override
        public int getItemCount() { return users.size(); }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView avatar;
            TextView username;
            VH(View v) {
                super(v);
                avatar = v.findViewById(R.id.forward_user_avatar);
                username = v.findViewById(R.id.forward_user_username);
            }
        }
    }
}

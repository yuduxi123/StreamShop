package com.bytedance.streamshop.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.Comment;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class CommentBottomSheetFragment extends BottomSheetDialogFragment {
    private static final String ARG_TARGET_TYPE = "targetType";
    private static final String ARG_TARGET_ID = "targetId";

    private String targetType;
    private String targetId;
    private ApiService apiService;
    private List<Comment> comments = new ArrayList<>();
    private CommentAdapter adapter;

    private RecyclerView commentList;
    private TextView countHeader;
    private EditText inputView;
    private MaterialButton sendBtn;

    public static CommentBottomSheetFragment newInstance(String targetType, String targetId) {
        CommentBottomSheetFragment fragment = new CommentBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET_TYPE, targetType);
        args.putString(ARG_TARGET_ID, targetId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            targetType = getArguments().getString(ARG_TARGET_TYPE);
            targetId = getArguments().getString(ARG_TARGET_ID);
        }
        apiService = new ApiService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comment_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadComments();
    }

    private void initViews(View view) {
        commentList = view.findViewById(R.id.comment_list);
        countHeader = view.findViewById(R.id.comment_count_header);
        inputView = view.findViewById(R.id.comment_input);
        sendBtn = view.findViewById(R.id.comment_send_btn);

        commentList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CommentAdapter();
        commentList.setAdapter(adapter);

        sendBtn.setOnClickListener(v -> postComment());
    }

    private void loadComments() {
        new Thread(() -> {
            try {
                var response = apiService.getComments(targetType, targetId, 1, 50);
                comments = response.getData();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        updateCount();
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void postComment() {
        String content = inputView.getText().toString().trim();
        if (content.isEmpty()) return;

        sendBtn.setEnabled(false);
        new Thread(() -> {
            try {
                Comment comment = apiService.postComment(targetType, targetId, content);
                comments.add(0, comment);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        inputView.setText("");
                        adapter.notifyItemInserted(0);
                        commentList.scrollToPosition(0);
                        updateCount();
                        sendBtn.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        sendBtn.setEnabled(true);
                    });
                }
            }
        }).start();
    }

    private void updateCount() {
        countHeader.setText(comments.size() + "条");
    }

    private class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.item_comment, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Comment comment = comments.get(position);
            if (comment.getUser() != null) {
                holder.username.setText(comment.getUser().getUsername());
                Glide.with(holder.itemView)
                        .load(comment.getUser().getAvatarUrl())
                        .circleCrop()
                        .into(holder.avatar);
            }
            holder.content.setText(comment.getContent());
            holder.time.setText(formatTime(comment.getCreatedAt()));
        }

        @Override
        public int getItemCount() {
            return comments.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView avatar;
            TextView username, content, time;

            ViewHolder(View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.comment_avatar);
                username = itemView.findViewById(R.id.comment_username);
                content = itemView.findViewById(R.id.comment_content);
                time = itemView.findViewById(R.id.comment_time);
            }
        }

        private String formatTime(String isoTime) {
            try {
                long time = java.time.Instant.parse(isoTime).toEpochMilli();
                long diff = System.currentTimeMillis() - time;
                if (diff < 60000) return "刚刚";
                if (diff < 3600000) return (diff / 60000) + "分钟前";
                if (diff < 86400000) return (diff / 3600000) + "小时前";
                return (diff / 86400000) + "天前";
            } catch (Exception e) {
                return isoTime;
            }
        }
    }
}

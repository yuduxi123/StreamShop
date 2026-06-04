package com.bytedance.streamshop.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.User;
import com.bytedance.streamshop.domain.model.Video;
import com.bytedance.streamshop.ui.analytics.AnalyticsDashboardActivity;
import com.bytedance.streamshop.ui.login.LoginActivity;
import com.bytedance.streamshop.ui.order.OrderListActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private View headerLoggedOut;
    private View headerLoggedIn;
    private ShapeableImageView avatarView;
    private ShapeableImageView avatarViewLoggedOut;
    private TextView usernameText;
    private TextView usernameTextLoggedOut;
    private TextView accountText;
    private TextView loginBtn;
    private MaterialButton logoutBtn;
    private View statsRow;
    private View actionsRow;
    private RecyclerView videoGrid;
    private TextView likesCountText;
    private TextView followingCountText;
    private TextView followersCountText;

    private ImageButton editNameBtn;
    private View liveRoomsIcon;

    private final List<Video> myVideos = new ArrayList<>();
    private VideoGridAdapter gridAdapter;

    private final ActivityResultLauncher<Intent> loginLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    updateAuthState();
                }
            });

    private final ActivityResultLauncher<CropImageContractOptions> cropLauncher =
            registerForActivityResult(new CropImageContract(), result -> {
                if (result.getError() != null) {
                    Toast.makeText(getContext(), "裁剪失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.getUriContent() != null) {
                    onAvatarCropped(result.getUriContent());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupVideoGrid();
        updateAuthState();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAuthState();
    }

    private void initViews(View view) {
        headerLoggedOut = view.findViewById(R.id.profile_header_logged_out);
        headerLoggedIn = view.findViewById(R.id.profile_header_logged_in);
        avatarView = view.findViewById(R.id.profile_avatar);
        avatarViewLoggedOut = view.findViewById(R.id.profile_avatar_logged_out);
        usernameText = view.findViewById(R.id.profile_username);
        usernameTextLoggedOut = view.findViewById(R.id.profile_username_logged_out);
        accountText = view.findViewById(R.id.profile_account);
        editNameBtn = view.findViewById(R.id.profile_edit_name_btn);
        loginBtn = view.findViewById(R.id.profile_login_btn);
        logoutBtn = view.findViewById(R.id.profile_logout_btn);
        statsRow = view.findViewById(R.id.profile_stats_row);
        actionsRow = view.findViewById(R.id.profile_actions_row);
        videoGrid = view.findViewById(R.id.profile_video_grid);
        likesCountText = view.findViewById(R.id.profile_likes_count);
        followingCountText = view.findViewById(R.id.profile_following_count);
        followersCountText = view.findViewById(R.id.profile_followers_count);

        view.findViewById(R.id.profile_orders).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                startActivity(new Intent(getActivity(), OrderListActivity.class));
            }
        });

        view.findViewById(R.id.profile_likes).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                startActivity(new Intent(getActivity(), LikedVideosActivity.class));
            } else {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.profile_videos).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                startActivity(new Intent(getActivity(), MyVideosActivity.class));
            } else {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        liveRoomsIcon = view.findViewById(R.id.profile_live_rooms_icon);
        liveRoomsIcon.setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                startActivity(new Intent(getActivity(), com.bytedance.streamshop.ui.live.MyLiveRoomsActivity.class));
            } else {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.profile_collections).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                startActivity(new Intent(getActivity(), CollectionsActivity.class));
            } else {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.profile_analytics).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                startActivity(new Intent(getActivity(), AnalyticsDashboardActivity.class));
            } else {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        loginBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            loginLauncher.launch(intent);
        });

        logoutBtn.setOnClickListener(v -> {
            ApiClient.getInstance().clearAuthToken();
            updateAuthState();
        });

        // Stats click listeners
        view.findViewById(R.id.profile_likes_stat).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                startActivity(new Intent(getActivity(), LikedVideosActivity.class));
            } else {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.profile_following_stat).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                Intent intent = new Intent(getActivity(), FollowListActivity.class);
                intent.putExtra(FollowListActivity.EXTRA_MODE, FollowListActivity.MODE_FOLLOWING);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.profile_followers_stat).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                Intent intent = new Intent(getActivity(), FollowListActivity.class);
                intent.putExtra(FollowListActivity.EXTRA_MODE, FollowListActivity.MODE_FOLLOWERS);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        avatarView.setOnClickListener(v -> {
            if (!ApiClient.getInstance().isAuthenticated()) return;
            CropImageOptions options = new CropImageOptions();
            options.guidelines = CropImageView.Guidelines.ON;
            options.cropShape = CropImageView.CropShape.OVAL;
            options.aspectRatioX = 1;
            options.aspectRatioY = 1;
            options.fixAspectRatio = true;
            cropLauncher.launch(new CropImageContractOptions(null, options));
        });

        editNameBtn.setOnClickListener(v -> showEditNameDialog());
    }

    private void showEditNameDialog() {
        EditText input = new EditText(getContext());
        input.setText(ApiClient.getInstance().getCurrentUsername());
        input.setPadding(48, 48, 48, 48);
        input.setTextColor(0xFF333333);
        input.setTextSize(16);

        new AlertDialog.Builder(getContext())
                .setTitle("修改名字")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateUsername(newName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateUsername(String newName) {
        editNameBtn.setEnabled(false);
        new Thread(() -> {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("username", newName);
                new ApiService().updateMyProfile(updates);
                ApiClient.getInstance().setCurrentUsername(newName);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        usernameText.setText(newName);
                        editNameBtn.setEnabled(true);
                        Toast.makeText(getContext(), "名字已更新", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        editNameBtn.setEnabled(true);
                        Toast.makeText(getContext(), "修改失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    private void setupVideoGrid() {
        gridAdapter = new VideoGridAdapter();
        videoGrid.setLayoutManager(new GridLayoutManager(getContext(), 2));
        videoGrid.setAdapter(gridAdapter);
    }

    private void onAvatarCropped(Uri croppedUri) {
        new Thread(() -> {
            try {
                File tempFile = new File(requireContext().getCacheDir(), "avatar_cropped.jpg");
                try (InputStream in = requireContext().getContentResolver().openInputStream(croppedUri);
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    if (in == null) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "读取图片失败", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }

                ApiService api = new ApiService();
                Map<String, Object> uploadResult = api.uploadFile(tempFile.getAbsolutePath(), "image/jpeg");
                String url = (String) uploadResult.get("url");

                Map<String, Object> updates = new HashMap<>();
                updates.put("avatarUrl", url);
                api.updateMyProfile(updates);

                ApiClient.getInstance().setCurrentAvatarUrl(url);
                requireActivity().runOnUiThread(() -> {
                    loadAvatar(url);
                    Toast.makeText(getContext(), "头像更新成功", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateAuthState() {
        boolean loggedIn = ApiClient.getInstance().isAuthenticated();
        if (loggedIn) {
            headerLoggedOut.setVisibility(View.GONE);
            headerLoggedIn.setVisibility(View.VISIBLE);
            String username = ApiClient.getInstance().getCurrentUsername();
            String account = ApiClient.getInstance().getCurrentAccount();
            usernameText.setText(username != null ? username : "已登录");
            accountText.setText(account != null ? "账号：" + account : "");
            loginBtn.setVisibility(View.GONE);
            logoutBtn.setVisibility(View.VISIBLE);
            statsRow.setVisibility(View.VISIBLE);
            actionsRow.setVisibility(View.VISIBLE);
            liveRoomsIcon.setVisibility(View.VISIBLE);

            String avatarUrl = ApiClient.getInstance().getCurrentAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                loadAvatar(avatarUrl);
            } else {
                avatarView.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            loadMyVideos();
        } else {
            headerLoggedOut.setVisibility(View.VISIBLE);
            headerLoggedIn.setVisibility(View.GONE);
            usernameTextLoggedOut.setText("未登录");
            loginBtn.setText("点击登录");
            loginBtn.setVisibility(View.VISIBLE);
            logoutBtn.setVisibility(View.GONE);
            statsRow.setVisibility(View.GONE);
            actionsRow.setVisibility(View.GONE);
            liveRoomsIcon.setVisibility(View.GONE);
            avatarViewLoggedOut.setImageResource(R.drawable.ic_avatar_placeholder);
            myVideos.clear();
            gridAdapter.notifyDataSetChanged();
        }
    }

    private void loadStats() {
        new Thread(() -> {
            try {
                String userId = ApiClient.getInstance().getCurrentUserId();
                if (userId == null) return;
                User user = new ApiService().getUserProfile(userId);
                final int likesReceived = user.getLikesReceived();
                final int following = user.getFollowing();
                final int followers = user.getFollowers();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        likesCountText.setText(formatCount(likesReceived));
                        followingCountText.setText(formatCount(following));
                        followersCountText.setText(formatCount(followers));
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        likesCountText.setText("0");
                        followingCountText.setText("0");
                        followersCountText.setText("0");
                    });
                }
            }
        }).start();
    }

    private void loadMyVideos() {
        new Thread(() -> {
            try {
                List<Video> data = new ApiService().getMyVideos(1, 100).getData();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        myVideos.clear();
                        if (data != null) myVideos.addAll(data);
                        gridAdapter.notifyDataSetChanged();
                        videoGrid.setVisibility(myVideos.isEmpty() ? View.GONE : View.VISIBLE);
                        loadStats();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        videoGrid.setVisibility(View.GONE);
                    });
                }
            }
        }).start();
    }

    private void loadAvatar(String url) {
        if (getContext() == null) return;
        Glide.with(this)
                .load(url)
                .circleCrop()
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .into(avatarView);
    }

    private String formatCount(Number num) {
        long n = num.longValue();
        if (n >= 10000) return (n / 1000 / 10.0) + "w";
        if (n >= 1000) return (n / 100 / 10.0) + "k";
        return String.valueOf(n);
    }

    // --- Video Grid Adapter ---

    private class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_profile_video_grid, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Video v = myVideos.get(pos);
            Glide.with(h.itemView).load(v.getCoverUrl()).into(h.cover);
            h.likes.setText(formatCount(v.getLikeCount()));

            h.itemView.setOnClickListener(view -> {
                String userId = ApiClient.getInstance().getCurrentUserId();
                if (userId != null) {
                    Intent intent = new Intent(getContext(), AuthorVideoFeedActivity.class);
                    intent.putExtra("author_id", userId);
                    intent.putExtra("start_position", pos);
                    startActivity(intent);
                }
            });
        }

        @Override public int getItemCount() { return myVideos.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView likes;
            VH(View v) {
                super(v);
                cover = v.findViewById(R.id.grid_video_cover);
                likes = v.findViewById(R.id.grid_video_likes);
            }
        }
    }
}

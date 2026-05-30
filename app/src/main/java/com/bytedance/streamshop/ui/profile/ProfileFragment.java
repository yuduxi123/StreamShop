package com.bytedance.streamshop.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.ui.login.LoginActivity;
import com.bytedance.streamshop.ui.order.OrderListActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private ShapeableImageView avatarView;
    private TextView usernameText;
    private TextView loginBtn;
    private MaterialButton logoutBtn;

    private final ActivityResultLauncher<Intent> loginLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    updateAuthState();
                }
            });

    // Crop launcher
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
        updateAuthState();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAuthState();
    }

    private void initViews(View view) {
        avatarView = view.findViewById(R.id.profile_avatar);
        usernameText = view.findViewById(R.id.profile_username);
        loginBtn = view.findViewById(R.id.profile_login_btn);
        logoutBtn = view.findViewById(R.id.profile_logout_btn);

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

        view.findViewById(R.id.profile_collections).setOnClickListener(v -> {
            if (ApiClient.getInstance().isAuthenticated()) {
                startActivity(new Intent(getActivity(), CollectionsActivity.class));
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

        // Avatar click: pick and crop
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
    }

    private void onAvatarCropped(Uri croppedUri) {
        new Thread(() -> {
            try {
                // Copy cropped image to temp file
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

                // Upload
                ApiService api = new ApiService();
                Map<String, Object> uploadResult = api.uploadFile(tempFile.getAbsolutePath(), "image/jpeg");
                String url = (String) uploadResult.get("url");

                // Update profile
                Map<String, Object> updates = new HashMap<>();
                updates.put("avatarUrl", url);
                api.updateMyProfile(updates);

                // Cache and refresh
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
            String username = ApiClient.getInstance().getCurrentUsername();
            usernameText.setText(username != null ? username : "已登录");
            loginBtn.setVisibility(View.GONE);
            logoutBtn.setVisibility(View.VISIBLE);

            String avatarUrl = ApiClient.getInstance().getCurrentAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                loadAvatar(avatarUrl);
            } else {
                avatarView.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        } else {
            usernameText.setText("未登录");
            loginBtn.setText("点击登录");
            loginBtn.setVisibility(View.VISIBLE);
            logoutBtn.setVisibility(View.GONE);
            avatarView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
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
}

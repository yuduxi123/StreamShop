package com.bytedance.streamshop.ui.profile;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.User;
import com.bumptech.glide.Glide;

public class AuthorProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private ImageView avatarView;
    private TextView usernameText;
    private TextView bioText;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author_profile);

        avatarView = findViewById(R.id.author_avatar);
        usernameText = findViewById(R.id.author_username);
        bioText = findViewById(R.id.author_bio);
        apiService = new ApiService();

        findViewById(R.id.author_back).setOnClickListener(v -> finish());

        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userId != null) {
            loadUserProfile(userId);
        }
    }

    private void loadUserProfile(String userId) {
        new Thread(() -> {
            try {
                User user = apiService.getUserProfile(userId);
                runOnUiThread(() -> {
                    usernameText.setText(user.getUsername());
                    bioText.setText("StreamShop Creator");
                    Glide.with(this)
                            .load(user.getAvatarUrl())
                            .circleCrop()
                            .into(avatarView);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }
}

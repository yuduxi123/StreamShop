package com.bytedance.streamshop.ui.feed;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.data.remote.LiveWebSocketClient;
import com.bytedance.streamshop.domain.model.Danmaku;
import com.bytedance.streamshop.domain.model.Product;
import com.bytedance.streamshop.domain.model.Video;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class ForwardedVideoActivity extends AppCompatActivity {
    private String videoId;
    private Video video;
    private ApiService apiService;
    private ExoPlayer player;
    private PlayerView playerView;
    private boolean isPlaying;
    private boolean viewReported;
    private boolean fallbackTried;

    private ShapeableImageView avatarView;
    private TextView usernameText;
    private TextView titleText;
    private TextView likeCountText;
    private TextView commentCountText;
    private TextView collectCountText;
    private TextView forwardCountText;
    private ImageButton likeButton;
    private ImageButton commentButton;
    private ImageButton collectButton;
    private ImageButton forwardButton;
    private ImageView followButton;
    private ViewGroup productContainer;
    private ImageView coverView;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView durationText;
    private ImageView playIndicator;
    private View videoRoot;
    private VideoDanmakuView danmakuView;
    private ImageButton danmakuButton;
    private LiveWebSocketClient wsClient;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean isUserSeeking;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forwarded_video);

        videoId = getIntent().getStringExtra("video_id");
        if (videoId == null) {
            finish();
            return;
        }

        apiService = new ApiService();
        initViews();
        loadVideo();
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        coverView = findViewById(R.id.video_cover);
        avatarView = findViewById(R.id.video_avatar);
        usernameText = findViewById(R.id.video_username);
        titleText = findViewById(R.id.video_title);
        likeCountText = findViewById(R.id.video_like_count);
        commentCountText = findViewById(R.id.video_comment_count);
        collectCountText = findViewById(R.id.video_collect_count);
        forwardCountText = findViewById(R.id.video_forward_count);
        likeButton = findViewById(R.id.video_like_btn);
        commentButton = findViewById(R.id.video_comment_btn);
        collectButton = findViewById(R.id.video_collect_btn);
        forwardButton = findViewById(R.id.video_forward_btn);
        followButton = findViewById(R.id.video_follow_btn);
        productContainer = findViewById(R.id.product_card_container);
        seekBar = findViewById(R.id.video_seekbar);
        currentTimeText = findViewById(R.id.video_current_time);
        durationText = findViewById(R.id.video_duration);
        playIndicator = findViewById(R.id.video_play_indicator);
        videoRoot = findViewById(R.id.video_root);
        danmakuView = findViewById(R.id.video_danmaku_view);
        danmakuButton = findViewById(R.id.video_danmaku_btn);

        findViewById(R.id.forwarded_back_btn).setOnClickListener(v -> finish());

        if (danmakuButton != null) danmakuButton.setOnClickListener(v -> showDanmakuInput());
        if (likeButton != null) likeButton.setOnClickListener(v -> toggleLike());
        if (commentButton != null) commentButton.setOnClickListener(v -> showComments());
        if (collectButton != null) collectButton.setOnClickListener(v -> toggleCollect());
        if (forwardButton != null) forwardButton.setOnClickListener(v -> showForwardSheet());
        if (followButton != null) followButton.setOnClickListener(v -> toggleFollow());

        if (videoRoot != null) videoRoot.setOnClickListener(v -> togglePlayPause());

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { isUserSeeking = true; }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isUserSeeking = false;
                    if (player != null) {
                        long duration = player.getDuration();
                        if (duration > 0) {
                            long position = duration * seekBar.getProgress() / seekBar.getMax();
                            player.seekTo(position);
                        }
                    }
                }
            });
        }
    }

    private void loadVideo() {
        new Thread(() -> {
            try {
                video = apiService.getVideoById(videoId);
                if (video == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
                runOnUiThread(() -> {
                    bindVideo();
                    setupProductCards();
                    initPlayer();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载视频失败", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void bindVideo() {
        if (video == null) return;

        if (video.getCoverUrl() != null && coverView != null) {
            Glide.with(this).load(video.getCoverUrl()).centerCrop().into(coverView);
        }

        if (video.getAuthor() != null) {
            if (usernameText != null) usernameText.setText(video.getAuthor().getUsername());
            if (avatarView != null) {
                Glide.with(this).load(video.getAuthor().getAvatarUrl()).circleCrop().skipMemoryCache(true).into(avatarView);
            }
        }

        if (titleText != null) titleText.setText(video.getTitle());
        if (likeCountText != null) likeCountText.setText(formatCount(video.getLikeCount()));
        if (commentCountText != null) commentCountText.setText(formatCount(video.getCommentCount()));
        if (forwardCountText != null) forwardCountText.setText(formatCount(video.getShareCount()));

        loadInteractionStates();
    }

    private void loadInteractionStates() {
        if (video == null) return;
        if (likeButton != null) {
            new Thread(() -> {
                try {
                    Map<String, Object> status = apiService.getLikeStatus("video", video.getId());
                    Boolean liked = (Boolean) status.get("liked");
                    runOnUiThread(() -> { if (likeButton != null && liked != null) likeButton.setSelected(liked); });
                } catch (Exception ignored) {}
            }).start();
        }
        if (collectButton != null) {
            new Thread(() -> {
                try {
                    Map<String, Object> status = apiService.getCollectionStatus("video", video.getId());
                    Boolean collected = (Boolean) status.get("collected");
                    runOnUiThread(() -> { if (collectButton != null && collected != null) collectButton.setSelected(collected); });
                } catch (Exception ignored) {}
            }).start();
        }
        if (video.getAuthor() != null && followButton != null) {
            new Thread(() -> {
                try {
                    boolean following = apiService.isFollowing(video.getAuthor().getId());
                    runOnUiThread(() -> followButton.setSelected(following));
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private void initPlayer() {
        if (video == null || video.getVideoUrl() == null) return;
        if (playerView == null) return;

        releasePlayer();

        try {
            player = new ExoPlayer.Builder(this).build();
        } catch (Exception e) {
            return;
        }

        playerView.setPlayer(player);
        playerView.setUseController(false);

        prepareAndPlay(video.getVideoUrl());

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    if (coverView != null) coverView.setVisibility(View.GONE);
                    reportViewIfNeeded();
                    if (isPlaying && player != null) player.play();
                    startProgressUpdates();
                }
            }

            @Override
            public void onPlayerError(@Nullable PlaybackException error) {
                if (coverView != null) coverView.setVisibility(View.VISIBLE);
                tryFallbackSource();
            }
        });

        player.setPlayWhenReady(true);
        isPlaying = true;

        loadDanmaku();
        connectDanmakuWebSocket();
    }

    private void prepareAndPlay(String url) {
        if (player == null || url == null || url.trim().isEmpty()) return;
        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
        isPlaying = true;
    }

    private void tryFallbackSource() {
        if (player == null || fallbackTried) return;
        fallbackTried = true;
        prepareAndPlay("https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4");
    }

    private void reportViewIfNeeded() {
        if (video == null || viewReported) return;
        viewReported = true;
        new Thread(() -> {
            try { apiService.incrementVideoView(video.getId()); } catch (Exception ignored) {}
        }).start();
    }

    private void play() {
        if (player != null) {
            isPlaying = true;
            player.setPlayWhenReady(true);
            if (danmakuView != null) danmakuView.play();
            startProgressUpdates();
        }
    }

    private void pause() {
        if (player != null) {
            isPlaying = false;
            player.setPlayWhenReady(false);
            if (danmakuView != null) danmakuView.pause();
            stopProgressUpdates();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressUpdates();
        releasePlayer();
        if (danmakuView != null) danmakuView.release();
        if (wsClient != null) wsClient.disconnect();
    }

    private void releasePlayer() {
        if (player != null) {
            isPlaying = false;
            player.stop();
            player.release();
            player = null;
        }
    }

    private void setupProductCards() {
        if (video == null || video.getProducts() == null || video.getProducts().isEmpty()) {
            if (productContainer != null) productContainer.setVisibility(View.GONE);
            return;
        }
        if (productContainer == null) return;

        productContainer.setVisibility(View.VISIBLE);
        productContainer.removeAllViews();

        for (Product product : video.getProducts()) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_product_card, productContainer, false);

            ImageView thumb = card.findViewById(R.id.product_thumb);
            TextView titleText = card.findViewById(R.id.product_title);
            TextView priceText = card.findViewById(R.id.product_price);
            TextView originalPriceText = card.findViewById(R.id.product_original_price);
            TextView discountText = card.findViewById(R.id.product_discount);
            TextView stockInfoText = card.findViewById(R.id.product_stock_info);

            if (thumb != null) Glide.with(this).load(product.getCoverUrl()).into(thumb);
            if (titleText != null) titleText.setText(product.getTitle());

            int price = (int) product.getPrice();
            if (priceText != null) priceText.setText("¥" + price);

            double originalPrice = product.getOriginalPrice();
            if (originalPrice > 0 && originalPrice > product.getPrice() && originalPriceText != null && discountText != null) {
                originalPriceText.setVisibility(View.VISIBLE);
                originalPriceText.setText("¥" + (int) originalPrice);
                originalPriceText.setPaintFlags(originalPriceText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                int discount = (int) (product.getPrice() * 100 / originalPrice);
                discountText.setVisibility(View.VISIBLE);
                discountText.setText(discount + "折");
            }

            if (stockInfoText != null) {
                if (product.getStock() <= 0) {
                    stockInfoText.setText("已售罄");
                    stockInfoText.setTextColor(0xFFFF3B30);
                } else {
                    stockInfoText.setText("已售" + formatCount(product.getSalesCount()));
                    stockInfoText.setTextColor(0xFF999999);
                }
            }

            card.setOnClickListener(v -> showProductDetail(product));
            productContainer.addView(card);
        }
    }

    private void toggleLike() {
        if (video == null || likeButton == null) return;
        likeButton.setEnabled(false);
        new Thread(() -> {
            try {
                boolean liked = apiService.toggleLike("video", video.getId());
                runOnUiThread(() -> {
                    likeButton.setSelected(liked);
                    int count = video.getLikeCount() + (liked ? 1 : -1);
                    video.setLikeCount(Math.max(0, count));
                    likeCountText.setText(formatCount(video.getLikeCount()));
                    likeButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (likeButton != null) likeButton.setEnabled(true); });
            }
        }).start();
    }

    private void toggleCollect() {
        if (video == null || collectButton == null) return;
        collectButton.setEnabled(false);
        new Thread(() -> {
            try {
                boolean collected = apiService.toggleCollection("video", video.getId());
                runOnUiThread(() -> {
                    collectButton.setSelected(collected);
                    collectButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (collectButton != null) collectButton.setEnabled(true); });
            }
        }).start();
    }

    private void toggleFollow() {
        if (video == null || video.getAuthor() == null || followButton == null) return;
        followButton.setEnabled(false);
        new Thread(() -> {
            try {
                boolean following = apiService.toggleFollow(video.getAuthor().getId());
                runOnUiThread(() -> {
                    followButton.setSelected(following);
                    followButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (followButton != null) followButton.setEnabled(true); });
            }
        }).start();
    }

    private void showComments() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm != null && video != null) {
            CommentBottomSheetFragment sheet = CommentBottomSheetFragment.newInstance("video", video.getId());
            sheet.show(fm, "comment");
        }
    }

    private void showProductDetail(Product product) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm != null) {
            ProductDetailBottomSheetFragment sheet = ProductDetailBottomSheetFragment.newInstance(product);
            sheet.show(fm, "product_detail");
        }
    }

    private void showForwardSheet() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm != null && video != null) {
            ForwardUserBottomSheetFragment sheet = ForwardUserBottomSheetFragment.newInstance(video.getId());
            sheet.setOnForwardListener(newShareCount -> {
                if (video != null) {
                    video.setShareCount(newShareCount);
                    if (forwardCountText != null) forwardCountText.setText(formatCount(newShareCount));
                }
            });
            sheet.show(fm, "forward");
        }
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (isPlaying) {
            pause();
            showPlayIndicator();
        } else {
            resumePlay();
        }
    }

    private void resumePlay() {
        play();
        if (playIndicator != null) playIndicator.setVisibility(View.GONE);
    }

    private void showPlayIndicator() {
        if (playIndicator == null) return;
        playIndicator.setVisibility(View.VISIBLE);
        playIndicator.setOnClickListener(v -> resumePlay());
    }

    private String formatCount(int count) {
        if (count >= 10000) return count / 10000 + "w";
        else if (count >= 1000) return count / 1000 + "k";
        return String.valueOf(count);
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player == null || !isPlaying) return;
            if (seekBar == null || currentTimeText == null || durationText == null) return;
            if (!isUserSeeking) {
                long position = player.getCurrentPosition();
                long duration = player.getDuration();
                if (duration > 0) {
                    int progress = (int) (position * seekBar.getMax() / duration);
                    seekBar.setProgress(progress);
                }
                currentTimeText.setText(formatTime(position));
                durationText.setText(formatTime(duration));
                if (danmakuView != null) danmakuView.setPlaybackPosition(position);
            }
            progressHandler.postDelayed(this, 300);
        }
    };

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private String formatTime(long ms) {
        if (ms < 0) return "00:00";
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // --- Danmaku ---

    private void loadDanmaku() {
        if (video == null || danmakuView == null) return;
        new Thread(() -> {
            try {
                List<Danmaku> list = apiService.getDanmaku(video.getId());
                runOnUiThread(() -> danmakuView.loadDanmaku(list));
            } catch (Exception ignored) {}
        }).start();
    }

    private void connectDanmakuWebSocket() {
        if (video == null || !ApiClient.getInstance().isAuthenticated()) return;
        String userId = ApiClient.getInstance().getCurrentUserId();
        String username = ApiClient.getInstance().getCurrentUsername();
        if (userId == null) return;

        if (wsClient != null) wsClient.disconnect();
        wsClient = new LiveWebSocketClient();
        wsClient.connect("video_" + video.getId(), userId, username, new LiveWebSocketClient.WsCallback() {
            @Override
            public void onNewDanmaku(JSONObject msg) {
                try {
                    Danmaku d = new Danmaku();
                    d.setId(msg.optString("id"));
                    d.setVideoId(msg.optString("videoId"));
                    d.setUserId(msg.optString("userId"));
                    d.setUsername(msg.optString("username"));
                    d.setContent(msg.optString("content"));
                    d.setColor(msg.optString("color", "#FFFFFF"));
                    d.setTimestampMs(msg.optLong("timestampMs"));
                    if (danmakuView != null) danmakuView.addRealtimeDanmaku(d);
                } catch (Exception ignored) {}
            }
        });
    }

    private void showDanmakuInput() {
        if (video == null) return;
        FragmentManager fm = getSupportFragmentManager();
        if (fm == null) return;
        DanmakuBottomSheetFragment sheet = DanmakuBottomSheetFragment.newInstance(video.getId());
        sheet.show(fm, "danmaku");
    }
}

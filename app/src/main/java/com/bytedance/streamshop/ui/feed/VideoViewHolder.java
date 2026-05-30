package com.bytedance.streamshop.ui.feed;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.data.remote.LiveWebSocketClient;
import com.bytedance.streamshop.domain.model.Danmaku;
import com.bytedance.streamshop.domain.model.Product;
import com.bytedance.streamshop.domain.model.Video;
import com.bytedance.streamshop.ui.profile.AuthorProfileActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class VideoViewHolder extends RecyclerView.ViewHolder {
    private final PlayerView playerView;
    private final ImageView coverView;
    private final ImageView playIndicator;
    private final ShapeableImageView avatarView;
    private final TextView usernameText;
    private final TextView titleText;
    private final TextView likeCountText;
    private final TextView commentCountText;
    private final TextView collectCountText;
    private final ImageButton likeButton;
    private final ImageButton commentButton;
    private final ImageButton collectButton;
    private final ViewGroup productContainer;
    private final SeekBar seekBar;
    private final TextView currentTimeText;
    private final TextView durationText;
    private final View videoRoot;
    private final View avatarContainer;
    private final ImageView followButton;
    private VideoDanmakuView danmakuView;
    private ImageButton danmakuButton;

    private Video video;
    private ApiService apiService;
    private ExoPlayer player;
    private boolean isPlaying;
    private boolean viewReported;
    private boolean fallbackTried;
    private boolean isUserSeeking;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Context context;
    private FragmentManager fragmentManager;
    private LiveWebSocketClient wsClient;

    public VideoViewHolder(@NonNull View itemView) {
        super(itemView);
        playerView = itemView.findViewById(R.id.player_view);
        coverView = itemView.findViewById(R.id.video_cover);
        playIndicator = itemView.findViewById(R.id.video_play_indicator);
        avatarView = itemView.findViewById(R.id.video_avatar);
        usernameText = itemView.findViewById(R.id.video_username);
        titleText = itemView.findViewById(R.id.video_title);
        likeCountText = itemView.findViewById(R.id.video_like_count);
        commentCountText = itemView.findViewById(R.id.video_comment_count);
        collectCountText = itemView.findViewById(R.id.video_collect_count);
        likeButton = itemView.findViewById(R.id.video_like_btn);
        commentButton = itemView.findViewById(R.id.video_comment_btn);
        collectButton = itemView.findViewById(R.id.video_collect_btn);
        productContainer = itemView.findViewById(R.id.product_card_container);
        seekBar = itemView.findViewById(R.id.video_seekbar);
        currentTimeText = itemView.findViewById(R.id.video_current_time);
        durationText = itemView.findViewById(R.id.video_duration);
        videoRoot = itemView.findViewById(R.id.video_root);
        avatarContainer = itemView.findViewById(R.id.video_avatar_container);
        followButton = itemView.findViewById(R.id.video_follow_btn);
        danmakuView = itemView.findViewById(R.id.video_danmaku_view);
        danmakuButton = itemView.findViewById(R.id.video_danmaku_btn);

        if (danmakuButton != null) danmakuButton.setOnClickListener(v -> showDanmakuInput());
        if (avatarContainer != null) avatarContainer.setOnClickListener(v -> openAuthorProfile());
        if (usernameText != null) usernameText.setOnClickListener(v -> openAuthorProfile());
        if (followButton != null) followButton.setOnClickListener(v -> toggleFollow());
        if (likeButton != null) likeButton.setOnClickListener(v -> toggleLike());
        if (commentButton != null) commentButton.setOnClickListener(v -> showComments());
        if (collectButton != null) collectButton.setOnClickListener(v -> toggleCollect());
        if (videoRoot != null) videoRoot.setOnClickListener(v -> togglePlayPause());

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isUserSeeking = true;
                }

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

    public void bind(Video video, Context context, FragmentManager fragmentManager) {
        releasePlayer();
        if (wsClient != null) wsClient.disconnect();
        this.video = video;
        this.context = context;
        this.fragmentManager = fragmentManager;
        this.apiService = new ApiService();
        this.isPlaying = false;
        this.viewReported = false;
        this.fallbackTried = false;
        this.isUserSeeking = false;

        bindVideo();
        setupProductCards();
        initPlayer();
        loadDanmaku();
        connectDanmakuWebSocket();
    }

    public void play() {
        if (player != null && video != null) {
            isPlaying = true;
            player.setPlayWhenReady(true);
            if (danmakuView != null) danmakuView.play();
            startProgressUpdates();
        }
    }

    public void pause() {
        if (player != null) {
            isPlaying = false;
            player.setPlayWhenReady(false);
            if (danmakuView != null) danmakuView.pause();
            stopProgressUpdates();
        }
    }

    public void release() {
        stopProgressUpdates();
        releasePlayer();
        if (danmakuView != null) danmakuView.release();
        if (wsClient != null) wsClient.disconnect();
    }

    // --- Video binding ---

    private void bindVideo() {
        if (video == null) return;

        if (video.getAuthor() != null) {
            if (usernameText != null) {
                usernameText.setText("@" + video.getAuthor().getUsername());
            }
            if (avatarView != null) {
                Glide.with(context)
                        .load(video.getAuthor().getAvatarUrl())
                        .circleCrop()
                        .skipMemoryCache(true)
                        .into(avatarView);
            }
        }

        if (titleText != null) titleText.setText(video.getTitle());
        if (likeCountText != null) likeCountText.setText(formatCount(video.getLikeCount()));
        if (commentCountText != null) commentCountText.setText(formatCount(video.getCommentCount()));

        loadInteractionStates();
    }

    private void loadInteractionStates() {
        if (video == null || apiService == null) return;
        new Thread(() -> {
            try {
                Map<String, Object> likeStatus = apiService.getLikeStatus("video", video.getId());
                Boolean liked = (Boolean) likeStatus.get("liked");
                itemView.post(() -> {
                    if (likeButton != null && liked != null) likeButton.setSelected(liked);
                });
            } catch (Exception ignored) {}
        }).start();
        new Thread(() -> {
            try {
                Map<String, Object> collectStatus = apiService.getCollectionStatus("video", video.getId());
                Boolean collected = (Boolean) collectStatus.get("collected");
                itemView.post(() -> {
                    if (collectButton != null && collected != null) collectButton.setSelected(collected);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    // --- Player ---

    private void initPlayer() {
        if (video == null || video.getVideoUrl() == null) return;
        if (playerView == null) return;

        try {
            player = new ExoPlayer.Builder(context).build();
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
                    reportViewIfNeeded();
                    if (isPlaying && player != null) {
                        player.play();
                    }
                    startProgressUpdates();
                }
            }

            @Override
            public void onPlayerError(@Nullable PlaybackException error) {
                tryFallbackSource();
            }
        });

        player.setPlayWhenReady(false);
        isPlaying = false;
    }

    private void prepareAndPlay(String url) {
        if (player == null || url == null || url.trim().isEmpty()) return;
        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(false);
        isPlaying = false;
    }

    private void tryFallbackSource() {
        if (player == null || fallbackTried) return;
        fallbackTried = true;
        String fallbackUrl = "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4";
        prepareAndPlay(fallbackUrl);
    }

    private void reportViewIfNeeded() {
        if (video == null || viewReported) return;
        viewReported = true;
        new Thread(() -> {
            try {
                int latestViewCount = apiService.incrementVideoView(video.getId());
                video.setViewCount(latestViewCount);
            } catch (Exception ignored) {
            }
        }).start();
    }

    // --- Product cards ---

    private void setupProductCards() {
        if (video == null || video.getProducts() == null || video.getProducts().isEmpty()) {
            if (productContainer != null) productContainer.setVisibility(View.GONE);
            return;
        }
        if (productContainer == null) return;

        productContainer.setVisibility(View.VISIBLE);
        productContainer.removeAllViews();

        for (Product product : video.getProducts()) {
            View card = LayoutInflater.from(context)
                    .inflate(R.layout.item_product_card, productContainer, false);

            ImageView thumb = card.findViewById(R.id.product_thumb);
            TextView titleText = card.findViewById(R.id.product_title);
            TextView priceText = card.findViewById(R.id.product_price);
            TextView originalPriceText = card.findViewById(R.id.product_original_price);
            TextView discountText = card.findViewById(R.id.product_discount);
            TextView stockInfoText = card.findViewById(R.id.product_stock_info);

            if (thumb != null) Glide.with(context).load(product.getCoverUrl()).into(thumb);
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

    // --- Actions ---

    private void openAuthorProfile() {
        if (video == null || video.getAuthor() == null || context == null) return;
        Intent intent = new Intent(context, AuthorProfileActivity.class);
        intent.putExtra(AuthorProfileActivity.EXTRA_USER_ID, video.getAuthor().getId());
        context.startActivity(intent);
    }

    private void toggleFollow() {
        if (followButton == null) return;
        followButton.setSelected(!followButton.isSelected());
        followButton.setAlpha(followButton.isSelected() ? 1f : 0.6f);
    }

    private void toggleLike() {
        if (video == null || likeButton == null) return;
        likeButton.setEnabled(false);
        new Thread(() -> {
            try {
                boolean liked = apiService.toggleLike("video", video.getId());
                itemView.post(() -> {
                    likeButton.setSelected(liked);
                    int count = video.getLikeCount() + (liked ? 1 : -1);
                    video.setLikeCount(Math.max(0, count));
                    if (likeCountText != null) likeCountText.setText(formatCount(video.getLikeCount()));
                    likeButton.setEnabled(true);
                });
            } catch (Exception e) {
                itemView.post(() -> { if (likeButton != null) likeButton.setEnabled(true); });
            }
        }).start();
    }

    private void toggleCollect() {
        if (video == null || collectButton == null) return;
        collectButton.setEnabled(false);
        new Thread(() -> {
            try {
                boolean collected = apiService.toggleCollection("video", video.getId());
                itemView.post(() -> {
                    collectButton.setSelected(collected);
                    collectButton.setEnabled(true);
                });
            } catch (Exception e) {
                itemView.post(() -> { if (collectButton != null) collectButton.setEnabled(true); });
            }
        }).start();
    }

    private void showComments() {
        if (fragmentManager != null && video != null) {
            CommentBottomSheetFragment sheet = CommentBottomSheetFragment.newInstance("video", video.getId());
            sheet.show(fragmentManager, "comment");
        }
    }

    private void showProductDetail(Product product) {
        if (fragmentManager != null) {
            ProductDetailBottomSheetFragment sheet = ProductDetailBottomSheetFragment.newInstance(product);
            sheet.show(fragmentManager, "product_detail");
        }
    }

    // --- Play/pause toggle ---

    private void togglePlayPause() {
        if (player == null) return;
        if (isPlaying) {
            pause();
            showPlayIndicator(false);
        } else {
            play();
            showPlayIndicator(true);
        }
    }

    private void showPlayIndicator(boolean playing) {
        if (playIndicator == null) return;
        playIndicator.setImageResource(playing ? R.drawable.ic_play_indicator : R.drawable.ic_pause_indicator);
        playIndicator.setVisibility(View.VISIBLE);
        playIndicator.setAlpha(1f);
        playIndicator.animate()
                .alpha(0f)
                .setDuration(600)
                .setStartDelay(200)
                .withEndAction(() -> {
                    if (playIndicator != null) playIndicator.setVisibility(View.GONE);
                });
    }

    // --- Progress ---

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

    // --- Helpers ---

    private String formatTime(long ms) {
        if (ms < 0) return "00:00";
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatCount(int count) {
        if (count >= 10000) {
            return count / 10000 + "w";
        } else if (count >= 1000) {
            return count / 1000 + "k";
        }
        return String.valueOf(count);
    }

    private void releasePlayer() {
        if (player != null) {
            stopProgressUpdates();
            isPlaying = false;
            player.stop();
            player.release();
            player = null;
        }
    }

    // --- Danmaku ---

    private void loadDanmaku() {
        if (video == null || danmakuView == null) return;
        new Thread(() -> {
            try {
                List<Danmaku> list = apiService.getDanmaku(video.getId());
                itemView.post(() -> danmakuView.loadDanmaku(list));
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
        if (video == null || player == null || context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("发送弹幕");

        final EditText input = new EditText(context);
        input.setHint("输入弹幕内容...");
        input.setMaxLines(1);
        input.setSingleLine(true);
        input.setPadding(48, 32, 48, 32);
        input.setTextSize(16);
        builder.setView(input);

        builder.setPositiveButton("发送", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) sendDanmaku(text);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void sendDanmaku(String content) {
        if (video == null || player == null || !ApiClient.getInstance().isAuthenticated()) return;
        long currentMs = player.getCurrentPosition();

        String[] colors = {"#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#007AFF", "#AF52DE", "#FF2D55"};
        String color = colors[new Random().nextInt(colors.length)];

        // Send to backend (persists + pushes via WebSocket to all viewers including self)
        new Thread(() -> {
            try {
                apiService.postDanmaku(video.getId(), content, color, currentMs);
            } catch (Exception ignored) {}
        }).start();
    }

    public Video getVideo() {
        return video;
    }
}

package com.bytedance.streamshop.ui.feed;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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

public class VideoPageFragment extends Fragment {
    private static final String ARG_VIDEO = "video";

    private Video video;
    private ApiService apiService;
    private ExoPlayer player;
    private PlayerView playerView;
    private boolean isPlaying = false;
    private boolean viewReported = false;
    private boolean fallbackTried = false;

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
    private boolean isUserSeeking = false;

    public static VideoPageFragment newInstance(Video video) {
        VideoPageFragment fragment = new VideoPageFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_VIDEO, video);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            video = (Video) getArguments().getSerializable(ARG_VIDEO);
        }
        apiService = new ApiService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        bindVideo();
        setupProductCards(view);
        initPlayer(view);
    }

    private void initViews(View view) {
        playerView = view.findViewById(R.id.player_view);
        coverView = view.findViewById(R.id.video_cover);
        avatarView = view.findViewById(R.id.video_avatar);
        usernameText = view.findViewById(R.id.video_username);
        titleText = view.findViewById(R.id.video_title);
        likeCountText = view.findViewById(R.id.video_like_count);
        commentCountText = view.findViewById(R.id.video_comment_count);
        collectCountText = view.findViewById(R.id.video_collect_count);
        forwardCountText = view.findViewById(R.id.video_forward_count);
        likeButton = view.findViewById(R.id.video_like_btn);
        commentButton = view.findViewById(R.id.video_comment_btn);
        collectButton = view.findViewById(R.id.video_collect_btn);
        forwardButton = view.findViewById(R.id.video_forward_btn);
        followButton = view.findViewById(R.id.video_follow_btn);
        productContainer = view.findViewById(R.id.product_card_container);
        seekBar = view.findViewById(R.id.video_seekbar);
        currentTimeText = view.findViewById(R.id.video_current_time);
        durationText = view.findViewById(R.id.video_duration);
        playIndicator = view.findViewById(R.id.video_play_indicator);
        videoRoot = view.findViewById(R.id.video_root);
        danmakuView = view.findViewById(R.id.video_danmaku_view);
        danmakuButton = view.findViewById(R.id.video_danmaku_btn);

        if (danmakuButton != null) danmakuButton.setOnClickListener(v -> showDanmakuInput());
        if (likeButton != null) likeButton.setOnClickListener(v -> toggleLike());
        if (commentButton != null) commentButton.setOnClickListener(v -> showComments());
        if (collectButton != null) collectButton.setOnClickListener(v -> toggleCollect());
        if (forwardButton != null) forwardButton.setOnClickListener(v -> showForwardSheet());
        if (followButton != null) followButton.setOnClickListener(v -> toggleFollow());

        if (videoRoot != null) {
            videoRoot.setOnClickListener(v -> togglePlayPause());
        }

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

    private void bindVideo() {
        if (video == null) return;

        // Load cover image as fallback (visible until video plays)
        if (video.getCoverUrl() != null && coverView != null) {
            Glide.with(this)
                    .load(video.getCoverUrl())
                    .centerCrop()
                    .into(coverView);
        }

        if (video.getAuthor() != null) {
            if (usernameText != null) {
                usernameText.setText(video.getAuthor().getUsername());
            }
            if (avatarView != null) {
                Glide.with(this)
                        .load(video.getAuthor().getAvatarUrl())
                        .circleCrop()
                        .skipMemoryCache(true)
                        .into(avatarView);
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
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (likeButton != null && liked != null) likeButton.setSelected(liked);
                        });
                    }
                } catch (Exception ignored) {}
            }).start();
        }
        if (collectButton != null) {
            new Thread(() -> {
                try {
                    Map<String, Object> status = apiService.getCollectionStatus("video", video.getId());
                    Boolean collected = (Boolean) status.get("collected");
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (collectButton != null && collected != null) collectButton.setSelected(collected);
                        });
                    }
                } catch (Exception ignored) {}
            }).start();
        }
        if (video.getAuthor() != null && followButton != null) {
            new Thread(() -> {
                try {
                    boolean following = apiService.isFollowing(video.getAuthor().getId());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> followButton.setSelected(following));
                    }
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private void initPlayer(View view) {
        if (video == null || video.getVideoUrl() == null) return;
        if (playerView == null) return;

        // Release any existing player before creating a new one
        releasePlayer();

        try {
            player = new ExoPlayer.Builder(requireContext()).build();
        } catch (Exception e) {
            return;
        }

        playerView.setPlayer(player);
        playerView.setUseController(false);

        prepareAndPlay(video.getVideoUrl());

        final ImageView cover = coverView;
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    if (cover != null) cover.setVisibility(View.GONE);
                    reportViewIfNeeded();
                    if (isPlaying && player != null) {
                        player.play();
                    }
                    startProgressUpdates();
                }
            }

            @Override
            public void onPlayerError(@Nullable PlaybackException error) {
                if (cover != null) cover.setVisibility(View.VISIBLE);
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
        // Fallback source to avoid regional CDN issues on primary URL.
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
                // Keep playback smooth even if metrics reporting fails.
            }
        }).start();
    }

    public void play() {
        if (player != null) {
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

    @Override
    public void onResume() {
        super.onResume();
        play();
    }

    @Override
    public void onPause() {
        super.onPause();
        pause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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

    private void setupProductCards(View view) {
        if (video == null || video.getProducts() == null || video.getProducts().isEmpty()) {
            if (productContainer != null) productContainer.setVisibility(View.GONE);
            return;
        }
        if (productContainer == null) return;

        productContainer.setVisibility(View.VISIBLE);
        productContainer.removeAllViews();

        for (Product product : video.getProducts()) {
            View card = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_product_card, productContainer, false);

            ImageView thumb = card.findViewById(R.id.product_thumb);
            TextView priceText = card.findViewById(R.id.product_price);

            priceText.setText("¥" + (int) product.getPrice());
            Glide.with(this).load(product.getCoverUrl()).into(thumb);

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
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        likeButton.setSelected(liked);
                        int count = video.getLikeCount() + (liked ? 1 : -1);
                        video.setLikeCount(Math.max(0, count));
                        likeCountText.setText(formatCount(video.getLikeCount()));
                        likeButton.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        likeButton.setEnabled(true);
                    });
                }
            }
        }).start();
    }

    private void toggleCollect() {
        if (video == null || collectButton == null) return;
        collectButton.setEnabled(false);
        new Thread(() -> {
            try {
                boolean collected = apiService.toggleCollection("video", video.getId());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        collectButton.setSelected(collected);
                        collectButton.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        collectButton.setEnabled(true);
                    });
                }
            }
        }).start();
    }

    private void toggleFollow() {
        if (video == null || video.getAuthor() == null || followButton == null) return;
        followButton.setEnabled(false);
        new Thread(() -> {
            try {
                boolean following = apiService.toggleFollow(video.getAuthor().getId());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        followButton.setSelected(following);
                        followButton.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (followButton != null) followButton.setEnabled(true);
                    });
                }
            }
        }).start();
    }

    private void showComments() {
        if (getParentFragmentManager() != null && video != null) {
            CommentBottomSheetFragment sheet = CommentBottomSheetFragment.newInstance("video", video.getId());
            sheet.show(getParentFragmentManager(), "comment");
        }
    }

    private void showProductDetail(Product product) {
        if (getParentFragmentManager() != null) {
            ProductDetailBottomSheetFragment sheet = ProductDetailBottomSheetFragment.newInstance(product);
            sheet.show(getParentFragmentManager(), "product_detail");
        }
    }

    private void showForwardSheet() {
        if (getParentFragmentManager() != null && video != null) {
            ForwardUserBottomSheetFragment sheet = ForwardUserBottomSheetFragment.newInstance(video.getId());
            sheet.setOnForwardListener(newShareCount -> {
                if (video != null) {
                    video.setShareCount(newShareCount);
                    if (forwardCountText != null) forwardCountText.setText(formatCount(newShareCount));
                }
            });
            sheet.show(getParentFragmentManager(), "forward");
        }
    }

    private String formatCount(int count) {
        if (count >= 10000) {
            return count / 10000 + "w";
        } else if (count >= 1000) {
            return count / 1000 + "k";
        }
        return String.valueOf(count);
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
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> danmakuView.loadDanmaku(list));
                }
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
        if (video == null || player == null) return;
        Context ctx = getContext();
        if (ctx == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("发送弹幕");

        final EditText input = new EditText(ctx);
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

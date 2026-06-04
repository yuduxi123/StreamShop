package com.bytedance.streamshop.ui.feed;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SnapHelper;
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

public class VideoViewHolder extends RecyclerView.ViewHolder {

    public interface OnVideoEndedListener {
        void onVideoEnded();
    }

    private final PlayerView playerView;
    private final ImageView coverView;
    private final ImageView playIndicator;
    private final ShapeableImageView avatarView;
    private final TextView usernameText;
    private final TextView titleText;
    private final TextView likeCountText;
    private final TextView commentCountText;
    private final TextView collectCountText;
    private final TextView forwardCountText;
    private final ImageButton likeButton;
    private final ImageButton commentButton;
    private final ImageButton collectButton;
    private final ImageButton forwardButton;
    private final ImageButton playModeButton;
    private final RecyclerView productContainer;
    private final DotSeekBar seekBar;
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
    private long pendingSeekMs = 0;
    private boolean hasExternalPlayer = false;
    private boolean playerIsExternal = false;
    private boolean viewReported;
    private boolean fallbackTried;
    private boolean isUserSeeking;
    private boolean loopMode;
    private boolean danmakuEnabled = true;
    private OnVideoEndedListener playbackModeListener;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Context context;
    private FragmentManager fragmentManager;
    private LiveWebSocketClient wsClient;
    private long videoDurationMs = 0;

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
        forwardCountText = itemView.findViewById(R.id.video_forward_count);
        likeButton = itemView.findViewById(R.id.video_like_btn);
        commentButton = itemView.findViewById(R.id.video_comment_btn);
        collectButton = itemView.findViewById(R.id.video_collect_btn);
        forwardButton = itemView.findViewById(R.id.video_forward_btn);
        playModeButton = itemView.findViewById(R.id.video_playmode_btn);
        productContainer = itemView.findViewById(R.id.product_card_container);
        seekBar = (DotSeekBar) itemView.findViewById(R.id.video_seekbar);
        currentTimeText = itemView.findViewById(R.id.video_current_time);
        durationText = itemView.findViewById(R.id.video_duration);
        videoRoot = itemView.findViewById(R.id.video_root);
        avatarContainer = itemView.findViewById(R.id.video_avatar_container);
        followButton = itemView.findViewById(R.id.video_follow_btn);
        danmakuView = itemView.findViewById(R.id.video_danmaku_view);
        danmakuButton = itemView.findViewById(R.id.video_danmaku_btn);

        // Product card RecyclerView one-time setup
        if (productContainer != null) {
            productContainer.setLayoutManager(
                    new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            new StartSnapHelper().attachToRecyclerView(productContainer);

            productContainer.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            });
        }

        if (danmakuButton != null) danmakuButton.setOnClickListener(v -> showDanmakuInput());
        if (avatarContainer != null) avatarContainer.setOnClickListener(v -> openAuthorProfile());
        if (usernameText != null) usernameText.setOnClickListener(v -> openAuthorProfile());
        if (followButton != null) followButton.setOnClickListener(v -> toggleFollow());
        if (likeButton != null) likeButton.setOnClickListener(v -> toggleLike());
        if (commentButton != null) commentButton.setOnClickListener(v -> showComments());
        if (collectButton != null) collectButton.setOnClickListener(v -> toggleCollect());
        if (forwardButton != null) forwardButton.setOnClickListener(v -> showForwardSheet());
        if (playModeButton != null) playModeButton.setOnClickListener(v -> togglePlayMode());
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
                            Product nearProduct = findNearProduct(position, duration);
                            if (nearProduct != null) {
                                player.seekTo(getEffectiveTimestamp(nearProduct));
                                if (context != null) {
                            showPlainToast(nearProduct.getTitle());
                        }
                            } else {
                                player.seekTo(position);
                            }
                        }
                    }
                }
            });
        }
    }

    public void setOnPlaybackModeListener(OnVideoEndedListener listener) {
        this.playbackModeListener = listener;
    }

    public void bind(Video video, Context context, FragmentManager fragmentManager) {
        if (!hasExternalPlayer) {
            releasePlayer();
        }
        hasExternalPlayer = false;
        if (wsClient != null) wsClient.disconnect();
        this.video = video;
        this.context = context;
        this.fragmentManager = fragmentManager;
        this.apiService = new ApiService();
        this.isPlaying = false;
        this.viewReported = false;
        this.fallbackTried = false;
        this.isUserSeeking = false;
        this.loopMode = false;
        updatePlayModeButton();

        bindVideo();
        setupProductCards();
        if (playIndicator != null) playIndicator.setVisibility(View.GONE);
        if (player == null) {
            initPlayer();
        } else {
            if (coverView != null) coverView.setVisibility(View.GONE);
        }
        loadDanmaku();
        connectDanmakuWebSocket();
    }

    public void play() {
        if (player != null && video != null) {
            isPlaying = true;
            player.setPlayWhenReady(true);
            if (danmakuView != null) danmakuView.play();
            if (playIndicator != null) playIndicator.setVisibility(View.GONE);
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

        if (video.getCoverUrl() != null && !video.getCoverUrl().isEmpty() && coverView != null) {
            coverView.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(video.getCoverUrl())
                    .centerCrop()
                    .into(coverView);
        }

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
        if (forwardCountText != null) forwardCountText.setText(formatCount(video.getShareCount()));

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
        // Load follow status
        if (video.getAuthor() != null && followButton != null) {
            new Thread(() -> {
                try {
                    boolean following = apiService.isFollowing(video.getAuthor().getId());
                    itemView.post(() -> followButton.setSelected(following));
                } catch (Exception ignored) {}
            }).start();
        }
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
                    if (pendingSeekMs > 0) {
                        player.seekTo(pendingSeekMs);
                        pendingSeekMs = 0;
                    }
                    if (coverView != null) coverView.setVisibility(View.GONE);
                    reportViewIfNeeded();
                    if (isPlaying && player != null) {
                        player.play();
                    }
                    startProgressUpdates();
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (loopMode) {
                        if (player != null) {
                            player.seekTo(0);
                            player.play();
                            if (danmakuView != null) {
                                danmakuView.setPlaybackPosition(0);
                                danmakuView.play();
                            }
                        }
                    } else {
                        if (playbackModeListener != null) {
                            playbackModeListener.onVideoEnded();
                        }
                    }
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
        productContainer.setAdapter(new ProductCardAdapter(video.getProducts()));
        updateProgressDots();
    }

    // --- Product card adapter ---

    private class ProductCardAdapter extends RecyclerView.Adapter<ProductCardAdapter.VH> {
        private final List<Product> products;

        ProductCardAdapter(List<Product> products) {
            this.products = products;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View card = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product_card, parent, false);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Product product = products.get(position);

            if (holder.thumb != null) Glide.with(context).load(product.getCoverUrl()).into(holder.thumb);
            if (holder.titleText != null) holder.titleText.setText(product.getTitle());

            int price = (int) product.getPrice();
            if (holder.priceText != null) holder.priceText.setText("¥" + price);

            double originalPrice = product.getOriginalPrice();
            if (originalPrice > 0 && originalPrice > product.getPrice()
                    && holder.originalPriceText != null && holder.discountText != null) {
                holder.originalPriceText.setVisibility(View.VISIBLE);
                holder.originalPriceText.setText("¥" + (int) originalPrice);
                holder.originalPriceText.setPaintFlags(
                        holder.originalPriceText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                int discount = (int) (product.getPrice() * 100 / originalPrice);
                holder.discountText.setVisibility(View.VISIBLE);
                holder.discountText.setText(discount + "折");
            }

            if (holder.stockInfoText != null) {
                if (product.getStock() <= 0) {
                    holder.stockInfoText.setText("已售罄");
                    holder.stockInfoText.setTextColor(0xFFFF3B30);
                } else {
                    holder.stockInfoText.setText("已售" + formatCount(product.getSalesCount()));
                    holder.stockInfoText.setTextColor(0xFF999999);
                }
            }

            if (holder.introButton != null) {
                holder.introButton.setOnClickListener(v -> {
                    if (player != null) {
                        long ts = getEffectiveTimestamp(product);
                        player.seekTo(ts);
                        if (!isPlaying) {
                            player.setPlayWhenReady(true);
                            isPlaying = true;
                            if (danmakuView != null) danmakuView.play();
                            startProgressUpdates();
                            if (playIndicator != null) playIndicator.setVisibility(View.GONE);
                        }
                        if (context != null) {
                        showPlainToast(product.getTitle());
                    }
                    }
                });
            }

            holder.itemView.setOnClickListener(v -> showProductDetail(product));
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView titleText, priceText, originalPriceText, discountText, stockInfoText, introButton;

            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.product_thumb);
                titleText = v.findViewById(R.id.product_title);
                priceText = v.findViewById(R.id.product_price);
                originalPriceText = v.findViewById(R.id.product_original_price);
                discountText = v.findViewById(R.id.product_discount);
                stockInfoText = v.findViewById(R.id.product_stock_info);
                introButton = v.findViewById(R.id.product_intro_btn);
            }
        }
    }

    // --- StartSnapHelper: snaps items to the left edge so 2 cards are always complete ---

    private static class StartSnapHelper extends SnapHelper {
        @Nullable
        @Override
        public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager layoutManager,
                                                  @NonNull View targetView) {
            int[] out = new int[2];
            if (layoutManager.canScrollHorizontally()) {
                out[0] = layoutManager.getDecoratedLeft(targetView) - layoutManager.getPaddingLeft();
                out[1] = 0;
            }
            return out;
        }

        @Nullable
        @Override
        public View findSnapView(RecyclerView.LayoutManager layoutManager) {
            if (!(layoutManager instanceof LinearLayoutManager)) return null;
            LinearLayoutManager lm = (LinearLayoutManager) layoutManager;
            int itemCount = layoutManager.getItemCount();
            if (itemCount == 0) return null;

            int firstVisible = lm.findFirstVisibleItemPosition();
            if (firstVisible == RecyclerView.NO_POSITION) return null;

            View firstView = lm.findViewByPosition(firstVisible);
            if (firstView == null) return null;

            int left = lm.getDecoratedLeft(firstView);
            int width = lm.getDecoratedMeasuredWidth(firstView);

            if (left < -width / 2 && firstVisible + 1 < itemCount) {
                View nextView = lm.findViewByPosition(firstVisible + 1);
                if (nextView != null) return nextView;
            }
            return firstView;
        }

        @Override
        public int findTargetSnapPosition(RecyclerView.LayoutManager layoutManager,
                                          int velocityX, int velocityY) {
            if (!(layoutManager instanceof LinearLayoutManager)) return RecyclerView.NO_POSITION;
            LinearLayoutManager lm = (LinearLayoutManager) layoutManager;
            int itemCount = layoutManager.getItemCount();
            if (itemCount == 0) return RecyclerView.NO_POSITION;

            int firstVisible = lm.findFirstVisibleItemPosition();
            if (firstVisible == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION;

            View firstView = lm.findViewByPosition(firstVisible);
            if (firstView == null) return RecyclerView.NO_POSITION;

            int left = lm.getDecoratedLeft(firstView);
            int width = lm.getDecoratedMeasuredWidth(firstView);

            int currentPage = (left < -width / 2) ? firstVisible + 1 : firstVisible;

            if (Math.abs(velocityX) < 600) return currentPage;

            int target = velocityX > 0 ? currentPage + 1 : currentPage - 1;
            return Math.max(0, Math.min(target, itemCount - 1));
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
        if (video == null || video.getAuthor() == null || followButton == null) return;
        followButton.setEnabled(false);
        new Thread(() -> {
            try {
                boolean following = apiService.toggleFollow(video.getAuthor().getId());
                itemView.post(() -> {
                    followButton.setSelected(following);
                    followButton.setEnabled(true);
                });
            } catch (Exception e) {
                itemView.post(() -> { if (followButton != null) followButton.setEnabled(true); });
            }
        }).start();
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

    private void togglePlayMode() {
        loopMode = !loopMode;
        updatePlayModeButton();
    }

    private void updatePlayModeButton() {
        if (playModeButton == null) return;
        if (loopMode) {
            playModeButton.setBackgroundResource(R.drawable.ic_playmode_loop);
        } else {
            playModeButton.setBackgroundResource(R.drawable.ic_playmode_sequential);
        }
    }

    private void showComments() {
        if (fragmentManager != null && video != null) {
            CommentBottomSheetFragment sheet = CommentBottomSheetFragment.newInstance("video", video.getId());
            sheet.show(fragmentManager, "comment");
        }
    }

    private void showForwardSheet() {
        if (fragmentManager != null && video != null) {
            ForwardUserBottomSheetFragment sheet = ForwardUserBottomSheetFragment.newInstance(video.getId());
            sheet.setOnForwardListener(newShareCount -> {
                if (video != null) {
                    video.setShareCount(newShareCount);
                    if (forwardCountText != null) forwardCountText.setText(formatCount(newShareCount));
                }
            });
            sheet.show(fragmentManager, "forward");
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
                    if (videoDurationMs != duration) {
                        videoDurationMs = duration;
                        updateProgressDots();
                    }
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

    private void showPlainToast(String text) {
        if (context == null) return;
        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_SHORT);
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setBackgroundColor(0xCC333333);
        int pad = (int) (12 * context.getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad - 4, pad, pad - 4);
        toast.setView(tv);
        toast.show();
    }

    private String formatCount(int count) {
        if (count >= 10000) {
            return count / 10000 + "w";
        } else if (count >= 1000) {
            return count / 1000 + "k";
        }
        return String.valueOf(count);
    }

    private Product findNearProduct(long positionMs, long durationMs) {
        if (video == null || video.getProducts() == null || video.getProducts().isEmpty()) return null;
        List<Product> products = video.getProducts();
        long threshold = Math.max(durationMs * 3 / 100, 500);
        Product nearest = null;
        long nearestDist = Long.MAX_VALUE;
        for (int i = 0; i < products.size(); i++) {
            long ts = getEffectiveTimestamp(products.get(i), i, products.size());
            long dist = Math.abs(ts - positionMs);
            if (dist < threshold && dist < nearestDist) {
                nearestDist = dist;
                nearest = products.get(i);
            }
        }
        return nearest;
    }

    private long getEffectiveTimestamp(Product product) {
        if (video == null || video.getProducts() == null) return 0;
        List<Product> products = video.getProducts();
        int index = products.indexOf(product);
        return getEffectiveTimestamp(product, index, products.size());
    }

    private long getEffectiveTimestamp(Product product, int index, int totalCount) {
        if (product.getTimestampMs() > 0) return product.getTimestampMs();
        if (videoDurationMs <= 0) return 0;
        return videoDurationMs * (index + 1) / (totalCount + 1);
    }

    private void updateProgressDots() {
        if (seekBar == null || video == null || video.getProducts() == null
                || video.getProducts().isEmpty() || videoDurationMs <= 0) {
            if (seekBar != null) seekBar.setDotRatios(null);
            return;
        }
        List<Product> products = video.getProducts();
        List<Float> ratios = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            long ts = getEffectiveTimestamp(products.get(i), i, products.size());
            float ratio = (float) ts / videoDurationMs;
            ratio = Math.max(0f, Math.min(1f, ratio));
            ratios.add(ratio);
        }
        seekBar.setDotRatios(ratios);
    }

    public ExoPlayer takePlayer() {
        ExoPlayer p = player;
        if (p != null) {
            stopProgressUpdates();
            p.setPlayWhenReady(false);
            if (playerView != null) playerView.setPlayer(null);
            player = null;
            isPlaying = false;
        }
        return p;
    }

    public void useExistingPlayer(ExoPlayer existingPlayer, long positionMs) {
        if (existingPlayer == null || playerView == null) return;
        this.player = existingPlayer;
        playerView.setPlayer(existingPlayer);
        playerView.setUseController(false);
        hasExternalPlayer = true;
        playerIsExternal = true;
        if (positionMs > 0) {
            long currentPos = existingPlayer.getCurrentPosition();
            if (Math.abs(currentPos - positionMs) > 500) {
                existingPlayer.seekTo(positionMs);
            }
        }
        if (coverView != null) coverView.setVisibility(View.GONE);
    }

    private void releasePlayer() {
        if (player != null) {
            stopProgressUpdates();
            isPlaying = false;
            if (!playerIsExternal) {
                player.stop();
                player.release();
            } else {
                if (playerView != null) playerView.setPlayer(null);
                playerIsExternal = false;
            }
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
        if (video == null || fragmentManager == null) return;
        DanmakuBottomSheetFragment sheet = DanmakuBottomSheetFragment.newInstance(video.getId());
        sheet.setDanmakuEnabled(danmakuEnabled);
        sheet.setOnDanmakuToggleListener(enabled -> {
            danmakuEnabled = enabled;
            if (danmakuView != null) {
                danmakuView.setVisibility(enabled ? View.VISIBLE : View.GONE);
            }
        });
        sheet.show(fragmentManager, "danmaku");
    }

    public long getCurrentPositionMs() {
        if (player != null) {
            long pos = player.getCurrentPosition();
            return pos > 0 ? pos : 0;
        }
        return 0;
    }

    public boolean isCurrentlyPlaying() {
        return isPlaying;
    }

    public void setRestoreInfo(long positionMs) {
        this.pendingSeekMs = Math.max(0, positionMs);
    }

    public Video getVideo() {
        return video;
    }
}

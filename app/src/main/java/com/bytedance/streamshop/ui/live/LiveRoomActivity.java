package com.bytedance.streamshop.ui.live;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.data.remote.LiveWebSocketClient;
import com.bytedance.streamshop.domain.model.Product;
import com.bytedance.streamshop.ui.feed.ProductDetailBottomSheetFragment;
import com.bytedance.streamshop.ui.profile.AuthorProfileActivity;
import com.bumptech.glide.Glide;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LiveRoomActivity extends AppCompatActivity {
    private static final String TAG = "LiveRoomActivity";
    private static final int STREAM_RETRY_DELAY_MS = 2000;
    private static final int MAX_STREAM_RETRY_COUNT = 10;

    private String roomId;
    private String userId = "1d552fd0-fd85-4952-a0cc-edb4fd674ed5"; // default user
    private String username = "test_user";

    private ExoPlayer player;
    private DanmakuView danmakuView;
    private ImageButton danmakuToggle;
    private ImageButton exitBtn;
    private boolean danmakuEnabled = true;
    private ShapeableImageView anchorAvatar;
    private TextView anchorName;
    private String anchorId;
    private RecyclerView activityFeedList;
    private final List<String> activityFeedItems = new ArrayList<>();
    private LiveWebSocketClient wsClient;
    private ApiService apiService;
    private TextView viewerCountText, hotValueText;
    private TextView streamPlaceholder;
    private EditText inputView;
    private RecyclerView productList;
    private ProductAdapter productAdapter;
    private FrameLayout couponContainer;

    private final List<Map<String, Object>> products = new ArrayList<>();
    private NumberFormat priceFmt = NumberFormat.getNumberInstance(Locale.CHINA);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int streamRetryCount = 0;
    private boolean streamLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);

        // Make status bar transparent with white icons for dark background
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        roomId = getIntent().getStringExtra("room_id");
        if (roomId == null) { finish(); return; }

        apiService = new ApiService();
        wsClient = new LiveWebSocketClient();
        priceFmt.setMinimumFractionDigits(0);

        initViews();
        setupPlayer();
        loadRoomDetail();
        connectWebSocket();
    }

    private void initViews() {
        anchorAvatar = findViewById(R.id.live_anchor_avatar);
        anchorName = findViewById(R.id.live_anchor_name);
        viewerCountText = findViewById(R.id.live_viewer_count);
        danmakuToggle = findViewById(R.id.live_danmaku_toggle);
        exitBtn = findViewById(R.id.live_exit_btn);
        hotValueText = findViewById(R.id.live_hot_value);
        streamPlaceholder = findViewById(R.id.live_stream_placeholder);
        inputView = findViewById(R.id.live_input);
        danmakuView = findViewById(R.id.live_danmaku_view);
        couponContainer = findViewById(R.id.live_coupon_container);

        findViewById(R.id.live_anchor_info).setOnClickListener(v -> {
            if (anchorId != null) {
                Intent intent = new Intent(LiveRoomActivity.this, AuthorProfileActivity.class);
                intent.putExtra(AuthorProfileActivity.EXTRA_USER_ID, anchorId);
                startActivity(intent);
            }
        });

        viewerCountText.setOnClickListener(v -> {
            wsClient.sendGetRoomUsers();
        });

        exitBtn.setOnClickListener(v -> finish());

        danmakuToggle.setOnClickListener(v -> {
            danmakuEnabled = !danmakuEnabled;
            danmakuToggle.setImageResource(danmakuEnabled
                    ? R.drawable.ic_danmaku_on : R.drawable.ic_danmaku_off);
            danmakuView.setVisibility(danmakuEnabled ? View.VISIBLE : View.GONE);
        });

        findViewById(R.id.live_send_btn).setOnClickListener(v -> sendMessage());

        inputView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });

        productList = findViewById(R.id.live_product_list);
        productList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        productAdapter = new ProductAdapter();
        productList.setAdapter(productAdapter);

        activityFeedList = findViewById(R.id.live_activity_feed);
        activityFeedList.setLayoutManager(new LinearLayoutManager(this));
        activityFeedList.setAdapter(new RecyclerView.Adapter<FeedVH>() {
            @NonNull @Override
            public FeedVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new FeedVH(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_live_activity_feed, parent, false));
            }
            @Override
            public void onBindViewHolder(@NonNull FeedVH holder, int position) {
                ((TextView) holder.itemView).setText(activityFeedItems.get(position));
            }
            @Override
            public int getItemCount() { return activityFeedItems.size(); }
        });
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        androidx.media3.ui.PlayerView playerView = findViewById(R.id.live_player_view);
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "Live stream playback failed", error);
                runOnUiThread(() -> {
                    showStreamPlaceholder("直播画面加载失败");
                    Toast.makeText(LiveRoomActivity.this, "视频加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    runOnUiThread(() -> hideStreamPlaceholder());
                }
            }
        });
    }

    private void playStream(String streamUrl) {
        if (streamUrl == null || streamUrl.isEmpty()) {
            showStreamPlaceholder("直播画面未接入");
            return;
        }
        streamLoaded = true;
        showStreamPlaceholder("正在加载直播画面...");
        Log.d(TAG, "Playing live stream: " + streamUrl);
        MediaItem mediaItem = MediaItem.fromUri(streamUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
    }

    private void loadRoomDetail() {
        new Thread(() -> {
            try {
                Map<String, Object> room = apiService.getRoomDetail(roomId);
                if (room == null) return;
                List<Map<String, Object>> roomProducts = (List<Map<String, Object>>) room.get("products");
                if (roomProducts != null) {
                    products.clear();
                    products.addAll(roomProducts);
                }
                String streamUrl = (String) room.get("streamUrl");
                Map<String, Object> anchor = (Map<String, Object>) room.get("anchor");
                String avatarUrl = null;
                if (anchor != null) {
                    anchorId = (String) anchor.get("id");
                    avatarUrl = (String) anchor.get("avatarUrl");
                    String name = (String) anchor.get("username");
                    if (name != null) {
                        runOnUiThread(() -> anchorName.setText(name));
                    }
                }
                final String finalAvatarUrl = avatarUrl;
                runOnUiThread(() -> {
                    if (finalAvatarUrl != null && !finalAvatarUrl.isEmpty()) {
                        Glide.with(LiveRoomActivity.this)
                                .load(finalAvatarUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_avatar_placeholder)
                                .into(anchorAvatar);
                    }
                    playStream(streamUrl);
                    productAdapter.notifyDataSetChanged();
                    if (TextUtils.isEmpty(streamUrl)) {
                        scheduleStreamRetry();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Load live room detail failed", e);
            }
        }).start();
    }

    private void scheduleStreamRetry() {
        if (streamLoaded || streamRetryCount >= MAX_STREAM_RETRY_COUNT) return;
        streamRetryCount++;
        showStreamPlaceholder("等待直播画面接入...");
        mainHandler.postDelayed(() -> {
            if (!streamLoaded) {
                loadRoomDetail();
            }
        }, STREAM_RETRY_DELAY_MS);
    }

    private void showStreamPlaceholder(String message) {
        if (streamPlaceholder == null) return;
        streamPlaceholder.setText(message);
        streamPlaceholder.setVisibility(View.VISIBLE);
    }

    private void hideStreamPlaceholder() {
        if (streamPlaceholder == null) return;
        streamPlaceholder.setVisibility(View.GONE);
    }

    private void connectWebSocket() {
        ApiClient client = ApiClient.getInstance();
        if (client.isAuthenticated() && client.getCurrentUsername() != null) {
            username = client.getCurrentUsername();
        }
        wsClient.connect(roomId, userId, username, new LiveWebSocketClient.WsCallback() {
            @Override
            public void onConnected() {}

            @Override
            public void onRoomJoined(int onlineCount, int hotValue) {
                runOnUiThread(() -> {
                    viewerCountText.setText(onlineCount + "人在线");
                    hotValueText.setText("热度: " + hotValue);
                });
            }

            @Override
            public void onOnlineCount(int count) {
                runOnUiThread(() -> viewerCountText.setText(count + "人在线"));
            }

            @Override
            public void onNewComment(JSONObject comment) {
                String user = comment.optString("username", "匿名");
                String content = comment.optString("content", "");
                runOnUiThread(() -> {
                    danmakuView.addDanmaku(user + ": " + content, "#FFFFFF");
                    addActivityFeedItem(user + ": " + content);
                });
            }

            @Override
            public void onNewDanmaku(JSONObject danmaku) {
                String content = danmaku.optString("content", "");
                String color = danmaku.optString("color", "#FFFFFF");
                runOnUiThread(() -> danmakuView.addDanmaku(content, color));
            }

            @Override
            public void onLikeUpdate(JSONObject msg) {}

            @Override
            public void onHotValueUpdate(int value) {
                runOnUiThread(() -> hotValueText.setText("热度: " + value));
            }

            @Override
            public void onProductChanged(String productId, String action) {
                runOnUiThread(() -> {
                    if ("explaining".equals(action)) {
                        Toast.makeText(LiveRoomActivity.this, "主播正在讲解商品 #" + productId.substring(0, 6), Toast.LENGTH_SHORT).show();
                    } else if ("added".equals(action) || "removed".equals(action) || "reordered".equals(action)) {
                        loadRoomDetail();
                    }
                });
            }

            @Override
            public void onCouponPushed(JSONObject coupon) {
                runOnUiThread(() -> showCouponNotification(coupon));
            }

            @Override
            public void onUserJoined(String username, String userId) {
                runOnUiThread(() -> addActivityFeedItem(username + " 进入了直播间"));
            }

            @Override
            public void onPurchase(String username, String productTitle, int quantity) {
                runOnUiThread(() -> addActivityFeedItem(username + "购买了" + productTitle + "×" + quantity));
            }

            @Override
            public void onRoomUsers(JSONArray users) {
                runOnUiThread(() -> {
                    LiveUserListBottomSheet sheet = LiveUserListBottomSheet.newInstance(users);
                    sheet.show(getSupportFragmentManager(), "user_list_sheet");
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> Toast.makeText(LiveRoomActivity.this, "直播连接已断开", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(LiveRoomActivity.this, "连接出错: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendMessage() {
        String text = inputView.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        inputView.setText("");
        wsClient.sendComment(text);
        danmakuView.addDanmaku(username + ": " + text, "#FFFFFF");
        addActivityFeedItem(username + ": " + text);
    }

    private void addActivityFeedItem(String msg) {
        runOnUiThread(() -> {
            activityFeedItems.add(msg);
            if (activityFeedItems.size() > 50) {
                activityFeedItems.remove(0);
            }
            if (activityFeedList.getAdapter() != null) {
                activityFeedList.getAdapter().notifyItemInserted(activityFeedItems.size() - 1);
                activityFeedList.scrollToPosition(activityFeedItems.size() - 1);
            }
        });
    }

    private static class FeedVH extends RecyclerView.ViewHolder {
        FeedVH(View v) { super(v); }
    }

    private void showCouponNotification(JSONObject coupon) {
        String title = coupon.optString("title", "优惠券");
        String type = coupon.optString("type", "fixed");
        double value = coupon.optDouble("value", 0);
        int stock = coupon.optInt("stock", 0);
        String claimDeadline = coupon.optString("claimDeadline", null);

        StringBuilder descBuilder = new StringBuilder();
        if ("fixed".equals(type)) {
            descBuilder.append("¥").append((int) value).append(" 优惠券");
        } else {
            descBuilder.append((int) value).append("% 折扣券");
        }
        descBuilder.append("  |  剩余").append(stock).append("张");

        couponContainer.removeAllViews();
        couponContainer.setVisibility(View.VISIBLE);

        View card = LayoutInflater.from(this).inflate(R.layout.item_coupon_card, couponContainer, false);
        ((TextView) card.findViewById(R.id.coupon_title)).setText(title);
        ((TextView) card.findViewById(R.id.coupon_desc)).setText(descBuilder.toString());

        // Countdown timer text
        TextView countdownText = card.findViewById(R.id.coupon_claim_btn);
        // Use coupon_claim_btn for claim; find or add a timer display
        // The card already has coupon_claim_btn for claiming

        MaterialButton claimBtn = card.findViewById(R.id.coupon_claim_btn);
        claimBtn.setOnClickListener(v -> {
            if (!ApiClient.getInstance().isAuthenticated()) {
                Toast.makeText(this, "请先登录后领券", Toast.LENGTH_SHORT).show();
                return;
            }
            String couponId = coupon.optString("id", "");
            if (!couponId.isEmpty()) {
                new Thread(() -> {
                    try {
                        apiService.claimCoupon(couponId);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "领取成功！", Toast.LENGTH_SHORT).show();
                            couponContainer.setVisibility(View.GONE);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "领取失败", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            }
        });

        couponContainer.addView(card);

        // Auto-hide based on claimDeadline
        if (claimDeadline != null) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                long deadlineMs = sdf.parse(claimDeadline).getTime();
                long remainingMs = deadlineMs - System.currentTimeMillis();
                if (remainingMs > 0) {
                    // Update button text with countdown every second
                    final MaterialButton btnRef = claimBtn;
                    Runnable countdownUpdater = new Runnable() {
                        @Override
                        public void run() {
                            long left = deadlineMs - System.currentTimeMillis();
                            if (left <= 0 || couponContainer.getVisibility() != View.VISIBLE) {
                                couponContainer.setVisibility(View.GONE);
                                return;
                            }
                            long mins = left / 60000;
                            long secs = (left % 60000) / 1000;
                            btnRef.setText("领取 (" + mins + ":" + String.format("%02d", secs) + ")");
                            btnRef.postDelayed(this, 1000);
                        }
                    };
                    claimBtn.post(countdownUpdater);
                } else {
                    couponContainer.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                // Fallback: hide after 10s
                couponContainer.postDelayed(() -> couponContainer.setVisibility(View.GONE), 10000);
            }
        } else {
            // No deadline set, hide after 10s
            couponContainer.postDelayed(() -> couponContainer.setVisibility(View.GONE), 10000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (player != null) { player.stop(); player.release(); player = null; }
        if (wsClient != null) wsClient.disconnect();
    }

    // --- Product Adapter ---

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_live_product_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> p = products.get(pos);
            String cover = (String) p.get("coverUrl");
            Object price = p.get("price");
            h.price.setText("¥" + (price instanceof Number ? priceFmt.format(((Number) price).doubleValue()) : "0"));
            Glide.with(h.itemView).load(cover).into(h.thumb);

            h.itemView.setOnClickListener(v -> {
                Product product = mapToProduct(p);
                ProductDetailBottomSheetFragment sheet = ProductDetailBottomSheetFragment.newInstance(product);
                sheet.show(getSupportFragmentManager(), "product_detail");
            });
        }

        @Override public int getItemCount() { return products.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView price;
            VH(View v) { super(v); thumb = v.findViewById(R.id.live_product_thumb); price = v.findViewById(R.id.live_product_price); }
        }
    }

    private Product mapToProduct(Map<String, Object> map) {
        Product p = new Product();
        p.setId((String) map.get("id"));
        p.setTitle((String) map.get("title"));
        p.setDescription((String) map.get("description"));
        Object price = map.get("price");
        p.setPrice(price instanceof Number ? ((Number) price).doubleValue() : 0);
        Object originalPrice = map.get("originalPrice");
        p.setOriginalPrice(originalPrice instanceof Number ? ((Number) originalPrice).doubleValue() : 0);
        p.setCoverUrl((String) map.get("coverUrl"));
        Object stock = map.get("stock");
        p.setStock(stock instanceof Number ? ((Number) stock).intValue() : 0);
        Object salesCount = map.get("salesCount");
        p.setSalesCount(salesCount instanceof Number ? ((Number) salesCount).intValue() : 0);
        p.setStatus((String) map.get("status"));
        p.setCategory((String) map.get("category"));
        p.setCreatedAt((String) map.get("createdAt"));
        return p;
    }
}

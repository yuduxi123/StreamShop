package com.bytedance.streamshop.ui.live;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
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
import com.bumptech.glide.Glide;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LiveRoomActivity extends AppCompatActivity {
    private String roomId;
    private String userId = "1d552fd0-fd85-4952-a0cc-edb4fd674ed5"; // default user
    private String username = "test_user";

    private ExoPlayer player;
    private DanmakuView danmakuView;
    private LiveWebSocketClient wsClient;
    private ApiService apiService;
    private TextView onlineCountText, hotValueText, roomTitle;
    private EditText inputView;
    private RecyclerView productList;
    private ProductAdapter productAdapter;
    private FrameLayout couponContainer;

    private final List<Map<String, Object>> products = new ArrayList<>();
    private NumberFormat priceFmt = NumberFormat.getNumberInstance(Locale.CHINA);

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
        roomTitle = findViewById(R.id.live_room_title);
        onlineCountText = findViewById(R.id.live_online_count);
        hotValueText = findViewById(R.id.live_hot_value);
        inputView = findViewById(R.id.live_input);
        danmakuView = findViewById(R.id.live_danmaku_view);
        couponContainer = findViewById(R.id.live_coupon_container);

        findViewById(R.id.live_back_btn).setOnClickListener(v -> finish());
        findViewById(R.id.live_send_btn).setOnClickListener(v -> sendMessage());
        findViewById(R.id.live_like_btn).setOnClickListener(v -> {
            wsClient.sendLike();
            findViewById(R.id.live_like_btn).animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).withEndAction(
                    () -> findViewById(R.id.live_like_btn).animate().scaleX(1f).scaleY(1f).setDuration(150));
        });

        inputView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });

        productList = findViewById(R.id.live_product_list);
        productList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        productAdapter = new ProductAdapter();
        productList.setAdapter(productAdapter);
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        androidx.media3.ui.PlayerView playerView = findViewById(R.id.live_player_view);
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);

        // Use a sample video accessible in China
        String videoUrl = "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4";
        MediaItem mediaItem = MediaItem.fromUri(videoUrl);
        player.setMediaItem(mediaItem);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                runOnUiThread(() -> Toast.makeText(LiveRoomActivity.this, "视频加载失败，请检查网络", Toast.LENGTH_SHORT).show());
            }
        });
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
                String title = (String) room.get("title");
                runOnUiThread(() -> {
                    if (title != null) roomTitle.setText(title);
                    productAdapter.notifyDataSetChanged();
                });
            } catch (Exception ignored) {}
        }).start();
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
                onlineCountText.setText(onlineCount + "人在线");
                hotValueText.setText("热度: " + hotValue);
            }

            @Override
            public void onOnlineCount(int count) {
                onlineCountText.setText(count + "人在线");
            }

            @Override
            public void onNewComment(JSONObject comment) {
                String user = comment.optString("username", "匿名");
                String content = comment.optString("content", "");
                addSystemMessage(user + ": " + content);
            }

            @Override
            public void onNewDanmaku(JSONObject danmaku) {
                String content = danmaku.optString("content", "");
                String color = danmaku.optString("color", "#FFFFFF");
                danmakuView.addDanmaku(content, color);
            }

            @Override
            public void onLikeUpdate(JSONObject msg) {}

            @Override
            public void onHotValueUpdate(int value) {
                hotValueText.setText("热度: " + value);
            }

            @Override
            public void onProductChanged(String productId, String action) {
                runOnUiThread(() -> {
                    if ("explaining".equals(action)) {
                        Toast.makeText(LiveRoomActivity.this, "主播正在讲解商品 #" + productId.substring(0, 6), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCouponPushed(JSONObject coupon) {
                runOnUiThread(() -> showCouponNotification(coupon));
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

        // Send as danmaku if short, comment if long
        if (text.length() <= 10) {
            wsClient.sendDanmaku(text);
        } else {
            wsClient.sendComment(text);
            addSystemMessage(username + ": " + text);
        }
    }

    private void addSystemMessage(String msg) {
        runOnUiThread(() -> {
            Toast.makeText(LiveRoomActivity.this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void showCouponNotification(JSONObject coupon) {
        String title = coupon.optString("title", "优惠券");
        String type = coupon.optString("type", "fixed");
        double value = coupon.optDouble("value", 0);
        String desc = "fixed".equals(type) ? "¥" + (int) value + " 优惠券" : (int) value + " 折优惠券";

        couponContainer.removeAllViews();
        couponContainer.setVisibility(View.VISIBLE);

        View card = LayoutInflater.from(this).inflate(R.layout.item_coupon_card, couponContainer, false);
        ((TextView) card.findViewById(R.id.coupon_title)).setText(title);
        ((TextView) card.findViewById(R.id.coupon_desc)).setText(desc);

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

        // Auto-hide after 10s
        couponContainer.postDelayed(() -> couponContainer.setVisibility(View.GONE), 10000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

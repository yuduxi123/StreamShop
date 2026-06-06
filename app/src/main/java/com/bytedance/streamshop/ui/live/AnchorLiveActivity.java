package com.bytedance.streamshop.ui.live;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;

import com.google.android.material.imageview.ShapeableImageView;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;

import org.json.JSONArray;
import org.json.JSONObject;

import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnchorLiveActivity extends AppCompatActivity implements ConnectCheckerRtmp {
    private static final String TAG = "AnchorLive";
    private static final int[][] VIDEO_PROFILES = {
            {1280, 720, 30, 1200 * 1024},
            {960, 540, 30, 900 * 1024},
            {640, 480, 24, 700 * 1024}
    };
    private static final int STREAM_ROTATION = 90;

    private String roomId;
    private String roomTitle;
    private String serverIp;
    private String rtmpUrl;

    private RtmpCamera1 rtmpCamera1;
    private SurfaceView cameraPreview;
    private LiveWebSocketClient wsClient;
    private ApiService apiService;

    private TextView hotValueText;
    private ShapeableImageView selfAvatar;
    private TextView selfName;
    private TextView viewerCountText;
    private ImageButton exitBtn;
    private TextView connectingText;
    private View connectingOverlay;
    private RecyclerView productList;
    private ProductAdapter productAdapter;
    private DanmakuView danmakuView;
    private EditText inputView;
    private ImageButton danmakuToggle;
    private ImageButton manageProductsBtn;
    private ImageButton couponBtn;
    private boolean danmakuEnabled = true;
    private RecyclerView activityFeedList;
    private final List<String> activityFeedItems = new ArrayList<>();

    private final List<Map<String, Object>> products = new ArrayList<>();
    private final NumberFormat priceFmt = NumberFormat.getNumberInstance(Locale.CHINA);

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isStreaming = false;
    private boolean surfaceReady = false;
    private boolean startAttempted = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_anchor_live);

        roomId = getIntent().getStringExtra("room_id");
        roomTitle = getIntent().getStringExtra("room_title");
        if (roomId == null) { finish(); return; }

        apiService = new ApiService();
        wsClient = new LiveWebSocketClient();
        priceFmt.setMinimumFractionDigits(0);

        serverIp = extractServerIp();
        rtmpUrl = "rtmp://" + serverIp + ":1935/live/" + roomId;
        Log.d(TAG, "RTMP publish url: " + rtmpUrl);

        initViews();
        loadRoomProducts();
        connectWebSocket();
        // Permissions are pre-granted in MyLiveRoomsActivity before launching here
    }

    private void initViews() {
        cameraPreview = findViewById(R.id.anchor_camera_preview);
        cameraPreview.setZOrderMediaOverlay(true);
        cameraPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceReady = true;
                tryStartStreaming();
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                startAttempted = false;
            }
        });

        selfAvatar = findViewById(R.id.anchor_self_avatar);
        selfName = findViewById(R.id.anchor_self_name);
        viewerCountText = findViewById(R.id.anchor_viewer_count);
        exitBtn = findViewById(R.id.anchor_exit_btn);
        hotValueText = findViewById(R.id.anchor_hot_value);
        connectingOverlay = findViewById(R.id.anchor_connecting_overlay);
        connectingText = findViewById(R.id.anchor_connecting_text);
        danmakuView = findViewById(R.id.anchor_danmaku_view);
        inputView = findViewById(R.id.anchor_chat_input);
        danmakuToggle = findViewById(R.id.anchor_danmaku_toggle);

        // Load own avatar + username
        ApiClient client = ApiClient.getInstance();
        String currentUsername = client.getCurrentUsername();
        String currentAvatar = client.getCurrentAvatarUrl();
        String currentUserId = client.getCurrentUserId();
        if (currentUsername != null && !currentUsername.isEmpty()) {
            selfName.setText(currentUsername);
        } else if (roomTitle != null && !roomTitle.isEmpty()) {
            selfName.setText(roomTitle);
        }
        if (currentAvatar != null && !currentAvatar.isEmpty()) {
            Glide.with(this).load(currentAvatar).circleCrop()
                    .placeholder(R.drawable.ic_avatar_placeholder).into(selfAvatar);
        }

        findViewById(R.id.anchor_self_info).setOnClickListener(v -> {
            if (currentUserId != null) {
                Intent intent = new Intent(AnchorLiveActivity.this, AuthorProfileActivity.class);
                intent.putExtra(AuthorProfileActivity.EXTRA_USER_ID, currentUserId);
                startActivity(intent);
            }
        });

        viewerCountText.setOnClickListener(v -> wsClient.sendGetRoomUsers());

        exitBtn.setOnClickListener(v -> showEndConfirmDialog());

        danmakuToggle.setOnClickListener(v -> {
            danmakuEnabled = !danmakuEnabled;
            danmakuToggle.setImageResource(danmakuEnabled
                    ? R.drawable.ic_danmaku_on : R.drawable.ic_danmaku_off);
            danmakuView.setVisibility(danmakuEnabled ? View.VISIBLE : View.GONE);
        });

        productList = findViewById(R.id.anchor_product_list);
        productList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        productAdapter = new ProductAdapter();
        productList.setAdapter(productAdapter);

        manageProductsBtn = findViewById(R.id.anchor_manage_products_btn);
        manageProductsBtn.setOnClickListener(v -> {
            LiveProductManageBottomSheet sheet = LiveProductManageBottomSheet.newInstance(roomId, products);
            sheet.setOnProductsChangedListener(this::loadRoomProducts);
            sheet.show(getSupportFragmentManager(), "manage_products_sheet");
        });

        couponBtn = findViewById(R.id.anchor_coupon_btn);
        couponBtn.setOnClickListener(v -> {
            LiveCouponCreateBottomSheet sheet = LiveCouponCreateBottomSheet.newInstance(roomId, products);
            sheet.show(getSupportFragmentManager(), "coupon_create_sheet");
        });

        findViewById(R.id.anchor_send_btn).setOnClickListener(v -> {
            String text = inputView.getText().toString().trim();
            if (text.isEmpty()) return;
            inputView.setText("");
            wsClient.sendComment(text);
            danmakuView.addDanmaku("主播: " + text, "#FFFFFF");
            addActivityFeedItem("主播: " + text);
        });

        inputView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                findViewById(R.id.anchor_send_btn).performClick();
                return true;
            }
            return false;
        });

        activityFeedList = findViewById(R.id.anchor_activity_feed);
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

    private void tryStartStreaming() {
        if (surfaceReady && !startAttempted) {
            startAttempted = true;
            startStreaming();
        }
    }

    private void startStreaming() {
        if (rtmpCamera1 != null) {
            try { rtmpCamera1.stopStream(); } catch (Exception ignored) {}
            try { rtmpCamera1.stopPreview(); } catch (Exception ignored) {}
        }

        connectingOverlay.setVisibility(View.VISIBLE);
        connectingText.setText("正在准备摄像头...");
        // statusBadge removed:("连接中");

        try {
            if (prepareVideoWithFallback()) {
                rtmpCamera1.prepareAudio();
                connectingText.setText("正在连接直播服务器...");
                rtmpCamera1.startStream(rtmpUrl);
            } else {
                Toast.makeText(this, "摄像头准备失败，请确认已授权摄像头权限", Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "startStreaming failed", e);
            Toast.makeText(this, "启动摄像头失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private boolean prepareVideoWithFallback() {
        for (int[] profile : VIDEO_PROFILES) {
            int width = profile[0];
            int height = profile[1];
            int fps = profile[2];
            int bitrate = profile[3];
            try {
                rtmpCamera1 = new RtmpCamera1(cameraPreview, this);
                rtmpCamera1.setReTries(10);
                Log.d(TAG, "Trying camera video profile: " + width + "x" + height + "@" + fps);
                if (rtmpCamera1.prepareVideo(width, height, fps, bitrate, STREAM_ROTATION)) {
                    Log.d(TAG, "Camera video profile selected: " + width + "x" + height + "@" + fps);
                    return true;
                }
                safeStopCamera(rtmpCamera1);
            } catch (Exception e) {
                Log.w(TAG, "Camera video profile failed: " + width + "x" + height + "@" + fps, e);
                safeStopCamera(rtmpCamera1);
            }
            rtmpCamera1 = null;
        }
        return false;
    }

    private void safeStopCamera(RtmpCamera1 camera) {
        if (camera == null) return;
        try { camera.stopStream(); } catch (Exception ignored) {}
        try { camera.stopPreview(); } catch (Exception ignored) {}
    }

    @Override
    public void onConnectionStartedRtmp(String rtmpUrl) {
        Log.d(TAG, "RTMP connection started: " + rtmpUrl);
        runOnUiThread(() -> connectingText.setText("正在连接..."));
    }

    @Override
    public void onConnectionSuccessRtmp() {
        Log.d(TAG, "RTMP connection success");
        runOnUiThread(() -> {
            connectingOverlay.setVisibility(View.GONE);
            // statusBadge removed:("推流中");
            isStreaming = true;
            Toast.makeText(this, "直播已开始", Toast.LENGTH_SHORT).show();
        });
        syncLiveStarted();
    }

    @Override
    public void onConnectionFailedRtmp(String reason) {
        Log.e(TAG, "RTMP connection failed: " + reason);
        runOnUiThread(() -> {
            // statusBadge removed:("连接失败");
            connectingText.setText("连接失败，重试中...");
            Toast.makeText(this, "推流连接失败: " + reason, Toast.LENGTH_LONG).show();
        });

        handler.postDelayed(() -> {
            if (!isDestroyed() && !isStreaming) {
                startAttempted = false;
                tryStartStreaming();
            }
        }, 3000);
    }

    @Override
    public void onNewBitrateRtmp(long bitrate) {}

    @Override
    public void onDisconnectRtmp() {
        Log.d(TAG, "RTMP disconnected");
        runOnUiThread(() -> {
            isStreaming = false;
            // statusBadge removed:("已断开");
            Toast.makeText(this, "推流已断开", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onAuthErrorRtmp() {}

    @Override
    public void onAuthSuccessRtmp() {}

    private void loadRoomProducts() {
        new Thread(() -> {
            try {
                Map<String, Object> room = apiService.getRoomDetail(roomId);
                if (room == null) return;
                List<Map<String, Object>> roomProducts = (List<Map<String, Object>>) room.get("products");
                if (roomProducts != null) {
                    products.clear();
                    products.addAll(roomProducts);
                }
                runOnUiThread(() -> productAdapter.notifyDataSetChanged());
            } catch (Exception ignored) {}
        }).start();
    }

    private void syncLiveStarted() {
        new Thread(() -> {
            try {
                Map<String, Object> room = apiService.startLiveRoom(roomId);
                Object streamUrl = room != null ? room.get("streamUrl") : null;
                Log.d(TAG, "Live room start synced, streamUrl=" + streamUrl);
                // live started
            } catch (Exception e) {
                Log.e(TAG, "Start live room failed", e);
                runOnUiThread(() -> {
                    // statusBadge removed:("同步失败");
                    Toast.makeText(this, "直播状态同步失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void connectWebSocket() {
        ApiClient client = ApiClient.getInstance();
        String username = "主播";
        String userId = client.getCurrentUserId();
        if (userId == null) userId = "anchor";

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
            public void onLikeUpdate(JSONObject msg) {
                int count = msg.optInt("likeCount", 0);
                runOnUiThread(() -> hotValueText.setText("热度: " + count));
            }

            @Override
            public void onHotValueUpdate(int value) {
                runOnUiThread(() -> hotValueText.setText("热度: " + value));
            }

            @Override
            public void onProductChanged(String productId, String action) {
                if ("added".equals(action) || "removed".equals(action) || "reordered".equals(action)) {
                    loadRoomProducts();
                }
            }

            @Override
            public void onCouponPushed(JSONObject coupon) {}

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
            public void onDisconnected() {}

            @Override
            public void onError(String message) {}
        });
    }

    private void showEndConfirmDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("结束直播")
                .setMessage("确定要结束本次直播吗？")
                .setPositiveButton("确定结束", (d, w) -> endLive())
                .setNegativeButton("继续直播", null)
                .show();
    }

    private void endLive() {
        isStreaming = false;

        // Capture references, then release on background thread
        RtmpCamera1 camera = rtmpCamera1;
        LiveWebSocketClient ws = wsClient;
        rtmpCamera1 = null;
        wsClient = null;

        new Thread(() -> {
            if (camera != null) {
                try { camera.stopStream(); } catch (Exception ignored) {}
                try { camera.stopPreview(); } catch (Exception ignored) {}
            }
            if (ws != null) {
                try { ws.disconnect(); } catch (Exception ignored) {}
            }
            try { apiService.endLiveRoom(roomId); } catch (Exception ignored) {}
        }).start();

        Toast.makeText(this, "直播已结束", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String extractServerIp() {
        String baseUrl = ApiClient.getInstance().getBaseUrl();
        try {
            String host = baseUrl.replace("http://", "").replace("https://", "");
            host = host.substring(0, host.indexOf(":"));
            if (host.equals("10.0.2.2") || host.equals("localhost") || host.equals("127.0.0.1")) {
                return getLocalIpAddress();
            }
            return host;
        } catch (Exception e) {
            return getLocalIpAddress();
        }
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIp = intf.getInetAddresses();
                     enumIp.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIp.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "10.208.69.9";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RtmpCamera1 camera = rtmpCamera1;
        LiveWebSocketClient ws = wsClient;
        rtmpCamera1 = null;
        wsClient = null;
        handler.removeCallbacksAndMessages(null);

        if (camera != null || ws != null) {
            new Thread(() -> {
                if (camera != null) {
                    try {
                        if (isStreaming) camera.stopStream();
                        camera.stopPreview();
                    } catch (Exception ignored) {}
                }
                if (ws != null) {
                    try { ws.disconnect(); } catch (Exception ignored) {}
                }
            }).start();
        }
    }

    // --- Product Adapter ---

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int type) {
            return new VH(android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_live_product_card, parent, false));
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            Map<String, Object> p = products.get(pos);
            String cover = (String) p.get("coverUrl");
            Object price = p.get("price");
            h.price.setText("¥" + (price instanceof Number ? priceFmt.format(((Number) price).doubleValue()) : "0"));
            com.bumptech.glide.Glide.with(h.itemView).load(cover).into(h.thumb);

            h.itemView.setOnClickListener(v -> {
                String productId = (String) p.get("id");
                if (productId != null) {
                    explainProduct(productId);
                }
                Product product = mapToProduct(p);
                ProductDetailBottomSheetFragment sheet = ProductDetailBottomSheetFragment.newInstance(product);
                sheet.show(getSupportFragmentManager(), "product_detail");
            });
        }

        @Override
        public int getItemCount() { return products.size(); }

        class VH extends RecyclerView.ViewHolder {
            android.widget.ImageView thumb;
            TextView price;
            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.live_product_thumb);
                price = v.findViewById(R.id.live_product_price);
            }
        }
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

    private void explainProduct(String productId) {
        Toast.makeText(this, "正在讲解商品...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                apiService.explainProduct(roomId, productId);
            } catch (Exception ignored) {}
        }).start();
    }
}

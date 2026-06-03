package com.bytedance.streamshop.ui.live;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.data.remote.LiveWebSocketClient;

import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.android.material.button.MaterialButton;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;

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

    private String roomId;
    private String roomTitle;
    private String serverIp;
    private String rtmpUrl;

    private RtmpCamera1 rtmpCamera1;
    private SurfaceView cameraPreview;
    private LiveWebSocketClient wsClient;
    private ApiService apiService;

    private TextView roomTitleView, onlineCountText, hotValueText, statusBadge;
    private TextView connectingText;
    private View connectingOverlay;
    private MaterialButton endLiveBtn;
    private RecyclerView productList;
    private ProductAdapter productAdapter;
    private DanmakuView danmakuView;

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
        setContentView(R.layout.activity_anchor_live);

        roomId = getIntent().getStringExtra("room_id");
        roomTitle = getIntent().getStringExtra("room_title");
        if (roomId == null) { finish(); return; }

        apiService = new ApiService();
        wsClient = new LiveWebSocketClient();
        priceFmt.setMinimumFractionDigits(0);

        serverIp = extractServerIp();
        rtmpUrl = "rtmp://" + serverIp + ":1935/live/" + roomId;

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
            public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        roomTitleView = findViewById(R.id.anchor_room_title);
        onlineCountText = findViewById(R.id.anchor_online_count);
        hotValueText = findViewById(R.id.anchor_hot_value);
        statusBadge = findViewById(R.id.anchor_status_badge);
        endLiveBtn = findViewById(R.id.anchor_end_live_btn);
        connectingOverlay = findViewById(R.id.anchor_connecting_overlay);
        connectingText = findViewById(R.id.anchor_connecting_text);
        danmakuView = findViewById(R.id.anchor_danmaku_view);

        if (roomTitle != null && !roomTitle.isEmpty()) {
            roomTitleView.setText(roomTitle);
        }

        findViewById(R.id.anchor_back_btn).setOnClickListener(v -> showEndConfirmDialog());

        findViewById(R.id.anchor_switch_camera_btn).setOnClickListener(v -> {
            if (rtmpCamera1 != null && isStreaming) {
                try {
                    rtmpCamera1.switchCamera();
                } catch (Exception e) {
                    Toast.makeText(this, "切换摄像头失败", Toast.LENGTH_SHORT).show();
                }
            }
        });

        endLiveBtn.setOnClickListener(v -> showEndConfirmDialog());

        productList = findViewById(R.id.anchor_product_list);
        productList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        productAdapter = new ProductAdapter();
        productList.setAdapter(productAdapter);
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

        try {
            rtmpCamera1 = new RtmpCamera1(cameraPreview, this);
            rtmpCamera1.setReTries(10);

            if (rtmpCamera1.prepareVideo(1280, 720, 30, 1200 * 1024, 270)) {
                rtmpCamera1.prepareAudio();
                connectingText.setText("正在连接直播服务器...");
                rtmpCamera1.startStream(rtmpUrl);
            } else {
                Toast.makeText(this, "摄像头准备失败，请确认已授权摄像头权限", Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception e) {
            Log.e("AnchorLive", "startStreaming failed", e);
            Toast.makeText(this, "启动摄像头失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onConnectionStartedRtmp(String rtmpUrl) {
        runOnUiThread(() -> connectingText.setText("正在连接..."));
    }

    @Override
    public void onConnectionSuccessRtmp() {
        runOnUiThread(() -> {
            connectingOverlay.setVisibility(View.GONE);
            statusBadge.setText("直播中");
            isStreaming = true;
            Toast.makeText(this, "直播已开始", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                try {
                    apiService.startLiveRoom(roomId);
                } catch (Exception ignored) {}
            }).start();
        });
    }

    @Override
    public void onConnectionFailedRtmp(String reason) {
        runOnUiThread(() -> {
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
        runOnUiThread(() -> {
            isStreaming = false;
            statusBadge.setText("已断开");
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
                    onlineCountText.setText(onlineCount + "人在线");
                    hotValueText.setText("热度: " + hotValue);
                });
            }

            @Override
            public void onOnlineCount(int count) {
                runOnUiThread(() -> onlineCountText.setText(count + "人在线"));
            }

            @Override
            public void onNewComment(JSONObject comment) {
                String user = comment.optString("username", "匿名");
                String content = comment.optString("content", "");
                runOnUiThread(() -> danmakuView.addDanmaku(user + ": " + content, "#FFFFFF"));
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
            public void onProductChanged(String productId, String action) {}

            @Override
            public void onCouponPushed(JSONObject coupon) {}

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
        return "10.17.24.7";
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

    private void explainProduct(String productId) {
        Toast.makeText(this, "正在讲解商品...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                apiService.explainProduct(roomId, productId);
            } catch (Exception ignored) {}
        }).start();
    }
}

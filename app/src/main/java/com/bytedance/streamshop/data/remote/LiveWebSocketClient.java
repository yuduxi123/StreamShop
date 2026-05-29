package com.bytedance.streamshop.data.remote;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class LiveWebSocketClient {
    private static final String TAG = "LiveWS";
    // 雷电模拟器不支持 10.0.2.2，用电脑局域网 IP
    // 如果换了 WiFi 导致 IP 变化，需要同步修改这里
    private static final String WS_URL = "ws://10.17.24.7:3000/ws";
    private static final int MAX_RETRY = 10;

    private final OkHttpClient client;
    private WebSocket websocket;
    private String roomId;
    private String userId;
    private String username;
    private WsCallback callback;
    private int retryCount = 0;
    private boolean connected = false;
    private boolean closing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public LiveWebSocketClient() {
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public void connect(String roomId, String userId, String username, WsCallback callback) {
        this.roomId = roomId;
        this.userId = userId;
        this.username = username;
        this.callback = callback;
        this.closing = false;
        doConnect();
    }

    private void doConnect() {
        Request request = new Request.Builder().url(WS_URL).build();
        websocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                connected = true;
                retryCount = 0;
                Log.d(TAG, "Connected, joining room " + roomId);
                // Join room immediately
                send("JOIN_ROOM", roomId, null, null);
                handler.post(() -> {
                    if (callback != null) callback.onConnected();
                });
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    String type = msg.optString("type");
                    handler.post(() -> {
                        if (callback != null) callback.onMessage(msg);
                    });

                    switch (type) {
                        case "ROOM_JOINED":
                            handler.post(() -> {
                                if (callback != null) callback.onRoomJoined(
                                        msg.optInt("onlineCount"), msg.optInt("hotValue"));
                            });
                            break;
                        case "ONLINE_COUNT_UPDATE":
                            handler.post(() -> {
                                if (callback != null) callback.onOnlineCount(msg.optInt("count"));
                            });
                            break;
                        case "NEW_COMMENT":
                            handler.post(() -> {
                                if (callback != null) callback.onNewComment(msg);
                            });
                            break;
                        case "NEW_DANMAKU":
                            handler.post(() -> {
                                if (callback != null) callback.onNewDanmaku(msg);
                            });
                            break;
                        case "LIKE_UPDATE":
                            handler.post(() -> {
                                if (callback != null) callback.onLikeUpdate(msg);
                            });
                            break;
                        case "HOT_VALUE_UPDATE":
                            handler.post(() -> {
                                if (callback != null) callback.onHotValueUpdate(msg.optInt("value"));
                            });
                            break;
                        case "PRODUCT_CHANGED":
                            handler.post(() -> {
                                if (callback != null) callback.onProductChanged(
                                        msg.optString("productId"), msg.optString("action"));
                            });
                            break;
                        case "COUPON_PUSHED":
                            handler.post(() -> {
                                if (callback != null) callback.onCouponPushed(msg.optJSONObject("coupon"));
                            });
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse error: " + e.getMessage());
                }
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                connected = false;
                handler.post(() -> {
                    if (callback != null) callback.onDisconnected();
                });
                if (!closing) scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                connected = false;
                Log.e(TAG, "WS failure: " + t.getMessage());
                handler.post(() -> {
                    if (callback != null) callback.onError(t.getMessage());
                });
                if (!closing) scheduleReconnect();
            }
        });
    }

    public void sendComment(String content) {
        if (!connected) return;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "SEND_COMMENT");
            msg.put("roomId", roomId);
            msg.put("userId", userId);
            msg.put("username", username);
            msg.put("content", content);
            websocket.send(msg.toString());
        } catch (Exception ignored) {}
    }

    public void sendDanmaku(String content) {
        if (!connected) return;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "SEND_DANMAKU");
            msg.put("roomId", roomId);
            msg.put("userId", userId);
            msg.put("username", username);
            msg.put("content", content);
            websocket.send(msg.toString());
        } catch (Exception ignored) {}
    }

    public void sendLike() {
        if (!connected) return;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "LIKE");
            msg.put("roomId", roomId);
            msg.put("userId", userId);
            msg.put("targetType", "live_room");
            msg.put("targetId", roomId);
            websocket.send(msg.toString());
        } catch (Exception ignored) {}
    }

    public void claimCoupon(String couponId) {
        if (!connected) return;
        send("CLAIM_COUPON", roomId, couponId, null);
    }

    private void send(String type, String roomId, String couponId, String content) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", type);
            msg.put("roomId", roomId);
            if (userId != null) msg.put("userId", userId);
            if (username != null) msg.put("username", username);
            if (couponId != null) msg.put("couponId", couponId);
            if (content != null) msg.put("content", content);
            websocket.send(msg.toString());
        } catch (Exception ignored) {}
    }

    private void scheduleReconnect() {
        if (closing || retryCount >= MAX_RETRY) return;
        retryCount++;
        int delay = Math.min(1000 * (int) Math.pow(2, retryCount - 1), 30000);
        Log.d(TAG, "Reconnecting in " + delay + "ms (attempt " + retryCount + ")");
        handler.postDelayed(this::doConnect, delay);
    }

    public void disconnect() {
        closing = true;
        if (websocket != null) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "LEAVE_ROOM");
                msg.put("roomId", roomId);
                websocket.send(msg.toString());
                websocket.close(1000, "Client leaving");
            } catch (Exception ignored) {}
        }
        handler.removeCallbacksAndMessages(null);
        connected = false;
    }

    public boolean isConnected() { return connected; }

    public interface WsCallback {
        default void onConnected() {}
        default void onMessage(JSONObject msg) {}
        default void onRoomJoined(int onlineCount, int hotValue) {}
        default void onOnlineCount(int count) {}
        default void onNewComment(JSONObject comment) {}
        default void onNewDanmaku(JSONObject danmaku) {}
        default void onLikeUpdate(JSONObject msg) {}
        default void onHotValueUpdate(int value) {}
        default void onProductChanged(String productId, String action) {}
        default void onCouponPushed(JSONObject coupon) {}
        default void onDisconnected() {}
        default void onError(String message) {}
    }
}

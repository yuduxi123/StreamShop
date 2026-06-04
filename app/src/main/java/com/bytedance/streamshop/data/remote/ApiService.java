package com.bytedance.streamshop.data.remote;

import com.bytedance.streamshop.domain.model.Comment;
import com.bytedance.streamshop.domain.model.Danmaku;
import com.bytedance.streamshop.domain.model.FeedItem;
import com.bytedance.streamshop.domain.model.Product;
import com.bytedance.streamshop.domain.model.User;
import com.bytedance.streamshop.domain.model.Video;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final ApiClient client;

    public ApiService() {
        this.client = ApiClient.getInstance();
    }

    // ---- Auth ----

    public User login(String username, String password) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "auth/login")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("Login failed: " + resp.code() + " " + respBody);
            }
            Map<String, Object> result = client.getGson().fromJson(respBody, Map.class);
            String token = (String) result.get("token");
            client.setAuthToken(token);
            Map<String, Object> userMap = (Map<String, Object>) result.get("user");
            String loggedUsername = (String) userMap.get("username");
            String loggedAccount = (String) userMap.get("account");
            String loggedUserId = (String) userMap.get("id");
            String avatarUrl = (String) userMap.get("avatarUrl");
            client.setCurrentUsername(loggedUsername);
            client.setCurrentAccount(loggedAccount != null ? loggedAccount : loggedUserId);
            client.setCurrentUserId(loggedUserId);
            client.setCurrentAvatarUrl(avatarUrl);
            return client.getGson().fromJson(client.getGson().toJson(userMap), User.class);
        }
    }

    public User register(String username, String account, String password) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("account", account);
        body.put("password", password);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "auth/register")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("Register failed: " + resp.code() + " " + respBody);
            }
            Map<String, Object> result = client.getGson().fromJson(respBody, Map.class);
            String token = (String) result.get("token");
            client.setAuthToken(token);
            Map<String, Object> userMap = (Map<String, Object>) result.get("user");
            String loggedUsername = (String) userMap.get("username");
            String loggedAccount = (String) userMap.get("account");
            String loggedUserId = (String) userMap.get("id");
            String avatarUrl = (String) userMap.get("avatarUrl");
            client.setCurrentUsername(loggedUsername);
            client.setCurrentAccount(loggedAccount != null ? loggedAccount : loggedUserId);
            client.setCurrentUserId(loggedUserId);
            client.setCurrentAvatarUrl(avatarUrl);
            return client.getGson().fromJson(client.getGson().toJson(userMap), User.class);
        }
    }

    // ---- Videos ----

    public ApiResponse<FeedItem> getFeed(int page, int limit) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "feed?page=" + page + "&limit=" + limit)
                .get()
                .build();
        return executePaginated(request, FeedItem.class);
    }

    public ApiResponse<Video> getVideos(int page, int limit) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "videos?page=" + page + "&limit=" + limit)
                .get()
                .build();
        return executePaginated(request, Video.class);
    }

    public Video getVideoById(String videoId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "videos/" + videoId)
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Get video failed: " + resp.code());
            return client.getGson().fromJson(respBody, Video.class);
        }
    }

    public ApiResponse<Video> getMyVideos(int page, int limit) throws IOException {
        String userId = client.getCurrentUserId();
        String url = client.getBaseUrl() + "videos?page=" + page + "&limit=" + limit;
        if (userId != null) {
            url += "&authorId=" + userId;
        }
        Request request = new Request.Builder().url(url).get().build();
        return executePaginated(request, Video.class);
    }

    public ApiResponse<Video> getUserVideos(String authorId, int page, int limit) throws IOException {
        String url = client.getBaseUrl() + "videos?page=" + page + "&limit=" + limit;
        if (authorId != null && !authorId.isEmpty()) {
            url += "&authorId=" + authorId;
        }
        Request request = new Request.Builder().url(url).get().build();
        return executePaginated(request, Video.class);
    }

    public Video createVideo(String title, String coverUrl, String videoUrl, List<String> tags, String status) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("coverUrl", coverUrl);
        body.put("videoUrl", videoUrl);
        body.put("tags", tags);
        body.put("status", status);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "videos")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Create video failed: " + resp.code());
            return client.getGson().fromJson(respBody, Video.class);
        }
    }

    public Video updateVideo(String videoId, String title, String coverUrl, String videoUrl, List<String> tags, String status) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("coverUrl", coverUrl);
        body.put("videoUrl", videoUrl);
        body.put("tags", tags);
        body.put("status", status);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "videos/" + videoId)
                .patch(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Update video failed: " + resp.code());
            return client.getGson().fromJson(respBody, Video.class);
        }
    }

    public boolean deleteVideo(String videoId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "videos/" + videoId)
                .delete()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            return resp.isSuccessful() || resp.code() == 204;
        }
    }

    // ---- Products ----

    public Product createProduct(String title, String coverUrl, double price, int stock) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("coverUrl", coverUrl);
        body.put("price", price);
        body.put("stock", stock);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "products")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Create product failed: " + resp.code());
            return client.getGson().fromJson(respBody, Product.class);
        }
    }

    public boolean bindVideoToProduct(String productId, String videoId, long timestampMs) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("videoId", videoId);
        body.put("timestampMs", timestampMs);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "products/" + productId + "/bind-video")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            return resp.isSuccessful();
        }
    }

    public int incrementVideoView(String videoId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "videos/" + videoId + "/view")
                .post(RequestBody.create("{}", JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("Increment view failed: " + resp.code());
            }
            Map<String, Object> result = client.getGson().fromJson(respBody, Map.class);
            Object val = result.get("viewCount");
            return val instanceof Number ? ((Number) val).intValue() : 0;
        }
    }

    // ---- Interactions ----

    public boolean toggleLike(String targetType, String targetId) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        return executePost("interactions/likes", body, "liked");
    }

    public List<Map<String, Object>> getLikedVideos() throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "interactions/likes?targetType=video")
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Get liked videos failed: " + resp.code());
            return client.getGson().fromJson(body, List.class);
        }
    }

    public Map<String, Object> getLikeStatus(String targetType, String targetId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "interactions/likes/status?targetType=" + targetType + "&targetId=" + targetId)
                .get()
                .build();
        return executeGetMap(request);
    }

    public Map<String, Object> getCollectionStatus(String targetType, String targetId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "interactions/collections/status?targetType=" + targetType + "&targetId=" + targetId)
                .get()
                .build();
        return executeGetMap(request);
    }

    public Map<String, Object> getLikeCount(String targetType, String targetId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "interactions/likes/count?targetType=" + targetType + "&targetId=" + targetId)
                .get()
                .build();
        return executeGetMap(request);
    }

    public boolean toggleCollection(String targetType, String targetId) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        return executePost("interactions/collections", body, "collected");
    }

    public List<Map<String, Object>> getCollections(String targetType) throws IOException {
        String url = client.getBaseUrl() + "interactions/collections";
        if (targetType != null && !targetType.isEmpty()) {
            url += "?targetType=" + targetType;
        }
        Request request = new Request.Builder().url(url).get().build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Get collections failed: " + resp.code());
            return client.getGson().fromJson(body, List.class);
        }
    }

    // ---- Comments ----

    public ApiResponse<Comment> getComments(String targetType, String targetId, int page, int limit) throws IOException {
        String url = client.getBaseUrl() + "comments?targetType=" + targetType + "&targetId=" + targetId + "&page=" + page + "&limit=" + limit;
        Request request = new Request.Builder().url(url).get().build();
        return executePaginated(request, Comment.class);
    }

    public Comment postComment(String targetType, String targetId, String content) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("content", content);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "comments")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("Post comment failed: " + resp.code());
            }
            return client.getGson().fromJson(respBody, Comment.class);
        }
    }

    // ---- Cart ----

    public boolean addToCart(String productId, int quantity) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        return executePost("cart", body, null);
    }

    public List<Map<String, Object>> getCart() throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "cart")
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Get cart failed: " + resp.code());
            return client.getGson().fromJson(body, List.class);
        }
    }

    public boolean updateCartItem(String itemId, Map<String, Object> updates) throws IOException {
        String json = client.getGson().toJson(updates);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "cart/" + itemId)
                .patch(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            return resp.isSuccessful();
        }
    }

    public boolean deleteCartItem(String itemId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "cart/" + itemId)
                .delete()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            return resp.isSuccessful();
        }
    }

    // ---- Orders ----

    public Map<String, Object> createOrder(String shippingAddress) throws IOException {
        return createOrder(shippingAddress, null);
    }

    public Map<String, Object> createOrder(String shippingAddress, List<Map<String, Object>> items) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("shippingAddress", shippingAddress);
        if (items != null && !items.isEmpty()) {
            body.put("items", items);
        }
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "orders")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Create order failed: " + resp.code() + " " + respBody);
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public ApiResponse<Map<String, Object>> getOrders(String status, int page, int limit) throws IOException {
        String url = client.getBaseUrl() + "orders?page=" + page + "&limit=" + limit;
        if (status != null && !status.isEmpty() && !status.equals("all")) {
            url += "&status=" + status;
        }
        Request request = new Request.Builder().url(url).get().build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Get orders failed: " + resp.code());
            Type type = new TypeToken<ApiResponse<Map<String, Object>>>(){}.getType();
            return client.getGson().fromJson(respBody, type);
        }
    }

    public Map<String, Object> getOrderDetail(String orderId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "orders/" + orderId)
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Get order failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public Map<String, Object> payOrder(String orderId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "orders/" + orderId + "/pay")
                .post(RequestBody.create("{}", JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Pay failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public Map<String, Object> cancelOrder(String orderId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "orders/" + orderId + "/cancel")
                .patch(RequestBody.create("{}", JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Cancel failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    // ---- Danmaku ----

    public List<Danmaku> getDanmaku(String videoId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "danmaku?videoId=" + videoId)
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Get danmaku failed: " + resp.code());
            Type listType = new TypeToken<List<Danmaku>>(){}.getType();
            return client.getGson().fromJson(body, listType);
        }
    }

    public Danmaku postDanmaku(String videoId, String content, String color, long timestampMs) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("videoId", videoId);
        body.put("content", content);
        body.put("color", color);
        body.put("timestampMs", timestampMs);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "danmaku")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Post danmaku failed: " + resp.code());
            return client.getGson().fromJson(respBody, Danmaku.class);
        }
    }

    // ---- Generic helpers ----

    private boolean executePost(String path, Map<String, ?> body, String successKey) throws IOException {
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + path)
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("Request failed: " + resp.code() + " " + respBody);
            }
            if (successKey != null) {
                Map<String, Object> result = client.getGson().fromJson(respBody, Map.class);
                Object val = result.get(successKey);
                return val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(String.valueOf(val));
            }
            return true;
        }
    }

    private Map<String, Object> executeGetMap(Request request) throws IOException {
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("Request failed: " + resp.code());
            }
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    // ---- Upload ----

    public Map<String, Object> uploadFile(String filePath, String mimeType) throws IOException {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        MediaType mediaType = MediaType.parse(mimeType);
        if (mediaType == null) mediaType = MediaType.parse("application/octet-stream");

        okhttp3.MultipartBody body = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", fileName,
                        okhttp3.RequestBody.create(new java.io.File(filePath), mediaType))
                .build();

        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "upload")
                .post(body)
                .build();

        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Upload failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    // ---- Profile ----

    public User updateMyProfile(Map<String, Object> updates) throws IOException {
        String json = client.getGson().toJson(updates);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "auth/me")
                .patch(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Update profile failed: " + resp.code());
            return client.getGson().fromJson(respBody, User.class);
        }
    }

    // ---- Live Rooms ----

    public ApiResponse<Map<String, Object>> getRooms(int page, int limit) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms?page=" + page + "&limit=" + limit)
                .get()
                .build();
        return executePaginatedMap(request);
    }

    public ApiResponse<Map<String, Object>> getMyLiveRooms(int page, int limit) throws IOException {
        String userId = client.getCurrentUserId();
        String url = client.getBaseUrl() + "live/rooms?page=" + page + "&limit=" + limit;
        if (userId != null) {
            url += "&anchorId=" + userId;
        }
        Request request = new Request.Builder().url(url).get().build();
        return executePaginatedMap(request);
    }

    public Map<String, Object> getRoomDetail(String roomId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms/" + roomId)
                .get()
                .build();
        return executeGetMap(request);
    }

    public Map<String, Object> createLiveRoom(String title, String coverUrl) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("coverUrl", coverUrl);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Create live room failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public Map<String, Object> updateLiveRoom(String roomId, String title, String coverUrl) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("coverUrl", coverUrl);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms/" + roomId)
                .patch(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Update live room failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public Map<String, Object> startLiveRoom(String roomId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms/" + roomId + "/start")
                .post(RequestBody.create("{}", JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Start live failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public Map<String, Object> endLiveRoom(String roomId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms/" + roomId + "/end")
                .post(RequestBody.create("{}", JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("End live failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public boolean deleteLiveRoom(String roomId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms/" + roomId)
                .delete()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            return resp.isSuccessful();
        }
    }

    public boolean bindProductToLiveRoom(String roomId, String productId) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms/" + roomId + "/products")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            return resp.isSuccessful();
        }
    }

    public boolean explainProduct(String roomId, String productId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "live/rooms/" + roomId + "/product/" + productId + "/explain")
                .post(RequestBody.create("{}", JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            return resp.isSuccessful();
        }
    }

    // ---- Coupons ----

    public boolean claimCoupon(String couponId) throws IOException {
        Map<String, Object> body = new HashMap<>();
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "coupons/" + couponId + "/claim")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            return resp.isSuccessful();
        }
    }

    // ---- Follow ----

    public boolean toggleFollow(String userId) throws IOException {
        Map<String, Object> body = new HashMap<>();
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "interactions/follow/" + userId)
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Follow failed: " + resp.code());
            Map<String, Object> result = client.getGson().fromJson(respBody, Map.class);
            Object val = result.get("following");
            return val instanceof Boolean ? (Boolean) val : false;
        }
    }

    public boolean isFollowing(String userId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "interactions/follow/" + userId + "/status")
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Check follow failed: " + resp.code());
            Map<String, Object> result = client.getGson().fromJson(respBody, Map.class);
            Object val = result.get("following");
            return val instanceof Boolean ? (Boolean) val : false;
        }
    }

    public List<Map<String, Object>> getFollowing() throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "interactions/following")
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Get following failed: " + resp.code());
            return client.getGson().fromJson(body, List.class);
        }
    }

    public List<Map<String, Object>> getFollowers() throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "interactions/followers")
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Get followers failed: " + resp.code());
            return client.getGson().fromJson(body, List.class);
        }
    }

    // ---- Users ----

    public User getUserProfile(String userId) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "users/" + userId)
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("User not found: " + resp.code());
            }
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            return client.getGson().fromJson(respBody, User.class);
        }
    }

    public List<Map<String, Object>> searchUsers(String query) throws IOException {
        String encoded = java.net.URLEncoder.encode(query, "UTF-8");
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "users/search?q=" + encoded)
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Search users failed: " + resp.code());
            return client.getGson().fromJson(body, List.class);
        }
    }

    public List<Map<String, Object>> searchVideos(String query) throws IOException {
        String encoded = java.net.URLEncoder.encode(query, "UTF-8");
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "videos/search?q=" + encoded)
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Search videos failed: " + resp.code());
            return client.getGson().fromJson(body, List.class);
        }
    }

    public List<Map<String, Object>> searchProducts(String query) throws IOException {
        String encoded = java.net.URLEncoder.encode(query, "UTF-8");
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "products?search=" + encoded)
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Search products failed: " + resp.code());
            Map<String, Object> result = client.getGson().fromJson(body, Map.class);
            Object data = result.get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : new ArrayList<>();
        }
    }

    // ---- Stats ----

    public Map<String, Object> getDashboardStats() throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "stats/dashboard")
                .get()
                .build();
        return executeGetMap(request);
    }

    // ---- Messages ----

    public List<Map<String, Object>> getConversations() throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "messages/conversations")
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) throw new IOException("Get conversations failed: " + resp.code());
            return client.getGson().fromJson(body, List.class);
        }
    }

    public ApiResponse<Map<String, Object>> getMessages(String conversationId, int page, int limit) throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "messages/conversations/" + conversationId
                        + "?page=" + page + "&limit=" + limit)
                .get()
                .build();
        return executePaginatedMap(request);
    }

    public Map<String, Object> sendMessage(String receiverId, String content) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("receiverId", receiverId);
        body.put("content", content);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "messages")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Send message failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public Map<String, Object> forwardVideo(String videoId, String receiverId) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("receiverId", receiverId);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "videos/" + videoId + "/share")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Forward video failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public Map<String, Object> createGroupChat(String name, List<String> memberIds) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("memberIds", memberIds);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "messages/conversations/group")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Create group failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public Map<String, Object> sendGroupMessage(String conversationId, String content) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("conversationId", conversationId);
        body.put("content", content);
        String json = client.getGson().toJson(body);
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "messages")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Send group message failed: " + resp.code());
            return client.getGson().fromJson(respBody, Map.class);
        }
    }

    public int getUnreadMessageCount() throws IOException {
        Request request = new Request.Builder()
                .url(client.getBaseUrl() + "messages/unread-count")
                .get()
                .build();
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IOException("Get unread count failed: " + resp.code());
            Map<String, Object> result = client.getGson().fromJson(respBody, Map.class);
            Object val = result.get("count");
            return val instanceof Number ? ((Number) val).intValue() : 0;
        }
    }

    // ---- Helpers ----

    private <T> ApiResponse<T> executePaginated(Request request, Class<T> clazz) throws IOException {
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("Request failed: " + resp.code());
            }
            Type type = TypeToken.getParameterized(ApiResponse.class, clazz).getType();
            return client.getGson().fromJson(respBody, type);
        }
    }

    private ApiResponse<Map<String, Object>> executePaginatedMap(Request request) throws IOException {
        try (Response resp = client.getHttpClient().newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("Request failed: " + resp.code());
            }
            Type type = new TypeToken<ApiResponse<Map<String, Object>>>(){}.getType();
            return client.getGson().fromJson(respBody, type);
        }
    }
}

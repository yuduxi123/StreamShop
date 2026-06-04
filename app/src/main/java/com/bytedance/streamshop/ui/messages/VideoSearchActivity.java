package com.bytedance.streamshop.ui.messages;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.Product;
import com.bytedance.streamshop.domain.model.Video;
import com.bytedance.streamshop.ui.feed.ProductDetailBottomSheetFragment;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VideoSearchActivity extends AppCompatActivity {
    private ApiService apiService;
    private EditText searchInput;
    private TextView searchBtn, tabVideo, tabProduct;
    private RecyclerView videoList, productList;
    private TextView emptyView;

    private final List<Map<String, Object>> videoResults = new ArrayList<>();
    private final List<Map<String, Object>> productResults = new ArrayList<>();
    private VideoAdapter videoAdapter;
    private ProductAdapter productAdapter;

    private boolean showingProducts = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_search);

        apiService = new ApiService();

        searchInput = findViewById(R.id.search_input);
        searchBtn = findViewById(R.id.search_btn);
        tabVideo = findViewById(R.id.tab_video);
        tabProduct = findViewById(R.id.tab_product);
        videoList = findViewById(R.id.search_video_list);
        productList = findViewById(R.id.search_product_list);
        emptyView = findViewById(R.id.search_empty);
        ImageButton backBtn = findViewById(R.id.search_back_btn);

        backBtn.setOnClickListener(v -> finish());

        videoList.setLayoutManager(new LinearLayoutManager(this));
        productList.setLayoutManager(new LinearLayoutManager(this));
        videoAdapter = new VideoAdapter();
        productAdapter = new ProductAdapter();
        videoList.setAdapter(videoAdapter);
        productList.setAdapter(productAdapter);

        // Tab switching
        tabVideo.setOnClickListener(v -> switchTab(false));
        tabProduct.setOnClickListener(v -> switchTab(true));

        // Search trigger
        searchBtn.setOnClickListener(v -> performSearch());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> performSearch();
                handler.postDelayed(searchRunnable, 400);
            }
        });
    }

    private void switchTab(boolean showProducts) {
        showingProducts = showProducts;
        tabVideo.setTextColor(showProducts ? 0xFF999999 : 0xFF333333);
        tabVideo.getPaint().setFakeBoldText(!showProducts);
        tabProduct.setTextColor(showProducts ? 0xFF333333 : 0xFF999999);
        tabProduct.getPaint().setFakeBoldText(showProducts);
        videoList.setVisibility(showProducts ? View.GONE : View.VISIBLE);
        productList.setVisibility(showProducts ? View.VISIBLE : View.GONE);
        updateEmptyView();
    }

    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) return;

        searchBtn.setText("...");
        new Thread(() -> {
            try {
                List<Map<String, Object>> videos = apiService.searchVideos(query);
                List<Map<String, Object>> products = apiService.searchProducts(query);
                runOnUiThread(() -> {
                    videoResults.clear();
                    productResults.clear();
                    if (videos != null) videoResults.addAll(videos);
                    if (products != null) productResults.addAll(products);
                    videoAdapter.notifyDataSetChanged();
                    productAdapter.notifyDataSetChanged();
                    updateEmptyView();
                    searchBtn.setText("搜索");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "搜索失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    searchBtn.setText("搜索");
                });
            }
        }).start();
    }

    private void updateEmptyView() {
        boolean empty = showingProducts ? productResults.isEmpty() : videoResults.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            String q = searchInput.getText().toString().trim();
            emptyView.setText(q.isEmpty() ? "请输入关键词搜索" : "暂无搜索结果");
        }
    }

    private void openVideoFeed(int startPos) {
        List<Video> videos = new ArrayList<>();
        Gson gson = new Gson();
        for (Map<String, Object> map : videoResults) {
            Video v = gson.fromJson(gson.toJson(map), Video.class);
            videos.add(v);
        }
        Intent intent = new Intent(this, VideoSearchFeedActivity.class);
        intent.putExtra("videos", new ArrayList<>(videos));
        intent.putExtra("start_position", startPos);
        startActivity(intent);
    }

    // ---- Video Adapter ----
    private class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_video, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> v = videoResults.get(pos);
            h.title.setText((String) v.get("title"));
            Map<String, Object> author = (Map<String, Object>) v.get("author");
            if (author != null) {
                h.authorName.setText((String) author.get("username"));
                String avatar = (String) author.get("avatarUrl");
                if (avatar != null && !avatar.isEmpty()) {
                    Glide.with(h.avatar).load(avatar).circleCrop().into(h.avatar);
                } else {
                    h.avatar.setImageResource(R.drawable.ic_avatar_placeholder);
                }
            }
            String cover = (String) v.get("coverUrl");
            if (cover != null && !cover.isEmpty()) {
                Glide.with(h.cover).load(cover).into(h.cover);
            } else {
                h.cover.setImageResource(R.drawable.ic_avatar_placeholder);
            }
            h.itemView.setOnClickListener(v2 -> openVideoFeed(pos));
        }

        @Override public int getItemCount() { return videoResults.size(); }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView cover, avatar;
            TextView title, authorName;
            VH(View v) {
                super(v);
                cover = v.findViewById(R.id.search_video_cover);
                avatar = v.findViewById(R.id.search_video_author_avatar);
                title = v.findViewById(R.id.search_video_title);
                authorName = v.findViewById(R.id.search_video_author_name);
            }
        }
    }

    // ---- Product Adapter ----
    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_product, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> p = productResults.get(pos);
            h.title.setText((String) p.get("title"));

            Object priceObj = p.get("price");
            String price = priceObj instanceof Number ? String.valueOf(((Number) priceObj).doubleValue()) : "0";
            h.price.setText("¥" + price);

            Object salesObj = p.get("salesCount");
            String sales = salesObj instanceof Number ? String.valueOf(((Number) salesObj).intValue()) : "0";
            h.sales.setText("已售 " + sales);

            String cover = (String) p.get("coverUrl");
            if (cover != null && !cover.isEmpty()) {
                Glide.with(h.cover).load(cover).into(h.cover);
            } else {
                h.cover.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            h.itemView.setOnClickListener(v -> {
                Gson gson = new Gson();
                Product product = gson.fromJson(gson.toJson(p), Product.class);
                ProductDetailBottomSheetFragment sheet = ProductDetailBottomSheetFragment.newInstance(product);
                sheet.show(getSupportFragmentManager(), "product_detail");
            });
        }

        @Override public int getItemCount() { return productResults.size(); }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView cover;
            TextView title, price, sales;
            VH(View v) {
                super(v);
                cover = v.findViewById(R.id.search_product_cover);
                title = v.findViewById(R.id.search_product_title);
                price = v.findViewById(R.id.search_product_price);
                sales = v.findViewById(R.id.search_product_sales);
            }
        }
    }
}

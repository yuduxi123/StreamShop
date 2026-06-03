package com.bytedance.streamshop.ui.live;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LiveRoomEditActivity extends AppCompatActivity {
    private EditText titleInput;
    private EditText productNameInput, productPriceInput, productStockInput;
    private TextView headerTitle, productHint;
    private ImageView coverPreview;
    private MaterialButton pickCoverBtn, pickProductImageBtn, addProductBtn;
    private TextView saveBtn;
    private RecyclerView productListView;

    private String editingRoomId;
    private String uploadedCoverUrl;
    private String uploadedProductImageUrl;
    private boolean isSaving;

    private final List<PendingProduct> pendingProducts = new ArrayList<>();
    private ProductListAdapter productListAdapter;

    private final ActivityResultLauncher<String> pickCoverLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onCoverPicked(uri);
            });

    private final ActivityResultLauncher<String> pickProductImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onProductImagePicked(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room_edit);

        titleInput = findViewById(R.id.liveroom_title_input);
        headerTitle = findViewById(R.id.liveroom_edit_title);
        pickCoverBtn = findViewById(R.id.liveroom_pick_cover);
        saveBtn = findViewById(R.id.liveroom_edit_save);
        coverPreview = findViewById(R.id.liveroom_cover_preview);
        productNameInput = findViewById(R.id.liveroom_product_name);
        productPriceInput = findViewById(R.id.liveroom_product_price);
        productStockInput = findViewById(R.id.liveroom_product_stock);
        pickProductImageBtn = findViewById(R.id.liveroom_pick_product_image);
        addProductBtn = findViewById(R.id.liveroom_add_product);
        productListView = findViewById(R.id.liveroom_product_list);
        productHint = findViewById(R.id.liveroom_product_image_hint);

        findViewById(R.id.liveroom_edit_back).setOnClickListener(v -> finish());

        pickCoverBtn.setOnClickListener(v -> pickCoverLauncher.launch("image/*"));
        pickProductImageBtn.setOnClickListener(v -> pickProductImageLauncher.launch("image/*"));
        addProductBtn.setOnClickListener(v -> addProduct());
        saveBtn.setOnClickListener(v -> save());

        productListAdapter = new ProductListAdapter();
        productListView.setLayoutManager(new LinearLayoutManager(this));
        productListView.setAdapter(productListAdapter);

        // Check if editing
        editingRoomId = getIntent().getStringExtra("room_id");
        if (editingRoomId != null) {
            headerTitle.setText("编辑直播间");
            titleInput.setText(getIntent().getStringExtra("room_title"));
            String existingCover = getIntent().getStringExtra("room_cover");
            if (existingCover != null && !existingCover.isEmpty()) {
                uploadedCoverUrl = existingCover;
                coverPreview.setVisibility(View.VISIBLE);
                Glide.with(this).load(existingCover).into(coverPreview);
            }
        }
    }

    private void onCoverPicked(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            String mimeType = resolver.getType(uri);
            String displayName = getFileName(uri);
            if (displayName == null) displayName = "cover.jpg";

            File tempFile = new File(getCacheDir(), displayName);
            try (InputStream in = resolver.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                if (in == null) {
                    Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }

            coverPreview.setVisibility(View.VISIBLE);
            Glide.with(this).load(tempFile).into(coverPreview);

            final String mime = mimeType != null ? mimeType : "image/jpeg";
            new Thread(() -> {
                try {
                    Map<String, Object> result = new ApiService().uploadFile(tempFile.getAbsolutePath(), mime);
                    String url = (String) result.get("url");
                    runOnUiThread(() -> {
                        uploadedCoverUrl = url;
                        Toast.makeText(this, "封面上传成功", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "上传失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onProductImagePicked(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            String mimeType = resolver.getType(uri);
            String displayName = getFileName(uri);
            if (displayName == null) displayName = "product.jpg";

            File tempFile = new File(getCacheDir(), displayName);
            try (InputStream in = resolver.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                if (in == null) {
                    Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }

            final String mime = mimeType != null ? mimeType : "image/jpeg";
            new Thread(() -> {
                try {
                    Map<String, Object> result = new ApiService().uploadFile(tempFile.getAbsolutePath(), mime);
                    String url = (String) result.get("url");
                    runOnUiThread(() -> {
                        uploadedProductImageUrl = url;
                        Toast.makeText(this, "商品图片上传成功", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "上传失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addProduct() {
        String name = productNameInput.getText().toString().trim();
        String priceStr = productPriceInput.getText().toString().trim();
        String stockStr = productStockInput.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "请输入商品名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (priceStr.isEmpty()) {
            Toast.makeText(this, "请输入商品价格", Toast.LENGTH_SHORT).show();
            return;
        }
        if (stockStr.isEmpty()) {
            Toast.makeText(this, "请输入库存数量", Toast.LENGTH_SHORT).show();
            return;
        }
        if (uploadedProductImageUrl == null || uploadedProductImageUrl.isEmpty()) {
            Toast.makeText(this, "请选择商品图片", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        int stock;
        try {
            price = Double.parseDouble(priceStr);
            stock = Integer.parseInt(stockStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "价格或库存格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }

        PendingProduct pp = new PendingProduct(name, uploadedProductImageUrl, price, stock);
        pendingProducts.add(pp);
        productListAdapter.notifyItemInserted(pendingProducts.size() - 1);

        productNameInput.setText("");
        productPriceInput.setText("");
        productStockInput.setText("");
        uploadedProductImageUrl = null;
        updateProductListVisibility();
    }

    private void removeProduct(int position) {
        pendingProducts.remove(position);
        productListAdapter.notifyItemRemoved(position);
        updateProductListVisibility();
    }

    private void updateProductListVisibility() {
        boolean empty = pendingProducts.isEmpty();
        productHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        productListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void save() {
        if (isSaving) return;

        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入直播间标题", Toast.LENGTH_SHORT).show();
            return;
        }

        String coverUrl = uploadedCoverUrl != null ? uploadedCoverUrl : "";

        isSaving = true;
        saveBtn.setEnabled(false);
        saveBtn.setText("保存中...");

        new Thread(() -> {
            try {
                ApiService api = new ApiService();
                String roomId;

                // Create or update room
                if (editingRoomId != null) {
                    api.updateLiveRoom(editingRoomId, title, coverUrl);
                    roomId = editingRoomId;
                } else {
                    Map<String, Object> room = api.createLiveRoom(title, coverUrl);
                    roomId = (String) room.get("id");
                }

                // Create products and bind to room
                for (PendingProduct pp : pendingProducts) {
                    try {
                        com.bytedance.streamshop.domain.model.Product product =
                                api.createProduct(pp.title, pp.coverUrl, pp.price, pp.stock);
                        api.bindProductToLiveRoom(roomId, product.getId());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(LiveRoomEditActivity.this,
                                "商品 " + pp.title + " 关联失败", Toast.LENGTH_SHORT).show());
                    }
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, editingRoomId != null ? "更新成功" : "创建成功", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    isSaving = false;
                    saveBtn.setEnabled(true);
                    saveBtn.setText("保存");
                });
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        String name = null;
        try {
            ContentResolver resolver = getContentResolver();
            android.database.Cursor cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx);
                cursor.close();
            }
        } catch (Exception ignored) {}
        if (name == null) name = uri.getLastPathSegment();
        return name;
    }

    private static class PendingProduct {
        String title;
        String coverUrl;
        double price;
        int stock;

        PendingProduct(String title, String coverUrl, double price, int stock) {
            this.title = title;
            this.coverUrl = coverUrl;
            this.price = price;
            this.stock = stock;
        }
    }

    private class ProductListAdapter extends RecyclerView.Adapter<ProductListAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_edit_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PendingProduct pp = pendingProducts.get(position);
            h.nameText.setText(pp.title);
            h.priceText.setText("¥" + (int) pp.price);
            Glide.with(h.thumbView).load(pp.coverUrl).into(h.thumbView);
            h.removeBtn.setOnClickListener(v -> removeProduct(h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return pendingProducts.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumbView;
            TextView nameText, priceText;
            android.widget.Button removeBtn;

            VH(View v) {
                super(v);
                thumbView = v.findViewById(R.id.edit_product_thumb);
                nameText = v.findViewById(R.id.edit_product_name);
                priceText = v.findViewById(R.id.edit_product_price);
                removeBtn = v.findViewById(R.id.edit_product_remove);
            }
        }
    }
}

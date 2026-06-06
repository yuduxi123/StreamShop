package com.bytedance.streamshop.ui.profile;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
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
import com.bytedance.streamshop.domain.model.Video;
import com.bytedance.streamshop.domain.model.Product;
import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoEditActivity extends AppCompatActivity {
    private EditText titleInput, tagsInput, productNameInput, productPriceInput, productOriginalPriceInput,
            productStockInput, timestampInput;
    private Spinner statusSpinner;
    private TextView headerTitle, videoInfoText, productHint;
    private ImageView coverPreview, productPreview;
    private ProgressBar videoProgress, coverProgress, productProgress;
    private Button pickVideoBtn, pickCoverBtn, pickProductImageBtn, addProductBtn, saveBtn,
            aiTitleBtn, aiSellingPointsBtn;
    private RecyclerView productListView;
    private String editingVideoId;
    private boolean isSaving;

    private String uploadedVideoUrl;
    private String uploadedCoverUrl;
    private String uploadedProductImageUrl;
    private File videoFile;
    private File coverFile;

    private String editingProductId; // non-null when editing an existing product (inline form mode)
    private final List<PendingProduct> pendingProducts = new ArrayList<>();
    private ProductListAdapter productListAdapter;

    private static final String[] STATUS_OPTIONS = {"draft", "published", "taken_down"};
    private static final String[] STATUS_LABELS = {"草稿", "已发布", "已下架"};

    // File picker for video
    private final ActivityResultLauncher<String> pickVideoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onFilePicked(uri, true);
            });

    // File picker for cover image
    private final ActivityResultLauncher<String> pickCoverLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onFilePicked(uri, false);
            });

    // File picker for product image
    private final ActivityResultLauncher<String> pickProductImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onProductImagePicked(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);

        titleInput = findViewById(R.id.video_edit_input_title);
        tagsInput = findViewById(R.id.video_edit_input_tags);
        statusSpinner = findViewById(R.id.video_edit_status_spinner);
        headerTitle = findViewById(R.id.video_edit_title);
        pickVideoBtn = findViewById(R.id.video_edit_pick_video);
        pickCoverBtn = findViewById(R.id.video_edit_pick_cover);
        saveBtn = findViewById(R.id.video_edit_save);
        videoInfoText = findViewById(R.id.video_edit_video_info);
        coverPreview = findViewById(R.id.video_edit_cover_preview);
        videoProgress = findViewById(R.id.video_edit_video_progress);
        coverProgress = findViewById(R.id.video_edit_cover_progress);

        // Product section views
        pickProductImageBtn = findViewById(R.id.video_edit_pick_product_image);
        addProductBtn = findViewById(R.id.video_edit_add_product);
        productNameInput = findViewById(R.id.video_edit_product_name);
        productPriceInput = findViewById(R.id.video_edit_product_price);
        productOriginalPriceInput = findViewById(R.id.video_edit_product_original_price);
        productStockInput = findViewById(R.id.video_edit_product_stock);
        timestampInput = findViewById(R.id.video_edit_product_timestamp);
        productPreview = findViewById(R.id.video_edit_product_preview);
        productProgress = findViewById(R.id.video_edit_product_progress);
        productListView = findViewById(R.id.video_edit_product_list);
        productHint = findViewById(R.id.video_edit_product_hint);
        aiTitleBtn = findViewById(R.id.video_edit_ai_title);
        aiSellingPointsBtn = findViewById(R.id.video_edit_ai_selling_points);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, STATUS_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(adapter);
        statusSpinner.setSelection(1); // default: published

        findViewById(R.id.video_edit_back).setOnClickListener(v -> finish());
        saveBtn.setOnClickListener(v -> save());

        pickVideoBtn.setOnClickListener(v -> pickVideoLauncher.launch("video/*"));
        pickCoverBtn.setOnClickListener(v -> pickCoverLauncher.launch("image/*"));
        pickProductImageBtn.setOnClickListener(v -> pickProductImageLauncher.launch("image/*"));
        addProductBtn.setOnClickListener(v -> addOrUpdateProduct());

        aiTitleBtn.setOnClickListener(v -> generateAiTitle());
        aiSellingPointsBtn.setOnClickListener(v -> generateAiSellingPoints());

        // Product list
        productListAdapter = new ProductListAdapter();
        productListView.setLayoutManager(new LinearLayoutManager(this));
        productListView.setAdapter(productListAdapter);

        // Check if editing existing video
        editingVideoId = getIntent().getStringExtra("video_id");
        if (editingVideoId != null) {
            headerTitle.setText("编辑视频");
            titleInput.setText(getIntent().getStringExtra("video_title"));
            tagsInput.setText(getIntent().getStringExtra("video_tags"));
            String status = getIntent().getStringExtra("video_status");
            if (status != null) {
                for (int i = 0; i < STATUS_OPTIONS.length; i++) {
                    if (STATUS_OPTIONS[i].equals(status)) {
                        statusSpinner.setSelection(i);
                        break;
                    }
                }
            }
            String existingCover = getIntent().getStringExtra("video_cover");
            String existingVideo = getIntent().getStringExtra("video_url");
            if (existingCover != null && !existingCover.isEmpty()) {
                uploadedCoverUrl = existingCover;
                coverPreview.setVisibility(View.VISIBLE);
                Glide.with(this).load(existingCover).into(coverPreview);
            }
            if (existingVideo != null && !existingVideo.isEmpty()) {
                uploadedVideoUrl = existingVideo;
                videoInfoText.setVisibility(View.VISIBLE);
                videoInfoText.setText("已有视频（重新选择覆盖）");
            }
            // Load existing products
            loadExistingProducts();
        }
    }

    // ---- Load existing products for editing ----

    private void loadExistingProducts() {
        if (editingVideoId == null) return;
        new Thread(() -> {
            try {
                List<Map<String, Object>> products = new ApiService().getVideoProducts(editingVideoId);
                runOnUiThread(() -> {
                    for (Map<String, Object> p : products) {
                        String productId = (String) p.get("id");
                        String title = (String) p.get("title");
                        String coverUrl = (String) p.get("coverUrl");
                        double price = ((Number) p.get("price")).doubleValue();
                        double originalPrice = 0;
                        Object op = p.get("originalPrice");
                        if (op instanceof Number) originalPrice = ((Number) op).doubleValue();
                        int stock = p.get("stock") instanceof Number ? ((Number) p.get("stock")).intValue() : 9999;
                        long timestampMs = p.get("timestampMs") instanceof Number
                                ? ((Number) p.get("timestampMs")).longValue() : 0;

                        PendingProduct pp = new PendingProduct(title, coverUrl, price, stock, timestampMs);
                        pp.productId = productId;
                        pp.originalPrice = originalPrice;
                        pp.isExisting = true;
                        pendingProducts.add(pp);
                    }
                    productListAdapter.notifyDataSetChanged();
                    updateProductListVisibility();
                });
            } catch (Exception ignored) {}
        }).start();
    }

    // ---- File picking and upload ----

    private void onFilePicked(Uri uri, boolean isVideo) {
        try {
            ContentResolver resolver = getContentResolver();
            String mimeType = resolver.getType(uri);
            String displayName = getFileName(uri);
            if (displayName == null) {
                displayName = isVideo ? "video.mp4" : "cover.jpg";
            }

            File tempFile = new File(getCacheDir(), displayName);
            try (InputStream in = resolver.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                if (in == null) {
                    Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            if (isVideo) {
                videoFile = tempFile;
                videoInfoText.setVisibility(View.VISIBLE);
                videoInfoText.setText("已选择: " + displayName);
                uploadAndSetUrl(tempFile, mimeType != null ? mimeType : "video/mp4", true);
            } else {
                coverFile = tempFile;
                coverPreview.setVisibility(View.VISIBLE);
                Glide.with(this).load(tempFile).into(coverPreview);
                uploadAndSetUrl(tempFile, mimeType != null ? mimeType : "image/jpeg", false);
            }
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
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            productPreview.setVisibility(View.VISIBLE);
            Glide.with(this).load(tempFile).into(productPreview);

            // Upload product image
            productProgress.setVisibility(View.VISIBLE);
            productProgress.setIndeterminate(true);

            final String mime = mimeType != null ? mimeType : "image/jpeg";
            new Thread(() -> {
                try {
                    Map<String, Object> result = new ApiService().uploadFile(tempFile.getAbsolutePath(), mime);
                    String url = (String) result.get("url");
                    runOnUiThread(() -> {
                        uploadedProductImageUrl = url;
                        productProgress.setVisibility(View.GONE);
                        Toast.makeText(this, "商品图片上传成功", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        productProgress.setVisibility(View.GONE);
                        Toast.makeText(this, "上传失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadAndSetUrl(File file, String mimeType, boolean isVideo) {
        ProgressBar pb = isVideo ? videoProgress : coverProgress;
        if (pb != null) {
            pb.setVisibility(View.VISIBLE);
            pb.setIndeterminate(true);
        }

        new Thread(() -> {
            try {
                Map<String, Object> result = new ApiService().uploadFile(file.getAbsolutePath(), mimeType);
                String url = (String) result.get("url");
                runOnUiThread(() -> {
                    if (isVideo) {
                        uploadedVideoUrl = url;
                        if (pb != null) pb.setVisibility(View.GONE);
                        Toast.makeText(this, "视频上传成功", Toast.LENGTH_SHORT).show();
                    } else {
                        uploadedCoverUrl = url;
                        if (pb != null) pb.setVisibility(View.GONE);
                        Toast.makeText(this, "封面上传成功", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (pb != null) pb.setVisibility(View.GONE);
                    Toast.makeText(this, "上传失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ---- Product management ----

    private void addOrUpdateProduct() {
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

        double price;
        int stock;
        try {
            price = Double.parseDouble(priceStr);
            stock = Integer.parseInt(stockStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "价格或库存格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }
        if (stock < 0) {
            Toast.makeText(this, "库存不能为负数", Toast.LENGTH_SHORT).show();
            return;
        }

        double originalPrice = 0;
        String opStr = productOriginalPriceInput.getText().toString().trim();
        if (!opStr.isEmpty()) {
            try {
                originalPrice = Double.parseDouble(opStr);
            } catch (NumberFormatException ignored) {}
        }

        long timestampMs = 0;
        String tsStr = timestampInput.getText().toString().trim();
        if (!tsStr.isEmpty() && tsStr.contains(":")) {
            String[] parts = tsStr.split(":");
            try {
                long minutes = Long.parseLong(parts[0]);
                long seconds = Long.parseLong(parts[1]);
                timestampMs = (minutes * 60 + seconds) * 1000;
            } catch (NumberFormatException ignored) {}
        }

        if (editingProductId != null) {
            // Updating an existing product from the inline form
            for (PendingProduct pp : pendingProducts) {
                if (editingProductId.equals(pp.productId)) {
                    pp.title = name;
                    pp.price = price;
                    pp.originalPrice = originalPrice;
                    pp.stock = stock;
                    pp.timestampMs = timestampMs;
                    if (uploadedProductImageUrl != null && !uploadedProductImageUrl.isEmpty()) {
                        pp.coverUrl = uploadedProductImageUrl;
                    }
                    pp.modified = true;
                    productListAdapter.notifyDataSetChanged();
                    break;
                }
            }
            addProductBtn.setText("+ 添加到此视频");
            editingProductId = null;
        } else {
            // Adding a new product
            String coverUrl = uploadedProductImageUrl != null ? uploadedProductImageUrl : "";
            if (coverUrl.isEmpty()) {
                Toast.makeText(this, "请选择商品图片", Toast.LENGTH_SHORT).show();
                return;
            }
            PendingProduct pp = new PendingProduct(name, coverUrl, price, stock, timestampMs);
            pp.originalPrice = originalPrice;
            pendingProducts.add(pp);
            productListAdapter.notifyItemInserted(pendingProducts.size() - 1);
        }

        // Reset product form
        productNameInput.setText("");
        productPriceInput.setText("");
        productOriginalPriceInput.setText("");
        productStockInput.setText("");
        timestampInput.setText("");
        uploadedProductImageUrl = null;
        productPreview.setVisibility(View.GONE);
        productPreview.setImageDrawable(null);
        updateProductListVisibility();
    }

    private void editProduct(int position) {
        PendingProduct pp = pendingProducts.get(position);
        if (pp.isExisting) {
            // Edit via dialog for existing products
            showEditProductDialog(pp, position);
        } else {
            // Load into inline form for new products
            productNameInput.setText(pp.title);
            productPriceInput.setText(String.valueOf((int) pp.price));
            if (pp.originalPrice > 0) {
                productOriginalPriceInput.setText(String.valueOf((int) pp.originalPrice));
            }
            productStockInput.setText(String.valueOf(pp.stock));
            if (pp.timestampMs > 0) {
                long mins = pp.timestampMs / 60000;
                long secs = (pp.timestampMs / 1000) % 60;
                timestampInput.setText(String.format("%d:%02d", mins, secs));
            }
            uploadedProductImageUrl = pp.coverUrl;
            productPreview.setVisibility(View.VISIBLE);
            Glide.with(this).load(pp.coverUrl).into(productPreview);
            if (pp.productId != null) {
                editingProductId = pp.productId;
                addProductBtn.setText("更新商品");
            }
        }
    }

    private void showEditProductDialog(PendingProduct pp, int position) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_product, null);
        EditText nameEt = dialogView.findViewById(R.id.dialog_product_name);
        EditText priceEt = dialogView.findViewById(R.id.dialog_product_price);
        EditText originalPriceEt = dialogView.findViewById(R.id.dialog_product_original_price);
        EditText stockEt = dialogView.findViewById(R.id.dialog_product_stock);
        EditText timestampEt = dialogView.findViewById(R.id.dialog_product_timestamp);

        nameEt.setText(pp.title);
        priceEt.setText(String.valueOf((int) pp.price));
        if (pp.originalPrice > 0) originalPriceEt.setText(String.valueOf((int) pp.originalPrice));
        stockEt.setText(String.valueOf(pp.stock));
        if (pp.timestampMs > 0) {
            long mins = pp.timestampMs / 60000;
            long secs = (pp.timestampMs / 1000) % 60;
            timestampEt.setText(String.format("%d:%02d", mins, secs));
        }

        new AlertDialog.Builder(this)
                .setTitle("编辑商品")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = nameEt.getText().toString().trim();
                    String priceStr = priceEt.getText().toString().trim();
                    String stockStr = stockEt.getText().toString().trim();

                    if (name.isEmpty() || priceStr.isEmpty() || stockStr.isEmpty()) {
                        Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        pp.title = name;
                        pp.price = Double.parseDouble(priceStr);
                        pp.stock = Integer.parseInt(stockStr);
                        String opStr = originalPriceEt.getText().toString().trim();
                        if (!opStr.isEmpty()) pp.originalPrice = Double.parseDouble(opStr);

                        String tsStr = timestampEt.getText().toString().trim();
                        if (!tsStr.isEmpty() && tsStr.contains(":")) {
                            String[] parts = tsStr.split(":");
                            pp.timestampMs = (Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])) * 1000;
                        }
                        pp.modified = true;
                        productListAdapter.notifyItemChanged(position);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "价格或库存格式不正确", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void removeProduct(int position) {
        PendingProduct pp = pendingProducts.get(position);
        if (pp.isExisting && pp.productId != null) {
            pp.deleted = true;
            pendingProducts.remove(position);
            productListAdapter.notifyItemRemoved(position);
        } else {
            pendingProducts.remove(position);
            productListAdapter.notifyItemRemoved(position);
        }
        updateProductListVisibility();
    }

    private void updateProductListVisibility() {
        boolean empty = pendingProducts.isEmpty();
        productHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        productListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ---- Save ----

    private void save() {
        if (isSaving) return;

        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入视频标题", Toast.LENGTH_SHORT).show();
            return;
        }

        String videoUrl = uploadedVideoUrl;
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "请选择视频文件", Toast.LENGTH_SHORT).show();
            return;
        }

        String coverUrl = uploadedCoverUrl != null ? uploadedCoverUrl : "";
        String tagsStr = tagsInput.getText().toString().trim();
        List<String> tags = tagsStr.isEmpty() ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(tagsStr.split(",")));
        for (int i = 0; i < tags.size(); i++) {
            tags.set(i, tags.get(i).trim());
        }
        tags.removeIf(String::isEmpty);

        String status = STATUS_OPTIONS[statusSpinner.getSelectedItemPosition()];

        isSaving = true;
        saveBtn.setEnabled(false);
        saveBtn.setText("保存中...");

        new Thread(() -> {
            try {
                ApiService api = new ApiService();
                String videoId;

                // Step 1: Create or update video
                if (editingVideoId != null) {
                    api.updateVideo(editingVideoId, title, coverUrl, videoUrl, tags, status);
                    videoId = editingVideoId;
                } else {
                    Video created = api.createVideo(title, coverUrl, videoUrl, tags, status);
                    videoId = created.getId();
                }

                // Step 2: Handle each pending product
                for (PendingProduct pp : pendingProducts) {
                    try {
                        if (pp.isExisting && pp.productId != null) {
                            // Existing product — update if modified
                            if (pp.modified) {
                                api.updateProduct(pp.productId, pp.title, pp.coverUrl,
                                        pp.price, pp.originalPrice, pp.stock);
                            }
                        } else {
                            // New product — create and bind
                            Product product = api.createProduct(pp.title, pp.coverUrl,
                                    pp.price, pp.originalPrice, pp.stock);
                            api.bindVideoToProduct(product.getId(), videoId, pp.timestampMs);
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(VideoEditActivity.this,
                                "商品 " + pp.title + " 保存失败", Toast.LENGTH_SHORT).show());
                    }
                }

                runOnUiThread(() -> {
                    Toast.makeText(this,
                            editingVideoId != null ? "更新成功" : "创建成功",
                            Toast.LENGTH_SHORT).show();
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

    // ---- AI Generation ----

    private void generateAiTitle() {
        aiTitleBtn.setEnabled(false);
        aiTitleBtn.setText("生成中...");
        new Thread(() -> {
            try {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("productName", productNameInput.getText().toString().trim());
                ctx.put("productPrice", productPriceInput.getText().toString().trim());
                ctx.put("videoContent", titleInput.getText().toString().trim());
                String text = new ApiService().generateAiContent("video_title", ctx);
                runOnUiThread(() -> {
                    String[] titles = text.split("\n");
                    showTitlePicker(titles);
                    aiTitleBtn.setEnabled(true);
                    aiTitleBtn.setText("AI生成标题");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    aiTitleBtn.setEnabled(true);
                    aiTitleBtn.setText("AI生成标题");
                });
            }
        }).start();
    }

    private void generateAiSellingPoints() {
        String name = productNameInput.getText().toString().trim();
        String price = productPriceInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请先输入商品名称", Toast.LENGTH_SHORT).show();
            return;
        }
        aiSellingPointsBtn.setEnabled(false);
        aiSellingPointsBtn.setText("生成中...");
        new Thread(() -> {
            try {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("productName", name);
                ctx.put("productPrice", price);
                String text = new ApiService().generateAiContent("product_selling_points", ctx);
                runOnUiThread(() -> {
                    showSellingPointsDialog(text);
                    aiSellingPointsBtn.setEnabled(true);
                    aiSellingPointsBtn.setText("AI生成卖点");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    aiSellingPointsBtn.setEnabled(true);
                    aiSellingPointsBtn.setText("AI生成卖点");
                });
            }
        }).start();
    }

    private void showTitlePicker(String[] titles) {
        new AlertDialog.Builder(this)
                .setTitle("选择标题")
                .setItems(titles, (dialog, which) -> titleInput.setText(titles[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSellingPointsDialog(String text) {
        new AlertDialog.Builder(this)
                .setTitle("AI 卖点文案")
                .setMessage(text)
                .setPositiveButton("复制", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip =
                            android.content.ClipData.newPlainText("selling_points", text);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ---- Helpers ----

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

    // ---- Data classes ----

    private static class PendingProduct {
        String title;
        String coverUrl;
        double price;
        double originalPrice;
        int stock;
        long timestampMs;
        String productId;
        boolean isExisting;
        boolean modified;
        boolean deleted;

        PendingProduct(String title, String coverUrl, double price, int stock, long timestampMs) {
            this.title = title;
            this.coverUrl = coverUrl;
            this.price = price;
            this.stock = stock;
            this.timestampMs = timestampMs;
            this.originalPrice = 0;
        }
    }

    // ---- RecyclerView adapter ----

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
            if (pp.originalPrice > 0) {
                h.originalPriceText.setVisibility(View.VISIBLE);
                h.originalPriceText.setText("¥" + (int) pp.originalPrice);
            } else {
                h.originalPriceText.setVisibility(View.GONE);
            }
            if (h.timestampText != null) {
                long mins = pp.timestampMs / 60000;
                long secs = (pp.timestampMs / 1000) % 60;
                h.timestampText.setText(String.format("%d:%02d", mins, secs));
            }
            Glide.with(h.thumbView).load(pp.coverUrl).into(h.thumbView);

            h.itemView.setOnClickListener(v -> editProduct(h.getBindingAdapterPosition()));
            h.removeBtn.setOnClickListener(v -> removeProduct(h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return pendingProducts.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumbView;
            TextView nameText, priceText, originalPriceText, timestampText;
            Button removeBtn;

            VH(View v) {
                super(v);
                thumbView = v.findViewById(R.id.edit_product_thumb);
                nameText = v.findViewById(R.id.edit_product_name);
                priceText = v.findViewById(R.id.edit_product_price);
                originalPriceText = v.findViewById(R.id.edit_product_original_price);
                timestampText = v.findViewById(R.id.edit_product_timestamp);
                removeBtn = v.findViewById(R.id.edit_product_remove);
            }
        }
    }
}

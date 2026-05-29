package com.bytedance.streamshop.ui.feed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiClient;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.Product;
import com.bytedance.streamshop.ui.order.OrderConfirmActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductDetailBottomSheetFragment extends BottomSheetDialogFragment {
    private static final String ARG_PRODUCT = "product";
    private Product product;

    public static ProductDetailBottomSheetFragment newInstance(Product product) {
        ProductDetailBottomSheetFragment fragment = new ProductDetailBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PRODUCT, product);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            product = (Product) getArguments().getSerializable(ARG_PRODUCT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (product == null) return;

        ImageView imageView = view.findViewById(R.id.product_image);
        TextView priceText = view.findViewById(R.id.product_price);
        TextView originalPriceText = view.findViewById(R.id.product_original_price);
        TextView titleText = view.findViewById(R.id.product_title);
        TextView descriptionText = view.findViewById(R.id.product_description);
        TextView salesText = view.findViewById(R.id.product_sales);
        TextView stockText = view.findViewById(R.id.product_stock);
        MaterialButton buyNowBtn = view.findViewById(R.id.product_buy_now_btn);
        MaterialButton addCartBtn = view.findViewById(R.id.product_add_cart_btn);
        MaterialButton collectBtn = view.findViewById(R.id.product_collect_btn);

        Glide.with(this).load(product.getCoverUrl()).into(imageView);
        priceText.setText("¥" + (int) product.getPrice());
        originalPriceText.setText("¥" + (int) product.getOriginalPrice());
        titleText.setText(product.getTitle());
        descriptionText.setText(product.getDescription());
        salesText.setText("已售 " + product.getSalesCount() + " 件");
        stockText.setText("库存 " + product.getStock() + " 件");

        buyNowBtn.setOnClickListener(v -> {
            if (!ApiClient.getInstance().isAuthenticated()) {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            if (product.getStock() <= 0) {
                Toast.makeText(getContext(), "库存不足", Toast.LENGTH_SHORT).show();
                return;
            }
            onBuyNow();
        });

        addCartBtn.setOnClickListener(v -> {
            if (!ApiClient.getInstance().isAuthenticated()) {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            addCartBtn.setEnabled(false);
            new Thread(() -> {
                try {
                    new ApiService().addToCart(product.getId(), 1);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "已加入购物车", Toast.LENGTH_SHORT).show();
                            dismiss();
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            addCartBtn.setEnabled(true);
                            String msg = e.getMessage();
                            if (msg != null && msg.contains("401")) {
                                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
                            } else if (msg != null && msg.contains("404")) {
                                Toast.makeText(getContext(), "商品已下架", Toast.LENGTH_SHORT).show();
                            } else if (msg != null && msg.contains("stock")) {
                                Toast.makeText(getContext(), "库存不足", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "加入购物车失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }).start();
        });

        loadCollectStatus(collectBtn);

        collectBtn.setOnClickListener(v -> {
            if (!ApiClient.getInstance().isAuthenticated()) {
                Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            collectBtn.setEnabled(false);
            new Thread(() -> {
                try {
                    boolean collected = new ApiService().toggleCollection("product", product.getId());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            updateCollectButton(collectBtn, collected);
                            collectBtn.setEnabled(true);
                            Toast.makeText(getContext(), collected ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> collectBtn.setEnabled(true));
                    }
                }
            }).start();
        });
    }

    private void loadCollectStatus(MaterialButton btn) {
        new Thread(() -> {
            try {
                Map<String, Object> status = new ApiService().getCollectionStatus("product", product.getId());
                Boolean collected = (Boolean) status.get("collected");
                if (getActivity() != null && collected != null) {
                    getActivity().runOnUiThread(() -> updateCollectButton(btn, collected));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateCollectButton(MaterialButton btn, boolean collected) {
        if (collected) {
            btn.setText("已收藏");
            btn.setIconResource(R.drawable.ic_collect_filled);
            btn.setStrokeColorResource(R.color.collect_gold);
            btn.setTextColor(0xFFFFD700);
        } else {
            btn.setText("收藏");
            btn.setIconResource(R.drawable.ic_collect_outline);
            btn.setStrokeColorResource(R.color.collect_gold);
            btn.setTextColor(0xFF666666);
        }
    }

    private void onBuyNow() {
        Map<String, Object> productMap = new HashMap<>();
        productMap.put("id", product.getId());
        productMap.put("title", product.getTitle());
        productMap.put("price", product.getPrice());
        productMap.put("coverUrl", product.getCoverUrl());

        Map<String, Object> item = new HashMap<>();
        item.put("productId", product.getId());
        item.put("quantity", 1);
        item.put("product", productMap);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);

        Intent intent = new Intent(getActivity(), OrderConfirmActivity.class);
        intent.putExtra("items", (java.io.Serializable) items);
        startActivity(intent);
        dismiss();
    }
}

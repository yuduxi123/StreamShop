package com.bytedance.streamshop.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.streamshop.R;

import java.util.List;
import java.util.Map;

public class LiveFeedCardViewHolder extends RecyclerView.ViewHolder {
    private final ImageView coverView;
    private final TextView statusText;
    private final TextView onlineText;
    private final TextView enterButton;
    private final TextView anchorText;
    private final TextView titleText;
    private final TextView currentProductText;
    private final ViewGroup productContainer;

    private Map<String, Object> room;
    private OnLiveCardClickListener clickListener;

    public interface OnLiveCardClickListener {
        void onLiveCardClick(Map<String, Object> room);
        void onProductReviewClick(String productId);
    }

    public LiveFeedCardViewHolder(@NonNull View itemView) {
        super(itemView);
        coverView = itemView.findViewById(R.id.feed_live_cover);
        statusText = itemView.findViewById(R.id.feed_live_status);
        onlineText = itemView.findViewById(R.id.feed_live_online);
        enterButton = itemView.findViewById(R.id.feed_live_enter_btn);
        anchorText = itemView.findViewById(R.id.feed_live_anchor);
        titleText = itemView.findViewById(R.id.feed_live_title);
        currentProductText = itemView.findViewById(R.id.feed_live_current_product);
        productContainer = itemView.findViewById(R.id.feed_live_products);
    }

    public void bind(Map<String, Object> room, OnLiveCardClickListener clickListener) {
        this.room = room;
        this.clickListener = clickListener;

        String coverUrl = stringValue(room.get("coverUrl"));
        if (!coverUrl.isEmpty()) {
            Glide.with(itemView.getContext()).load(coverUrl).centerCrop().into(coverView);
        }

        String status = stringValue(room.get("status"));
        boolean isLive = "live".equals(status);
        statusText.setText(isLive ? "直播中" : "未开播");
        statusText.setTextColor(isLive ? 0xFFFF3B30 : 0xFFFFFFFF);

        int onlineCount = intValue(room.get("onlineCount"));
        onlineText.setText(formatCount(onlineCount) + " 人在线");

        Map<String, Object> anchor = mapValue(room.get("anchor"));
        String anchorName = anchor != null ? stringValue(anchor.get("username")) : "";
        anchorText.setText(anchorName.isEmpty() ? "@主播" : "@" + anchorName);

        String title = stringValue(room.get("title"));
        titleText.setText(title.isEmpty() ? "直播间" : title);

        bindCurrentProduct();
        bindProducts();

        View.OnClickListener listener = v -> {
            if (clickListener != null && room != null) {
                clickListener.onLiveCardClick(room);
            }
        };
        itemView.setOnClickListener(listener);
        enterButton.setOnClickListener(listener);
    }

    private void bindCurrentProduct() {
        Map<String, Object> currentProduct = mapValue(room.get("currentProduct"));
        String title = currentProduct != null ? stringValue(currentProduct.get("title")) : "";
        if (title.isEmpty()) {
            currentProductText.setVisibility(View.GONE);
        } else {
            currentProductText.setVisibility(View.VISIBLE);
            currentProductText.setText("正在讲解：" + title);
        }
    }

    private void bindProducts() {
        productContainer.removeAllViews();
        List<Object> products = listValue(room.get("products"));
        if (products == null || products.isEmpty()) {
            productContainer.setVisibility(View.GONE);
            return;
        }

        productContainer.setVisibility(View.VISIBLE);
        int count = Math.min(products.size(), 3);
        for (int i = 0; i < count; i++) {
            Map<String, Object> product = mapValue(products.get(i));
            if (product == null) continue;

            View card = LayoutInflater.from(itemView.getContext())
                    .inflate(R.layout.item_live_product_card, productContainer, false);
            ImageView thumb = card.findViewById(R.id.live_product_thumb);
            TextView price = card.findViewById(R.id.live_product_price);
            TextView reviewsBtn = card.findViewById(R.id.live_product_reviews_btn);

            String coverUrl = stringValue(product.get("coverUrl"));
            if (!coverUrl.isEmpty()) {
                Glide.with(card).load(coverUrl).centerCrop().into(thumb);
            }
            price.setText("¥" + intValue(product.get("price")));

            String productId = stringValue(product.get("id"));
            if (reviewsBtn != null && !productId.isEmpty()) {
                reviewsBtn.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onProductReviewClick(productId);
                    }
                });
            }

            productContainer.addView(card);
        }
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return value != null ? (int) Double.parseDouble(String.valueOf(value)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listValue(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    private static String formatCount(int count) {
        if (count >= 10000) return count / 10000 + "w";
        if (count >= 1000) return count / 1000 + "k";
        return String.valueOf(count);
    }
}

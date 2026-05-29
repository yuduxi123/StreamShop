package com.bytedance.streamshop.ui.order;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderDetailActivity extends AppCompatActivity {
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        apiService = new ApiService();

        String orderId = getIntent().getStringExtra("order_id");
        findViewById(R.id.detail_back).setOnClickListener(v -> finish());

        loadOrder(orderId);
    }

    private void loadOrder(String orderId) {
        new Thread(() -> {
            try {
                Map<String, Object> order = apiService.getOrderDetail(orderId);
                runOnUiThread(() -> bindOrder(order));
            } catch (Exception ignored) {}
        }).start();
    }

    private void bindOrder(Map<String, Object> order) {
        String status = (String) order.get("status");
        double finalAmount = ((Number) order.get("finalAmount")).doubleValue();
        double totalAmount = ((Number) order.get("totalAmount")).doubleValue();
        String createdAt = (String) order.get("createdAt");
        String address = (String) order.get("shippingAddress");

        TextView statusText = findViewById(R.id.detail_status);
        TextView amountText = findViewById(R.id.detail_amount);
        TextView orderIdText = findViewById(R.id.detail_order_id);
        TextView timeText = findViewById(R.id.detail_create_time);
        TextView addressText = findViewById(R.id.detail_address);
        ViewGroup itemsContainer = findViewById(R.id.detail_items);

        statusText.setText(getStatusText(status));

        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.CHINA);
        fmt.setMinimumFractionDigits(2);
        amountText.setText("¥" + fmt.format(finalAmount));
        orderIdText.setText("订单号：" + order.get("id"));
        timeText.setText("创建时间：" + (createdAt != null ? createdAt.substring(0, 19).replace("T", " ") : ""));
        addressText.setText("收货地址：" + (address != null ? address : ""));

        // Render items
        List<Map<String, Object>> items = (List<Map<String, Object>>) order.get("items");
        if (items != null) {
            for (Map<String, Object> item : items) {
                Map<String, Object> product = (Map<String, Object>) item.get("product");
                android.view.View row = getLayoutInflater().inflate(R.layout.item_order_confirm_product, itemsContainer, false);
                android.widget.ImageView image = row.findViewById(R.id.order_confirm_item_image);
                android.widget.TextView title = row.findViewById(R.id.order_confirm_item_title);
                android.widget.TextView price = row.findViewById(R.id.order_confirm_item_price);
                android.widget.TextView qty = row.findViewById(R.id.order_confirm_item_qty);

                if (product != null) {
                    com.bumptech.glide.Glide.with(this).load((String) product.get("coverUrl")).into(image);
                    title.setText((String) product.get("title"));
                    price.setText("¥" + (int) ((Number) product.get("price")).doubleValue());
                }
                qty.setText("x" + item.get("quantity"));
                itemsContainer.addView(row);
            }
        }
    }

    private String getStatusText(String status) {
        switch (status) {
            case "pending": return "待支付";
            case "paid": return "待发货";
            case "shipped": return "已发货";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            default: return status;
        }
    }
}

package com.bytedance.streamshop.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderConfirmActivity extends AppCompatActivity {
    private List<Map<String, Object>> items = new ArrayList<>();
    private ApiService apiService;
    private EditText addressInput;
    private ViewGroup itemsContainer;
    private TextView subtotalText, discountText, totalText, finalAmountText;
    private MaterialButton submitBtn;
    private double totalAmount = 0;
    private String couponId;
    private double couponDiscount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_confirm);
        apiService = new ApiService();

        items = (List<Map<String, Object>>) getIntent().getSerializableExtra("items");
        if (items == null) items = new ArrayList<>();

        couponId = getIntent().getStringExtra("couponId");
        if (couponId != null) {
            String couponType = getIntent().getStringExtra("couponType");
            double couponValue = getIntent().getDoubleExtra("couponValue", 0);
            calculateDiscount(couponType, couponValue);
        }

        initViews();
        calculateTotal();
        renderItems();
    }

    private void calculateDiscount(String type, double value) {
        double tempTotal = 0;
        for (Map<String, Object> item : items) {
            int qty = ((Number) item.get("quantity")).intValue();
            Map<String, Object> product = (Map<String, Object>) item.get("product");
            if (product != null && product.get("price") != null) {
                tempTotal += ((Number) product.get("price")).doubleValue() * qty;
            }
        }
        if ("fixed".equals(type)) {
            couponDiscount = Math.min(value, tempTotal);
        } else {
            couponDiscount = Math.round(tempTotal * value / 100.0);
        }
    }

    private void initViews() {
        findViewById(R.id.confirm_back).setOnClickListener(v -> finish());
        addressInput = findViewById(R.id.confirm_address);
        itemsContainer = findViewById(R.id.confirm_items_container);
        subtotalText = findViewById(R.id.confirm_subtotal);
        discountText = findViewById(R.id.confirm_discount);
        totalText = findViewById(R.id.confirm_total);
        finalAmountText = findViewById(R.id.confirm_final_amount);
        submitBtn = findViewById(R.id.confirm_submit_btn);

        submitBtn.setOnClickListener(v -> submitOrder());
    }

    private void calculateTotal() {
        totalAmount = 0;
        for (Map<String, Object> item : items) {
            int qty = ((Number) item.get("quantity")).intValue();
            Map<String, Object> product = (Map<String, Object>) item.get("product");
            if (product != null && product.get("price") != null) {
                totalAmount += ((Number) product.get("price")).doubleValue() * qty;
            }
        }
        updateAmounts(couponDiscount);
    }

    private void updateAmounts(double discount) {
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.CHINA);
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        double finalAmount = Math.max(0, totalAmount - discount);
        subtotalText.setText("¥" + fmt.format(totalAmount));
        discountText.setText("-¥" + fmt.format(discount));
        totalText.setText("¥" + fmt.format(finalAmount));
        finalAmountText.setText("¥" + fmt.format(finalAmount));
    }

    private void renderItems() {
        itemsContainer.removeAllViews();
        for (Map<String, Object> item : items) {
            Map<String, Object> product = (Map<String, Object>) item.get("product");
            View row = getLayoutInflater().inflate(R.layout.item_order_confirm_product, itemsContainer, false);
            ImageView image = row.findViewById(R.id.order_confirm_item_image);
            TextView title = row.findViewById(R.id.order_confirm_item_title);
            TextView price = row.findViewById(R.id.order_confirm_item_price);
            TextView qty = row.findViewById(R.id.order_confirm_item_qty);

            if (product != null) {
                Glide.with(this).load((String) product.get("coverUrl")).into(image);
                title.setText((String) product.get("title"));
                double p = ((Number) product.get("price")).doubleValue();
                price.setText("¥" + (int) p);
            }
            qty.setText("x" + item.get("quantity"));
            itemsContainer.addView(row);
        }
    }

    private void submitOrder() {
        String address = addressInput.getText().toString().trim();
        if (address.isEmpty()) {
            addressInput.setError("请输入收货地址");
            return;
        }

        submitBtn.setEnabled(false);
        submitBtn.setText("提交中...");

        new Thread(() -> {
            try {
                Map<String, Object> order = apiService.createOrder(address, items, couponId);
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, PaymentActivity.class);
                    intent.putExtra("order_id", (String) order.get("id"));
                    double amount = ((Number) order.get("finalAmount")).doubleValue();
                    intent.putExtra("amount", amount);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    submitBtn.setEnabled(true);
                    submitBtn.setText("提交订单");
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("No items")) {
                        Toast.makeText(this, "购物车为空，请先添加商品", Toast.LENGTH_SHORT).show();
                    } else if (msg != null && msg.contains("stock")) {
                        Toast.makeText(this, "库存不足", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "提交失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
}

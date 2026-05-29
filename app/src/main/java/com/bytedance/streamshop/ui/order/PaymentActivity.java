package com.bytedance.streamshop.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {
    private String orderId;
    private double amount;
    private ApiService apiService;
    private TextView iconView, statusView, amountView;
    private MaterialButton backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        orderId = getIntent().getStringExtra("order_id");
        amount = getIntent().getDoubleExtra("amount", 0);
        apiService = new ApiService();

        iconView = findViewById(R.id.payment_icon);
        statusView = findViewById(R.id.payment_status);
        amountView = findViewById(R.id.payment_amount);
        backBtn = findViewById(R.id.payment_back_btn);

        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.CHINA);
        fmt.setMinimumFractionDigits(2);
        amountView.setText("¥" + fmt.format(amount));

        backBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, OrderListActivity.class));
            finish();
        });

        processPayment();
    }

    private void processPayment() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            new Thread(() -> {
                try {
                    Map<String, Object> result = apiService.payOrder(orderId);
                    boolean success = (boolean) result.get("success");
                    runOnUiThread(() -> showResult(success));
                } catch (Exception e) {
                    runOnUiThread(() -> showResult(false));
                }
            }).start();
        }, 1500); // Simulate processing delay
    }

    private void showResult(boolean success) {
        findViewById(R.id.payment_progress).setVisibility(android.view.View.GONE);
        backBtn.setVisibility(android.view.View.VISIBLE);

        if (success) {
            iconView.setText("✅");
            iconView.setBackgroundResource(R.drawable.bg_circle);
            statusView.setText("支付成功");
        } else {
            iconView.setText("❌");
            iconView.setBackgroundResource(R.drawable.bg_circle);
            statusView.setText("支付失败");
        }
    }
}

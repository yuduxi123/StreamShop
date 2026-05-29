package com.bytedance.streamshop.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderListActivity extends AppCompatActivity {
    private TabLayout tabs;
    private RecyclerView recyclerView;
    private OrderAdapter adapter;
    private ApiService apiService;
    private List<Map<String, Object>> orders = new ArrayList<>();
    private String currentStatus = "all";
    private final String[] statusLabels = {"全部", "待支付", "已支付", "已完成"};
    private final String[] statusValues = {"all", "pending", "paid", "completed"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_list);
        apiService = new ApiService();

        findViewById(R.id.order_list_back).setOnClickListener(v -> finish());
        tabs = findViewById(R.id.order_tabs);
        recyclerView = findViewById(R.id.order_list);

        for (String label : statusLabels) {
            tabs.addTab(tabs.newTab().setText(label));
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrderAdapter();
        recyclerView.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentStatus = statusValues[tab.getPosition()];
                loadOrders();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadOrders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrders();
    }

    private void loadOrders() {
        new Thread(() -> {
            try {
                var response = apiService.getOrders(currentStatus, 1, 50);
                orders = response.getData();
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            } catch (Exception ignored) {}
        }).start();
    }

    private class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            Map<String, Object> order = orders.get(position);
            String orderId = (String) order.get("id");
            String status = (String) order.get("status");

            h.orderId.setText("订单号: " + orderId.substring(0, 8) + "...");
            h.statusText.setText(getStatusText(status));

            // Products
            h.productsContainer.removeAllViews();
            List<Map<String, Object>> items = (List<Map<String, Object>>) order.get("items");
            if (items != null) {
                for (Map<String, Object> item : items) {
                    Map<String, Object> product = (Map<String, Object>) item.get("product");
                    View row = LayoutInflater.from(OrderListActivity.this)
                            .inflate(R.layout.item_order_product, h.productsContainer, false);
                    TextView name = row.findViewById(R.id.order_product_name);
                    TextView spec = row.findViewById(R.id.order_product_spec);
                    if (product != null) {
                        name.setText((String) product.get("title"));
                    }
                    spec.setText("x" + item.get("quantity"));
                    h.productsContainer.addView(row);
                }
            }

            // Amount
            double amount = ((Number) order.get("finalAmount")).doubleValue();
            NumberFormat fmt = NumberFormat.getNumberInstance(Locale.CHINA);
            fmt.setMinimumFractionDigits(2);
            h.amountText.setText("合计：¥" + fmt.format(amount));

            // Actions
            h.actionsContainer.removeAllViews();
            if ("pending".equals(status)) {
                MaterialButton cancelBtn = new MaterialButton(OrderListActivity.this);
                cancelBtn.setText("取消订单");
                cancelBtn.setTextSize(12f);
                cancelBtn.setCornerRadius(16);
                cancelBtn.setStrokeColorResource(android.R.color.darker_gray);
                cancelBtn.setStrokeWidth(1);
                h.actionsContainer.addView(cancelBtn);

                MaterialButton payBtn = new MaterialButton(OrderListActivity.this);
                payBtn.setText("去支付");
                payBtn.setTextSize(12f);
                payBtn.setCornerRadius(16);
                payBtn.setBackgroundTintList(getColorStateList(android.R.color.holo_orange_dark));
                h.actionsContainer.addView(payBtn);

                payBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(OrderListActivity.this, PaymentActivity.class);
                    intent.putExtra("order_id", orderId);
                    intent.putExtra("amount", amount);
                    startActivity(intent);
                });

                cancelBtn.setOnClickListener(v -> {
                    new Thread(() -> {
                        try { apiService.cancelOrder(orderId); runOnUiThread(() -> loadOrders()); }
                        catch (Exception ignored) {}
                    }).start();
                });
            }

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(OrderListActivity.this, OrderDetailActivity.class);
                intent.putExtra("order_id", orderId);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return orders.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView orderId, statusText, amountText;
            ViewGroup productsContainer, actionsContainer;

            ViewHolder(View v) {
                super(v);
                orderId = v.findViewById(R.id.order_item_id);
                statusText = v.findViewById(R.id.order_item_status);
                amountText = v.findViewById(R.id.order_item_amount);
                productsContainer = v.findViewById(R.id.order_item_products);
                actionsContainer = v.findViewById(R.id.order_item_actions);
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
}

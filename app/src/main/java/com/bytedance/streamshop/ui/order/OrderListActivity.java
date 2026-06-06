package com.bytedance.streamshop.ui.order;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
        private int openPosition = -1;
        private float buttonPanelWidth;

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order, parent, false);
            buttonPanelWidth = 120 * parent.getContext().getResources().getDisplayMetrics().density;
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            Map<String, Object> order = orders.get(position);
            String orderId = (String) order.get("id");
            String status = (String) order.get("status");

            h.orderId.setText("订单号: " + orderId.substring(0, 8) + "...");
            h.statusText.setText(getStatusText(status));

            // Store order data on views for swipe actions
            h.foreground.setTag(order);

            // Products
            h.productsContainer.removeAllViews();
            List<Map<String, Object>> items = (List<Map<String, Object>>) order.get("items");
            String productId = null;
            if (items != null) {
                for (Map<String, Object> item : items) {
                    Map<String, Object> product = (Map<String, Object>) item.get("product");
                    View row = LayoutInflater.from(OrderListActivity.this)
                            .inflate(R.layout.item_order_product, h.productsContainer, false);
                    TextView name = row.findViewById(R.id.order_product_name);
                    TextView spec = row.findViewById(R.id.order_product_spec);
                    if (product != null) {
                        name.setText((String) product.get("title"));
                        productId = (String) product.get("id");
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

            final String fProductId = productId;

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

            // Reset swipe position on rebind
            h.foreground.setTranslationX(0f);

            // --- Swipe configuration based on status ---
            String finalProductId = fProductId;

            if ("paid".equals(status) || "shipped".equals(status)) {
                // Paid/shipped: show 已收货 + 催单
                h.actionBtn1.setText("已收货");
                h.actionBtn1.setBackgroundColor(0xFF4CAF50);
                h.actionBtn2.setText("催单");
                h.actionBtn2.setBackgroundColor(0xFFFF9500);
                h.actionBtn2.setVisibility(View.VISIBLE);
                h.actionBtn1.setOnClickListener(v -> {
                    closeOpenItem();
                    confirmReceive(orderId);
                });
                h.actionBtn2.setOnClickListener(v -> {
                    closeOpenItem();
                    remindOrder(orderId);
                });
            } else if ("completed".equals(status)) {
                // Completed: show 评价 only
                h.actionBtn1.setText("评价");
                h.actionBtn1.setBackgroundColor(0xFFFF9500);
                h.actionBtn2.setVisibility(View.GONE);
                h.actionBtn1.setOnClickListener(v -> {
                    closeOpenItem();
                    showReviewSheet(orderId, finalProductId);
                });
            }

            // Only enable swipe for paid/shipped/completed
            boolean swipeable = "paid".equals(status) || "shipped".equals(status) || "completed".equals(status);

            h.foreground.setOnTouchListener((v, event) -> {
                if (!swipeable) return false;

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (openPosition != -1 && openPosition != position) {
                        closeOpenItem();
                    }
                    h.swipeStartX = event.getRawX();
                    h.swipeStartTrans = v.getTranslationX();
                    return true;
                }

                float dx = event.getRawX() - h.swipeStartX;
                float newTrans = h.swipeStartTrans + dx;

                if (newTrans > 0) newTrans = 0;
                float maxSwipe = "completed".equals(status) ? -buttonPanelWidth / 2 : -buttonPanelWidth;
                if (newTrans < maxSwipe) newTrans = maxSwipe;

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(dx) > 5) {
                        recyclerView.requestDisallowInterceptTouchEvent(true);
                    }
                    v.setTranslationX(newTrans);
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    recyclerView.requestDisallowInterceptTouchEvent(false);
                    float currentTrans = v.getTranslationX();
                    float threshold = maxSwipe * 0.4f;

                    if (Math.abs(currentTrans - h.swipeStartTrans) < 10) {
                        // It was a tap, not a swipe
                        v.animate().translationX(0).setDuration(150).start();
                        openPosition = -1;
                        openOrderDetail(orderId);
                    } else if (currentTrans < threshold) {
                        v.animate().translationX(maxSwipe).setDuration(150).start();
                        openPosition = position;
                    } else {
                        v.animate().translationX(0).setDuration(150).start();
                        openPosition = -1;
                    }
                    return true;
                }
                return false;
            });

            // Fallback click on whole item (when not swipeable)
            if (!swipeable) {
                h.foreground.setOnClickListener(v -> openOrderDetail(orderId));
            }
        }

        private void closeOpenItem() {
            if (openPosition != -1) {
                int old = openPosition;
                openPosition = -1;
                notifyItemChanged(old);
            }
        }

        private void openOrderDetail(String orderId) {
            Intent intent = new Intent(OrderListActivity.this, OrderDetailActivity.class);
            intent.putExtra("order_id", orderId);
            startActivity(intent);
        }

        private void confirmReceive(String orderId) {
            new AlertDialog.Builder(OrderListActivity.this)
                    .setTitle("确认收货")
                    .setMessage("确定已收到商品？确认后订单将变为已完成状态。")
                    .setPositiveButton("确定", (d, w) -> {
                        new Thread(() -> {
                            try {
                                apiService.receiveOrder(orderId);
                                runOnUiThread(() -> {
                                    Toast.makeText(OrderListActivity.this, "已确认收货", Toast.LENGTH_SHORT).show();
                                    loadOrders();
                                });
                            } catch (Exception e) {
                                runOnUiThread(() ->
                                        Toast.makeText(OrderListActivity.this, "操作失败", Toast.LENGTH_SHORT).show());
                            }
                        }).start();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }

        private void remindOrder(String orderId) {
            new AlertDialog.Builder(OrderListActivity.this)
                    .setTitle("催单")
                    .setMessage("将向商家发送催单提醒，确定要催单吗？")
                    .setPositiveButton("确定", (d, w) -> {
                        new Thread(() -> {
                            try {
                                apiService.remindOrder(orderId);
                                runOnUiThread(() ->
                                        Toast.makeText(OrderListActivity.this, "已发送催单提醒", Toast.LENGTH_SHORT).show());
                            } catch (Exception e) {
                                runOnUiThread(() ->
                                        Toast.makeText(OrderListActivity.this, "催单失败", Toast.LENGTH_SHORT).show());
                            }
                        }).start();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }

        private void showReviewSheet(String orderId, String productId) {
            if (productId == null) {
                Toast.makeText(OrderListActivity.this, "无法获取商品信息", Toast.LENGTH_SHORT).show();
                return;
            }
            ReviewBottomSheetFragment sheet = ReviewBottomSheetFragment.newInstance(productId, orderId);
            sheet.show(getSupportFragmentManager(), "review_sheet");
        }

        @Override
        public int getItemCount() { return orders.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView orderId, statusText, amountText;
            ViewGroup productsContainer, actionsContainer;
            View foreground;
            TextView actionBtn1, actionBtn2;
            float swipeStartX, swipeStartTrans;

            ViewHolder(View v) {
                super(v);
                foreground = v.findViewById(R.id.order_foreground);
                actionBtn1 = v.findViewById(R.id.order_action_btn1);
                actionBtn2 = v.findViewById(R.id.order_action_btn2);
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

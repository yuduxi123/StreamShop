package com.bytedance.streamshop.ui.cart;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CartFragment extends Fragment {
    private CartViewModel viewModel;
    private RecyclerView recyclerView;
    private CartAdapter adapter;
    private View emptyView;
    private CheckBox selectAllCheck;
    private TextView totalText;
    private MaterialButton checkoutBtn;
    private TextView editBtn;
    private View bottomBar;
    private boolean isManageMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupViewModel();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadCart();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.cart_list);
        emptyView = view.findViewById(R.id.cart_empty);
        selectAllCheck = view.findViewById(R.id.cart_select_all);
        totalText = view.findViewById(R.id.cart_total);
        checkoutBtn = view.findViewById(R.id.cart_checkout_btn);
        editBtn = view.findViewById(R.id.cart_edit_btn);
        bottomBar = view.findViewById(R.id.cart_bottom_bar);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CartAdapter();
        recyclerView.setAdapter(adapter);

        selectAllCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                viewModel.toggleSelectAll();
            }
        });

        checkoutBtn.setOnClickListener(v -> {
            if (isManageMode) {
                onDeleteSelected();
            } else {
                onCheckout();
            }
        });

        editBtn.setOnClickListener(v -> toggleManageMode());
    }

    private void toggleManageMode() {
        isManageMode = !isManageMode;
        editBtn.setText(isManageMode ? "完成" : "管理");

        if (isManageMode) {
            totalText.setVisibility(View.GONE);
            checkoutBtn.setText("删除选中");
            checkoutBtn.setBackgroundTintList(ColorStateList.valueOf(0xFF999999));
        } else {
            totalText.setVisibility(View.VISIBLE);
            checkoutBtn.setText("结算 (" + viewModel.getSelectedItems().size() + ")");
            checkoutBtn.setBackgroundTintList(ColorStateList.valueOf(0xFFFF3B30));
        }

        adapter.notifyDataSetChanged();
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(CartViewModel.class);

        viewModel.getCartItems().observe(getViewLifecycleOwner(), items -> {
            adapter.notifyDataSetChanged();
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            bottomBar.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            if (items.isEmpty() && isManageMode) {
                toggleManageMode();
            }
        });

        viewModel.getTotalPrice().observe(getViewLifecycleOwner(), total -> {
            NumberFormat fmt = NumberFormat.getNumberInstance(Locale.CHINA);
            fmt.setMinimumFractionDigits(2);
            fmt.setMaximumFractionDigits(2);
            totalText.setText("¥" + fmt.format(total));
            if (!isManageMode) {
                checkoutBtn.setText("结算 (" + viewModel.getSelectedItems().size() + ")");
            }
        });

        viewModel.getAllSelected().observe(getViewLifecycleOwner(), selected -> {
            selectAllCheck.setOnCheckedChangeListener(null);
            selectAllCheck.setChecked(selected);
            selectAllCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) viewModel.toggleSelectAll();
            });
        });
    }

    private void onCheckout() {
        List<Map<String, Object>> selected = viewModel.getSelectedItems();
        if (selected.isEmpty()) return;

        Intent intent = new Intent(getActivity(), com.bytedance.streamshop.ui.order.OrderConfirmActivity.class);
        intent.putExtra("items", new java.util.ArrayList<>(selected));
        startActivity(intent);
    }

    private void onDeleteSelected() {
        List<Map<String, Object>> selected = viewModel.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(getContext(), "请选择要删除的商品", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Map<String, Object> item : selected) {
            viewModel.deleteItemById((String) item.get("id"));
        }
        Toast.makeText(getContext(), "已删除 " + selected.size() + " 件商品", Toast.LENGTH_SHORT).show();
    }

    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(getContext()).inflate(R.layout.item_cart, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            Map<String, Object> item = viewModel.getCartItems().getValue().get(position);
            Map<String, Object> product = (Map<String, Object>) item.get("product");

            h.checkBox.setChecked((boolean) item.getOrDefault("selected", false));
            h.checkBox.setOnClickListener(v -> viewModel.toggleItemSelection(position));

            if (product != null) {
                h.title.setText((String) product.getOrDefault("title", ""));
                double price = ((Number) product.getOrDefault("price", 0)).doubleValue();
                h.price.setText("¥" + (int) price);
                Glide.with(h.itemView).load((String) product.get("coverUrl")).into(h.image);
            }

            int qty = ((Number) item.get("quantity")).intValue();
            h.quantityText.setText(String.valueOf(qty));

            if (isManageMode) {
                h.qtyGroup.setVisibility(View.GONE);
                h.deleteBtn.setVisibility(View.VISIBLE);
                h.deleteBtn.setOnClickListener(v -> {
                    viewModel.deleteItem(position);
                    Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                });
                h.minusBtn.setOnClickListener(null);
                h.plusBtn.setOnClickListener(null);
            } else {
                h.qtyGroup.setVisibility(View.VISIBLE);
                h.deleteBtn.setVisibility(View.GONE);
                h.minusBtn.setOnClickListener(v -> viewModel.updateQuantity(position, -1));
                h.plusBtn.setOnClickListener(v -> viewModel.updateQuantity(position, 1));
            }

            h.itemView.setOnLongClickListener(null);
        }

        @Override
        public int getItemCount() {
            List<Map<String, Object>> items = viewModel.getCartItems().getValue();
            return items != null ? items.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            ImageView image;
            TextView title, price, quantityText;
            ImageButton minusBtn, plusBtn, deleteBtn;
            View qtyGroup;

            ViewHolder(View v) {
                super(v);
                checkBox = v.findViewById(R.id.cart_item_check);
                image = v.findViewById(R.id.cart_item_image);
                title = v.findViewById(R.id.cart_item_title);
                price = v.findViewById(R.id.cart_item_price);
                quantityText = v.findViewById(R.id.cart_item_quantity);
                minusBtn = v.findViewById(R.id.cart_item_minus);
                plusBtn = v.findViewById(R.id.cart_item_plus);
                deleteBtn = v.findViewById(R.id.cart_item_delete);
                qtyGroup = v.findViewById(R.id.cart_item_qty_group);
            }
        }
    }
}

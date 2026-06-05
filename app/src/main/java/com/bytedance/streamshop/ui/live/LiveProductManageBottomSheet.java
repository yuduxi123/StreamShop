package com.bytedance.streamshop.ui.live;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LiveProductManageBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ROOM_ID = "room_id";
    private static final String ARG_PRODUCTS_JSON = "products_json";

    private String roomId;
    private List<Map<String, Object>> products;
    private RecyclerView productList;
    private TextView emptyText;
    private ProductManageAdapter adapter;
    private ApiService apiService;
    private NumberFormat priceFmt;
    private OnProductsChangedListener listener;

    public interface OnProductsChangedListener {
        void onProductsChanged();
    }

    public static LiveProductManageBottomSheet newInstance(String roomId,
                                                           List<Map<String, Object>> products) {
        LiveProductManageBottomSheet fragment = new LiveProductManageBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        try {
            JSONArray arr = new JSONArray();
            for (Map<String, Object> p : products) {
                JSONObject obj = new JSONObject();
                for (Map.Entry<String, Object> e : p.entrySet()) {
                    obj.put(e.getKey(), e.getValue());
                }
                arr.put(obj);
            }
            args.putString(ARG_PRODUCTS_JSON, arr.toString());
        } catch (Exception ignored) {}
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnProductsChangedListener(OnProductsChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        products = new ArrayList<>();
        priceFmt = NumberFormat.getNumberInstance(Locale.CHINA);
        priceFmt.setMinimumFractionDigits(0);
        apiService = new ApiService();

        if (getArguments() != null) {
            roomId = getArguments().getString(ARG_ROOM_ID);
            try {
                JSONArray arr = new JSONArray(getArguments().getString(ARG_PRODUCTS_JSON, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Map<String, Object> map = new java.util.HashMap<>();
                    for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                        String key = it.next();
                        map.put(key, obj.get(key));
                    }
                    products.add(map);
                }
            } catch (Exception ignored) {}
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_product_manage_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        productList = view.findViewById(R.id.manage_product_list);
        emptyText = view.findViewById(R.id.manage_product_empty);
        TextView doneBtn = view.findViewById(R.id.manage_product_done);
        TextView addBtn = view.findViewById(R.id.manage_add_product_btn);

        productList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ProductManageAdapter();
        productList.setAdapter(adapter);

        doneBtn.setOnClickListener(v -> dismiss());

        addBtn.setOnClickListener(v -> showAddProductDialog());

        updateUI();
    }

    private void updateUI() {
        if (products.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            productList.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            productList.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private void removeProduct(int position) {
        Map<String, Object> product = products.get(position);
        String productId = (String) product.get("id");
        if (productId == null) return;

        new Thread(() -> {
            try {
                boolean ok = apiService.unbindProductFromLiveRoom(roomId, productId);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (ok) {
                            products.remove(position);
                            adapter.notifyItemRemoved(position);
                            updateUI();
                            notifyListener();
                        } else {
                            Toast.makeText(getContext(), "移除失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "网络错误", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void moveUp(int position) {
        if (position <= 0) return;
        Map<String, Object> item = products.remove(position);
        products.add(position - 1, item);
        adapter.notifyItemMoved(position, position - 1);
        syncOrder();
    }

    private void moveDown(int position) {
        if (position >= products.size() - 1) return;
        Map<String, Object> item = products.remove(position);
        products.add(position + 1, item);
        adapter.notifyItemMoved(position, position + 1);
        syncOrder();
    }

    private void syncOrder() {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> p : products) {
            Object id = p.get("id");
            if (id != null) ids.add((String) id);
        }
        new Thread(() -> {
            try {
                apiService.reorderLiveRoomProducts(roomId, ids);
                notifyListener();
            } catch (Exception ignored) {}
        }).start();
    }

    private void showAddProductDialog() {
        new Thread(() -> {
            try {
                // Load all products and filter by current user's ownership
                com.bytedance.streamshop.data.remote.ApiClient client =
                        com.bytedance.streamshop.data.remote.ApiClient.getInstance();
                String userId = client.getCurrentUserId();

                com.bytedance.streamshop.data.remote.ApiResponse<java.util.Map<String, Object>> resp =
                        apiService.getAllProducts();
                if (resp == null || resp.getData() == null) return;

                List<Map<String, Object>> allProducts = resp.getData();
                if (allProducts == null || allProducts.isEmpty()) return;

                // Filter: owner's products not already in the room
                java.util.Set<String> existingIds = new java.util.HashSet<>();
                for (Map<String, Object> p : products) {
                    Object id = p.get("id");
                    if (id != null) existingIds.add((String) id);
                }

                List<Map<String, Object>> available = new ArrayList<>();
                for (Map<String, Object> p : allProducts) {
                    String ownerId = (String) p.get("ownerId");
                    String pid = (String) p.get("id");
                    if (userId != null && userId.equals(ownerId) && !existingIds.contains(pid)) {
                        available.add(p);
                    }
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> showProductPicker(available));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void showProductPicker(List<Map<String, Object>> available) {
        if (available.isEmpty()) {
            Toast.makeText(getContext(), "没有可添加的商品", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] names = new CharSequence[available.size()];
        for (int i = 0; i < available.size(); i++) {
            Map<String, Object> p = available.get(i);
            String title = (String) p.get("title");
            Object price = p.get("price");
            double priceVal = price instanceof Number ? ((Number) price).doubleValue() : 0;
            names[i] = title + " (¥" + priceFmt.format(priceVal) + ")";
        }

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("选择商品添加到直播间")
                .setItems(names, (dialog, which) -> {
                    Map<String, Object> selected = available.get(which);
                    String productId = (String) selected.get("id");
                    if (productId != null) {
                        addProductToRoom(productId, selected);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addProductToRoom(String productId, Map<String, Object> product) {
        new Thread(() -> {
            try {
                boolean ok = apiService.bindProductToLiveRoom(roomId, productId);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (ok) {
                            products.add(product);
                            adapter.notifyItemInserted(products.size() - 1);
                            updateUI();
                            notifyListener();
                        } else {
                            Toast.makeText(getContext(), "添加失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "网络错误", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void notifyListener() {
        if (listener != null) listener.onProductsChanged();
    }

    private class ProductManageAdapter extends RecyclerView.Adapter<ProductManageAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_live_manage_product, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> product = products.get(position);
            String cover = (String) product.get("coverUrl");
            String name = (String) product.get("title");
            Object price = product.get("price");
            Object stockObj = product.get("stock");
            String priceText = "¥" + (price instanceof Number
                    ? priceFmt.format(((Number) price).doubleValue()) : "0");
            int stock = stockObj instanceof Number ? ((Number) stockObj).intValue() : 0;
            String stockText = "库存: " + stock;

            Glide.with(holder.thumb).load(cover).into(holder.thumb);
            holder.name.setText(name);
            holder.price.setText(priceText);
            holder.stock.setText(stockText);

            holder.stock.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Map<String, Object> p = products.get(pos);
                String pid = (String) p.get("id");
                if (pid == null) return;
                showEditStockDialog(pid, pos);
            });

            holder.upBtn.setOnClickListener(v -> moveUp(holder.getBindingAdapterPosition()));
            holder.downBtn.setOnClickListener(v -> moveDown(holder.getBindingAdapterPosition()));
            holder.removeBtn.setOnClickListener(v -> removeProduct(holder.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() { return products.size(); }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView thumb;
            TextView name, price, stock;
            ImageButton upBtn, downBtn, removeBtn;

            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.manage_item_thumb);
                name = v.findViewById(R.id.manage_item_name);
                price = v.findViewById(R.id.manage_item_price);
                stock = v.findViewById(R.id.manage_item_stock);
                upBtn = v.findViewById(R.id.manage_item_up);
                downBtn = v.findViewById(R.id.manage_item_down);
                removeBtn = v.findViewById(R.id.manage_item_remove);
            }
        }
    }

    private void showEditStockDialog(String productId, int position) {
        Map<String, Object> product = products.get(position);
        Object stockObj = product.get("stock");
        int currentStock = stockObj instanceof Number ? ((Number) stockObj).intValue() : 0;

        android.widget.EditText input = new android.widget.EditText(getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentStock));
        input.selectAll();

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("修改库存")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    try {
                        int newStock = Integer.parseInt(input.getText().toString().trim());
                        new Thread(() -> {
                            try {
                                boolean ok = apiService.updateProductStock(productId, newStock);
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (ok) {
                                            product.put("stock", newStock);
                                            adapter.notifyItemChanged(position);
                                            Toast.makeText(getContext(), "库存已更新", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(getContext(), "更新失败", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() ->
                                            Toast.makeText(getContext(), "网络错误", Toast.LENGTH_SHORT).show());
                                }
                            }
                        }).start();
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }
}

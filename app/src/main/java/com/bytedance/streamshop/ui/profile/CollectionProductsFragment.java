package com.bytedance.streamshop.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.Product;
import com.bytedance.streamshop.ui.feed.ProductDetailBottomSheetFragment;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CollectionProductsFragment extends Fragment {
    private RecyclerView gridView;
    private View emptyView;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private ProductGridAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection_products, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gridView = view.findViewById(R.id.collection_products_grid);
        emptyView = view.findViewById(R.id.collection_products_empty);

        gridView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new ProductGridAdapter();
        gridView.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> data = new ApiService().getCollections("product");
                items.clear();
                items.addAll(data);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private class ProductGridAdapter extends RecyclerView.Adapter<ProductGridAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_collection_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = items.get(position);
            Map<String, Object> product = (Map<String, Object>) item.get("target");
            if (product != null) {
                Glide.with(h.image).load((String) product.get("coverUrl")).into(h.image);
                h.title.setText((String) product.getOrDefault("title", ""));
                double price = ((Number) product.getOrDefault("price", 0)).doubleValue();
                h.price.setText("¥" + (int) price);
                Object sales = product.get("salesCount");
                h.sales.setText("已售 " + (sales != null ? sales : 0));
            }
            h.itemView.setOnClickListener(v -> showProductDetail(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView image;
            TextView title, price, sales;
            VH(View v) {
                super(v);
                image = v.findViewById(R.id.col_product_image);
                title = v.findViewById(R.id.col_product_title);
                price = v.findViewById(R.id.col_product_price);
                sales = v.findViewById(R.id.col_product_sales);
            }
        }
    }

    private void showProductDetail(int position) {
        Map<String, Object> item = items.get(position);
        Map<String, Object> target = (Map<String, Object>) item.get("target");
        if (target == null) return;

        Product product = new Product();
        product.setId((String) target.get("id"));
        product.setTitle((String) target.get("title"));
        product.setPrice(((Number) target.getOrDefault("price", 0)).doubleValue());
        product.setOriginalPrice(((Number) target.getOrDefault("originalPrice", 0)).doubleValue());
        product.setCoverUrl((String) target.get("coverUrl"));
        product.setStock(((Number) target.getOrDefault("stock", 0)).intValue());
        product.setSalesCount(((Number) target.getOrDefault("salesCount", 0)).intValue());
        product.setDescription((String) target.get("description"));

        FragmentManager fm = getParentFragmentManager();
        ProductDetailBottomSheetFragment sheet = ProductDetailBottomSheetFragment.newInstance(product);
        sheet.show(fm, "collection_product");
    }
}

package com.bytedance.streamshop.ui.live;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiveCouponCreateBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ROOM_ID = "room_id";
    private static final String ARG_PRODUCTS_JSON = "products_json";

    private String roomId;
    private List<Map<String, Object>> roomProducts;

    private EditText nameInput, valueInput, minPurchaseInput, stockInput;
    private RadioGroup typeGroup, scopeGroup;
    private RadioButton typeFixed, typePercent, scopeSpecific;
    private LinearLayout productSelectContainer;
    private final List<CheckBox> productCheckBoxes = new ArrayList<>();
    private Spinner validSpinner, claimSpinner;
    private Button publishBtn;
    private ApiService apiService;

    public interface OnCouponCreatedListener {
        void onCouponCreated();
    }

    private OnCouponCreatedListener listener;

    public static LiveCouponCreateBottomSheet newInstance(String roomId,
                                                          List<Map<String, Object>> products) {
        LiveCouponCreateBottomSheet fragment = new LiveCouponCreateBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Map<String, Object> p : products) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("id", (String) p.get("id"));
                obj.put("title", (String) p.get("title"));
                arr.put(obj);
            }
            args.putString(ARG_PRODUCTS_JSON, arr.toString());
        } catch (Exception ignored) {}
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnCouponCreatedListener(OnCouponCreatedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomProducts = new ArrayList<>();
        apiService = new ApiService();
        if (getArguments() != null) {
            roomId = getArguments().getString(ARG_ROOM_ID);
            try {
                org.json.JSONArray arr = new org.json.JSONArray(
                        getArguments().getString(ARG_PRODUCTS_JSON, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", obj.getString("id"));
                    map.put("title", obj.getString("title"));
                    roomProducts.add(map);
                }
            } catch (Exception ignored) {}
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_coupon_create_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        nameInput = view.findViewById(R.id.coupon_create_name);
        valueInput = view.findViewById(R.id.coupon_create_value);
        minPurchaseInput = view.findViewById(R.id.coupon_create_min_purchase);
        stockInput = view.findViewById(R.id.coupon_create_stock);
        typeGroup = view.findViewById(R.id.coupon_create_type);
        typeFixed = view.findViewById(R.id.coupon_type_fixed);
        typePercent = view.findViewById(R.id.coupon_type_percent);
        scopeGroup = view.findViewById(R.id.coupon_create_scope);
        scopeSpecific = view.findViewById(R.id.coupon_scope_specific);
        productSelectContainer = view.findViewById(R.id.coupon_product_select_container);

        // Show/hide product selection when scope changes
        scopeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.coupon_scope_specific) {
                buildProductCheckboxes();
                productSelectContainer.setVisibility(View.VISIBLE);
            } else {
                productSelectContainer.setVisibility(View.GONE);
            }
        });

        validSpinner = view.findViewById(R.id.coupon_create_valid_spinner);
        claimSpinner = view.findViewById(R.id.coupon_create_claim_spinner);
        publishBtn = view.findViewById(R.id.coupon_create_publish_btn);
        TextView valueUnit = view.findViewById(R.id.coupon_value_unit);

        // Update unit label when type changes
        typeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.coupon_type_percent) {
                valueUnit.setText("%");
            } else {
                valueUnit.setText("元");
            }
        });

        // Valid duration spinner
        String[] validOptions = {"1小时", "6小时", "12小时", "24小时", "3天"};
        Integer[] validHours = {1, 6, 12, 24, 72};
        ArrayAdapter<String> validAdapter = new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_spinner_item, validOptions) {
            @NonNull @Override
            public View getView(int position, @Nullable View v, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, v, parent);
                tv.setTextColor(0xFFFFFFFF);
                return tv;
            }
            @Override
            public View getDropDownView(int position, @Nullable View v, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, v, parent);
                tv.setTextColor(0xFFFFFFFF);
                return tv;
            }
        };
        validAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        validSpinner.setAdapter(validAdapter);

        // Claim deadline spinner
        String[] claimOptions = {"1分钟", "3分钟", "5分钟", "10分钟", "15分钟"};
        Integer[] claimMinutes = {1, 3, 5, 10, 15};
        ArrayAdapter<String> claimAdapter = new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_spinner_item, claimOptions) {
            @NonNull @Override
            public View getView(int position, @Nullable View v, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, v, parent);
                tv.setTextColor(0xFFFFFFFF);
                return tv;
            }
            @Override
            public View getDropDownView(int position, @Nullable View v, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, v, parent);
                tv.setTextColor(0xFFFFFFFF);
                return tv;
            }
        };
        claimAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        claimSpinner.setAdapter(claimAdapter);

        publishBtn.setOnClickListener(v -> publishCoupon(validHours, claimMinutes));
    }

    private void buildProductCheckboxes() {
        if (productSelectContainer.getChildCount() > 0) return; // Already built
        productCheckBoxes.clear();
        for (Map<String, Object> p : roomProducts) {
            CheckBox cb = new CheckBox(getContext());
            cb.setText((String) p.get("title"));
            cb.setTag(p.get("id"));
            cb.setTextColor(0xFFFFFFFF);
            cb.setPadding(0, 8, 0, 8);
            productCheckBoxes.add(cb);
            productSelectContainer.addView(cb);
        }
    }

    private void publishCoupon(Integer[] validHours, Integer[] claimMinutes) {
        String title = nameInput.getText().toString().trim();
        String valueStr = valueInput.getText().toString().trim();
        String minStr = minPurchaseInput.getText().toString().trim();
        String stockStr = stockInput.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(getContext(), "请输入优惠券名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (valueStr.isEmpty()) {
            Toast.makeText(getContext(), "请输入优惠值", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = typeFixed.isChecked() ? "fixed" : "percentage";
        double value = Double.parseDouble(valueStr);
        int minPurchase = minStr.isEmpty() ? 0 : Integer.parseInt(minStr);
        int stock = stockStr.isEmpty() ? 100 : Integer.parseInt(stockStr);
        int validIdx = validSpinner.getSelectedItemPosition();
        int claimIdx = claimSpinner.getSelectedItemPosition();
        long validMs = validHours[validIdx] * 3600 * 1000L;
        int claimMin = claimMinutes[claimIdx];
        String validTo = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                java.util.Locale.US).format(new java.util.Date(System.currentTimeMillis() + validMs));

        // Product scope
        String productScope = "all";
        List<String> productIds = new ArrayList<>();
        if (scopeSpecific != null && scopeSpecific.isChecked()) {
            productScope = "specific";
            for (CheckBox cb : productCheckBoxes) {
                if (cb.isChecked()) {
                    productIds.add((String) cb.getTag());
                }
            }
            if (productIds.isEmpty()) {
                Toast.makeText(getContext(), "请至少选择一个商品", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Build params
        Map<String, Object> params = new HashMap<>();
        params.put("title", title);
        params.put("type", type);
        params.put("value", value);
        params.put("minPurchase", minPurchase);
        params.put("stock", stock);
        params.put("validTo", validTo);
        params.put("claimDeadlineMinutes", claimMin);
        params.put("productScope", productScope);
        params.put("productIds", productIds);

        new Thread(() -> {
            try {
                boolean ok = apiService.createLiveCoupon(roomId, params);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (ok) {
                            Toast.makeText(getContext(), "优惠券已发布", Toast.LENGTH_SHORT).show();
                            if (listener != null) listener.onCouponCreated();
                            dismiss();
                        } else {
                            Toast.makeText(getContext(), "发布失败", Toast.LENGTH_SHORT).show();
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
}

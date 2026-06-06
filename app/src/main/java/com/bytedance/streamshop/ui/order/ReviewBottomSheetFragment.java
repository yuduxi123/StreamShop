package com.bytedance.streamshop.ui.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

public class ReviewBottomSheetFragment extends BottomSheetDialogFragment {
    private static final String ARG_PRODUCT_ID = "productId";
    private static final String ARG_ORDER_ID = "orderId";

    private String productId;
    private String orderId;
    private int selectedRating = 0;
    private ImageView[] stars = new ImageView[5];

    public static ReviewBottomSheetFragment newInstance(String productId, String orderId) {
        ReviewBottomSheetFragment f = new ReviewBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PRODUCT_ID, productId);
        args.putString(ARG_ORDER_ID, orderId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            productId = getArguments().getString(ARG_PRODUCT_ID);
            orderId = getArguments().getString(ARG_ORDER_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_review_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        stars[0] = view.findViewById(R.id.review_star_1);
        stars[1] = view.findViewById(R.id.review_star_2);
        stars[2] = view.findViewById(R.id.review_star_3);
        stars[3] = view.findViewById(R.id.review_star_4);
        stars[4] = view.findViewById(R.id.review_star_5);
        EditText input = view.findViewById(R.id.review_input);
        MaterialButton submitBtn = view.findViewById(R.id.review_submit_btn);

        for (int i = 0; i < 5; i++) {
            final int rating = i + 1;
            stars[i].setOnClickListener(v -> setRating(rating));
        }

        submitBtn.setOnClickListener(v -> {
            if (selectedRating == 0) {
                Toast.makeText(getContext(), "请选择评分", Toast.LENGTH_SHORT).show();
                return;
            }
            String content = input.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(getContext(), "请输入评价内容", Toast.LENGTH_SHORT).show();
                return;
            }

            submitBtn.setEnabled(false);
            new Thread(() -> {
                try {
                    new ApiService().postReview("product", productId, content, selectedRating, orderId);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "评价成功", Toast.LENGTH_SHORT).show();
                            dismiss();
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            submitBtn.setEnabled(true);
                            Toast.makeText(getContext(), "评价失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }).start();
        });
    }

    private void setRating(int rating) {
        selectedRating = rating;
        for (int i = 0; i < 5; i++) {
            stars[i].setImageResource(i < rating ? R.drawable.ic_star_on : R.drawable.ic_star_off);
        }
    }
}

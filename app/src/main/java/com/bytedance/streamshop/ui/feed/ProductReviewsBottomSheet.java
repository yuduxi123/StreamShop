package com.bytedance.streamshop.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.domain.model.Comment;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductReviewsBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_PRODUCT_ID = "productId";

    private String productId;
    private RecyclerView reviewsList;
    private TextView emptyView, avgRatingText, avgStarsText, countText;
    private ReviewsAdapter adapter;
    private final List<Comment> reviews = new ArrayList<>();

    public static ProductReviewsBottomSheet newInstance(String productId) {
        ProductReviewsBottomSheet f = new ProductReviewsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PRODUCT_ID, productId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            productId = getArguments().getString(ARG_PRODUCT_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product_reviews, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        avgRatingText = view.findViewById(R.id.reviews_avg_rating);
        avgStarsText = view.findViewById(R.id.reviews_avg_stars);
        countText = view.findViewById(R.id.reviews_count);
        reviewsList = view.findViewById(R.id.reviews_list);
        emptyView = view.findViewById(R.id.reviews_empty);

        reviewsList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ReviewsAdapter();
        reviewsList.setAdapter(adapter);

        loadReviews();
    }

    private void loadReviews() {
        new Thread(() -> {
            try {
                ApiService api = new ApiService();

                // Load summary
                Map<String, Object> summary = api.getProductReviewSummary(productId);
                double avg = ((Number) summary.get("averageRating")).doubleValue();
                int total = ((Number) summary.get("totalReviews")).intValue();

                // Load reviews
                var response = api.getProductReviews(productId, 1, 20);
                List<Comment> data = response.getData();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        avgRatingText.setText(String.format(Locale.US, "%.1f", avg));
                        avgStarsText.setText(getStarsString(avg));
                        countText.setText("共 " + total + " 条评价");

                        reviews.clear();
                        if (data != null) reviews.addAll(data);
                        adapter.notifyDataSetChanged();

                        boolean empty = reviews.isEmpty();
                        reviewsList.setVisibility(empty ? View.GONE : View.VISIBLE);
                        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                    });
                }
            } catch (Exception ignored) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        emptyView.setVisibility(View.VISIBLE);
                        reviewsList.setVisibility(View.GONE);
                    });
                }
            }
        }).start();
    }

    private String getStarsString(double rating) {
        int full = (int) rating;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < full ? "★" : "☆");
        }
        return sb.toString();
    }

    private class ReviewsAdapter extends RecyclerView.Adapter<ReviewsAdapter.VH> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_review, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Comment review = reviews.get(position);

            if (review.getUser() != null) {
                h.username.setText(review.getUser().getUsername());
                String avatar = review.getUser().getAvatarUrl();
                if (avatar != null && !avatar.isEmpty()) {
                    Glide.with(h.avatar).load(avatar).circleCrop().into(h.avatar);
                }
            } else {
                h.username.setText("匿名用户");
            }

            h.content.setText(review.getContent());

            // Build stars
            h.starsContainer.removeAllViews();
            int rating = review.getRating();
            for (int i = 0; i < 5; i++) {
                ImageView star = new ImageView(getContext());
                int size = (int) (12 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                star.setLayoutParams(lp);
                star.setImageResource(i < rating ? R.drawable.ic_star_on : R.drawable.ic_star_off);
                star.setScaleType(ImageView.ScaleType.FIT_CENTER);
                h.starsContainer.addView(star);
            }

            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        .parse(review.getCreatedAt());
                if (d != null) h.time.setText(sdf.format(d));
            } catch (Exception e) {
                h.time.setText("");
            }
        }

        @Override
        public int getItemCount() { return reviews.size(); }

        class VH extends RecyclerView.ViewHolder {
            ShapeableImageView avatar;
            TextView username, content, time;
            LinearLayout starsContainer;

            VH(View v) {
                super(v);
                avatar = v.findViewById(R.id.review_avatar);
                username = v.findViewById(R.id.review_username);
                content = v.findViewById(R.id.review_content);
                time = v.findViewById(R.id.review_time);
                starsContainer = v.findViewById(R.id.review_stars_display);
            }
        }
    }
}

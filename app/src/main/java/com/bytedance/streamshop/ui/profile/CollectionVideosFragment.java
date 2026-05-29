package com.bytedance.streamshop.ui.profile;

import android.content.Intent;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CollectionVideosFragment extends Fragment {
    private RecyclerView gridView;
    private View emptyView;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private VideoGridAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection_videos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gridView = view.findViewById(R.id.collection_videos_grid);
        emptyView = view.findViewById(R.id.collection_videos_empty);

        gridView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        adapter = new VideoGridAdapter();
        gridView.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> data = new ApiService().getCollections("video");
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

    private class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_collection_video, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = items.get(position);
            Map<String, Object> target = (Map<String, Object>) item.get("target");
            if (target != null) {
                Glide.with(h.thumb).load((String) target.get("coverUrl")).into(h.thumb);
                Object collectionCount = target.get("collectionCount");
                h.count.setText(collectionCount != null ? formatCount(((Number) collectionCount).intValue()) : "0");
            }
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), LikedVideoFeedActivity.class);
                intent.putExtra("start_position", h.getBindingAdapterPosition());
                intent.putExtra("source", "collections");
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView count;
            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.collected_thumb);
                count = v.findViewById(R.id.collected_count);
            }
        }
    }

    private String formatCount(int count) {
        if (count >= 10000) return String.format("%.1fw", count / 10000.0);
        return String.valueOf(count);
    }
}

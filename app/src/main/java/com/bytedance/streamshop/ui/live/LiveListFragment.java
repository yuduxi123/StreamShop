package com.bytedance.streamshop.ui.live;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bumptech.glide.Glide;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LiveListFragment extends Fragment {
    private RecyclerView roomList;
    private TextView emptyView;
    private RoomAdapter adapter;
    private ApiService apiService;
    private final List<Map<String, Object>> rooms = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        roomList = view.findViewById(R.id.live_room_list);
        emptyView = view.findViewById(R.id.live_list_empty);
        apiService = new ApiService();

        roomList.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new RoomAdapter();
        roomList.setAdapter(adapter);

        loadRooms();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRooms();
    }

    private void loadRooms() {
        new Thread(() -> {
            try {
                var response = apiService.getRooms(1, 50);
                rooms.clear();
                rooms.addAll(response.getData());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        emptyView.setVisibility(rooms.isEmpty() ? View.VISIBLE : View.GONE);
                        roomList.setVisibility(rooms.isEmpty() ? View.GONE : View.VISIBLE);
                    });
                }
            } catch (Exception ignored) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        emptyView.setVisibility(View.VISIBLE);
                        roomList.setVisibility(View.GONE);
                    });
                }
            }
        }).start();
    }

    private class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.VH> {
        private final NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_live_room, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> room = rooms.get(pos);
            String title = (String) room.get("title");
            String cover = (String) room.get("coverUrl");
            String status = (String) room.get("status");

            h.title.setText(title != null ? title : "直播间");
            Glide.with(h.itemView).load(cover).into(h.cover);

            Map<String, Object> anchor = (Map<String, Object>) room.get("anchor");
            h.anchorName.setText(anchor != null ? (String) anchor.get("username") : "主播");

            boolean isLive = "live".equals(status);
            h.liveBadge.setVisibility(isLive ? View.VISIBLE : View.GONE);

            if (isLive) {
                int online = room.get("onlineCount") instanceof Number
                        ? ((Number) room.get("onlineCount")).intValue() : 0;
                String onlineStr = online > 1000
                        ? nf.format(online / 1000.0) + "k"
                        : String.valueOf(online);
                h.statusText.setText(onlineStr + "人在看");
                h.statusText.setTextColor(0xCCFFFFFF);
                h.statusText.setVisibility(View.VISIBLE);
            } else {
                h.statusText.setText("未开播");
                h.statusText.setTextColor(0xCCFFFFFF);
                h.statusText.setVisibility(View.VISIBLE);
            }

            h.itemView.setOnClickListener(v -> {
                if (isLive && getActivity() != null) {
                    Intent intent = new Intent(getActivity(), LiveRoomActivity.class);
                    intent.putExtra("room_id", (String) room.get("id"));
                    startActivity(intent);
                }
            });
        }

        @Override public int getItemCount() { return rooms.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView title, anchorName, statusText, liveBadge;
            VH(View v) {
                super(v);
                cover = v.findViewById(R.id.room_cover);
                title = v.findViewById(R.id.room_title);
                anchorName = v.findViewById(R.id.room_anchor);
                statusText = v.findViewById(R.id.room_status);
                liveBadge = v.findViewById(R.id.room_live_badge);
            }
        }
    }
}

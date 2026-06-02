package com.bytedance.streamshop.ui.live;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.bytedance.streamshop.ui.feed.LiveFeedCardViewHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LiveListFragment extends Fragment {
    private ViewPager2 viewPager;
    private TextView emptyView;
    private LiveRoomPagerAdapter adapter;
    private ApiService apiService;
    private final List<Map<String, Object>> rooms = new ArrayList<>();
    private int currentPosition = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewPager = view.findViewById(R.id.live_viewpager);
        emptyView = view.findViewById(R.id.live_list_empty);
        apiService = new ApiService();

        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager.setOffscreenPageLimit(1);
        adapter = new LiveRoomPagerAdapter();
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                if (position >= rooms.size() - 2) {
                    loadRooms();
                }
            }
        });

        loadRooms();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (rooms.isEmpty()) {
            loadRooms();
        }
    }

    private void loadRooms() {
        new Thread(() -> {
            try {
                var response = apiService.getRooms(1, 50);
                List<Map<String, Object>> data = response.getData();
                if (data == null || data.isEmpty()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            emptyView.setVisibility(View.VISIBLE);
                            viewPager.setVisibility(View.GONE);
                        });
                    }
                    return;
                }

                // Only show live rooms
                List<Map<String, Object>> liveRooms = new ArrayList<>();
                for (Map<String, Object> room : data) {
                    if ("live".equals(room.get("status"))) {
                        liveRooms.add(room);
                    }
                }

                rooms.clear();
                rooms.addAll(liveRooms);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        boolean empty = rooms.isEmpty();
                        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                        viewPager.setVisibility(empty ? View.GONE : View.VISIBLE);
                        currentPosition = Math.min(currentPosition, rooms.size() - 1);
                        viewPager.setCurrentItem(currentPosition, false);
                    });
                }
            } catch (Exception ignored) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        emptyView.setVisibility(View.VISIBLE);
                        viewPager.setVisibility(View.GONE);
                    });
                }
            }
        }).start();
    }

    private class LiveRoomPagerAdapter extends RecyclerView.Adapter<LiveFeedCardViewHolder> {
        @NonNull
        @Override
        public LiveFeedCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new LiveFeedCardViewHolder(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_feed_live_card, parent, false)
            );
        }

        @Override
        public void onBindViewHolder(@NonNull LiveFeedCardViewHolder holder, int position) {
            Map<String, Object> room = rooms.get(position);
            holder.bind(room, r -> {
                String roomId = r.get("id") != null ? String.valueOf(r.get("id")) : "";
                if (!roomId.isEmpty() && getActivity() != null) {
                    Intent intent = new Intent(getActivity(), LiveRoomActivity.class);
                    intent.putExtra("room_id", roomId);
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }
    }
}

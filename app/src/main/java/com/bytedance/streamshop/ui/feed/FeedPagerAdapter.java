package com.bytedance.streamshop.ui.feed;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.domain.model.FeedItem;
import com.bytedance.streamshop.domain.model.Video;

import java.util.ArrayList;
import java.util.List;

public class FeedPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_VIDEO = 1;
    private static final int TYPE_LIVE = 2;

    private List<FeedItem> items = new ArrayList<>();
    private FragmentManager fragmentManager;
    private VideoViewHolder.OnVideoEndedListener playbackModeListener;
    private LiveFeedCardViewHolder.OnLiveCardClickListener liveCardClickListener;

    public void setFragmentManager(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
    }

    public void setPlaybackModeListener(VideoViewHolder.OnVideoEndedListener listener) {
        this.playbackModeListener = listener;
    }

    public void setLiveCardClickListener(LiveFeedCardViewHolder.OnLiveCardClickListener listener) {
        this.liveCardClickListener = listener;
    }

    public void setItems(List<FeedItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addItems(List<FeedItem> newItems) {
        if (newItems == null || newItems.isEmpty()) return;
        int startPos = this.items.size();
        this.items.addAll(newItems);
        notifyItemRangeInserted(startPos, newItems.size());
    }

    public List<FeedItem> getItems() {
        return items;
    }

    public RecyclerView.ViewHolder getViewHolderAt(RecyclerView recyclerView, int position) {
        if (position < 0 || position >= items.size()) return null;
        return recyclerView.findViewHolderForAdapterPosition(position);
    }

    @Override
    public int getItemViewType(int position) {
        FeedItem item = items.get(position);
        return item != null && item.isLive() ? TYPE_LIVE : TYPE_VIDEO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_LIVE) {
            return new LiveFeedCardViewHolder(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_feed_live_card, parent, false)
            );
        }
        return new VideoViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_video_page, parent, false)
        );
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FeedItem item = items.get(position);
        if (holder instanceof VideoViewHolder) {
            Video video = item.getVideo();
            if (video != null) {
                ((VideoViewHolder) holder).bind(video, holder.itemView.getContext(), fragmentManager);
                ((VideoViewHolder) holder).setOnPlaybackModeListener(playbackModeListener);
            }
        } else if (holder instanceof LiveFeedCardViewHolder && item.getLiveRoom() != null) {
            ((LiveFeedCardViewHolder) holder).bind(item.getLiveRoom(), liveCardClickListener);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).release();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}

package com.bytedance.streamshop.ui.feed;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.domain.model.Video;

import java.util.ArrayList;
import java.util.List;

public class FeedPagerAdapter extends RecyclerView.Adapter<VideoViewHolder> {

    private List<Video> videos = new ArrayList<>();
    private FragmentManager fragmentManager;

    public void setFragmentManager(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
    }

    public void setVideos(List<Video> videos) {
        this.videos = videos != null ? videos : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addVideos(List<Video> newVideos) {
        if (newVideos == null || newVideos.isEmpty()) return;
        int startPos = this.videos.size();
        this.videos.addAll(newVideos);
        notifyItemRangeInserted(startPos, newVideos.size());
    }

    public List<Video> getVideos() {
        return videos;
    }

    public VideoViewHolder getViewHolderAt(RecyclerView recyclerView, int position) {
        if (position < 0 || position >= videos.size()) return null;
        return (VideoViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VideoViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_video_page, parent, false)
        );
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video video = videos.get(position);
        holder.bind(video, holder.itemView.getContext(), fragmentManager);
    }

    @Override
    public void onViewRecycled(@NonNull VideoViewHolder holder) {
        super.onViewRecycled(holder);
        holder.release();
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }
}

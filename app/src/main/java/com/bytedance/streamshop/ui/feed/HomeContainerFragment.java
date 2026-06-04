package com.bytedance.streamshop.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.ui.live.LiveListFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class HomeContainerFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home_container, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.home_tab_layout);
        viewPager = view.findViewById(R.id.home_tab_pager);

        viewPager.setOffscreenPageLimit(1);
        viewPager.setAdapter(new HomeTabAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "推荐" : "直播");
        }).attach();
    }

    public void switchToLiveTab() {
        if (viewPager != null) {
            viewPager.setCurrentItem(1, true);
        }
    }

    private class HomeTabAdapter extends FragmentStateAdapter {
        HomeTabAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new FeedFragment();
            }
            return new LiveListFragment();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}

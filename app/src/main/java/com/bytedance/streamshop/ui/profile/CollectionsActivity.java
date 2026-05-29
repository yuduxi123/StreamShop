package com.bytedance.streamshop.ui.profile;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bytedance.streamshop.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class CollectionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collections);

        findViewById(R.id.collections_back).setOnClickListener(v -> finish());

        ViewPager2 pager = findViewById(R.id.collections_pager);
        TabLayout tabs = findViewById(R.id.collections_tabs);

        pager.setAdapter(new CollectionsPagerAdapter(this));

        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            tab.setText(position == 0 ? "视频" : "商品");
        }).attach();
    }

    private static class CollectionsPagerAdapter extends FragmentStateAdapter {
        public CollectionsPagerAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new CollectionVideosFragment();
            } else {
                return new CollectionProductsFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}

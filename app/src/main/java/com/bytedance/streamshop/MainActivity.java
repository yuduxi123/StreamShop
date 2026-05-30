package com.bytedance.streamshop;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.bytedance.streamshop.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private TextView[] tabs;
    private int[][] tabDestIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) return;
        navController = navHostFragment.getNavController();

        tabs = new TextView[] {
                findViewById(R.id.nav_feed),
                findViewById(R.id.nav_live),
                findViewById(R.id.nav_cart),
                findViewById(R.id.nav_profile)
        };
        tabDestIds = new int[][] {
                new int[] { R.id.navigation_feed },
                new int[] { R.id.navigation_live },
                new int[] { R.id.navigation_cart },
                new int[] { R.id.navigation_profile }
        };

        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> selectTab(index));
        }

        navController.addOnDestinationChangedListener((ctrl, dest, args) -> {
            for (int i = 0; i < tabs.length; i++) {
                updateTabStyle(i, dest.getId() == tabDestIds[i][0]);
            }
        });

        selectTab(0);
    }

    private void selectTab(int index) {
        navController.navigate(tabDestIds[index][0]);
        for (int i = 0; i < tabs.length; i++) {
            updateTabStyle(i, i == index);
        }
    }

    private void updateTabStyle(int index, boolean selected) {
        TextView tab = tabs[index];
        if (selected) {
            tab.setTextColor(0xFFFFFFFF);
            tab.setTextSize(15);
            tab.setTypeface(null, Typeface.BOLD);
        } else {
            tab.setTextColor(0xFFAAAAAA);
            tab.setTextSize(13);
            tab.setTypeface(null, Typeface.NORMAL);
        }
    }
}

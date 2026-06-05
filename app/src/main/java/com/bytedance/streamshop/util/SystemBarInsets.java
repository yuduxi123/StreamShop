package com.bytedance.streamshop.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class SystemBarInsets {
    private static final String STATUS_BAR_INSET_TAG = "status_bar_inset";

    private SystemBarInsets() {}

    public static void applyStatusBarPadding(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        content.post(() -> {
            if (content.getChildCount() == 0) return;
            View target = content.findViewWithTag(STATUS_BAR_INSET_TAG);
            applyStatusBarPadding(target != null ? target : content.getChildAt(0));
        });
    }

    public static void applyStatusBarPadding(View view) {
        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (target, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            target.setPadding(
                    initialLeft,
                    initialTop + statusBars.top,
                    initialRight,
                    initialBottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}

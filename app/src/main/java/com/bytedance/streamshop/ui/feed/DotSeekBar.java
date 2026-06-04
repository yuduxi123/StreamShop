package com.bytedance.streamshop.ui.feed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatSeekBar;

import java.util.ArrayList;
import java.util.List;

public class DotSeekBar extends AppCompatSeekBar {
    private List<Float> dotRatios = new ArrayList<>();
    private final Paint dotPaint;
    private final float dotRadiusPx;

    public DotSeekBar(Context context) {
        this(context, null);
    }

    public DotSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0xFFFFFFFF);
        dotPaint.setStyle(Paint.Style.FILL);
        dotRadiusPx = dpToPx(2.5f);
    }

    public void setDotRatios(List<Float> ratios) {
        dotRatios = ratios != null ? ratios : new ArrayList<>();
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dotRatios.isEmpty()) return;

        int trackLeft = getPaddingLeft();
        int trackRight = getWidth() - getPaddingRight();
        int trackWidth = trackRight - trackLeft;
        if (trackWidth <= 0) return;

        float centerY = getHeight() / 2f;

        for (float ratio : dotRatios) {
            float x = trackLeft + ratio * trackWidth;
            canvas.drawCircle(x, centerY, dotRadiusPx, dotPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}

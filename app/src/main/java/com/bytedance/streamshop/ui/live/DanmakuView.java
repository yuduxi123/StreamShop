package com.bytedance.streamshop.ui.live;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class DanmakuView extends View {
    private static final int MAX_VISIBLE = 20;
    private static final int TEXT_SIZE = 28;
    private static final float SPEED = 2.5f;

    private final List<DanmakuItem> items = new ArrayList<>();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    private final Runnable frameCallback = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            updatePositions();
            invalidate();
            handler.postDelayed(this, 16); // ~60fps
        }
    };

    public DanmakuView(Context context) {
        super(context);
        init();
    }

    public DanmakuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setTextSize(TEXT_SIZE);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        strokePaint.setTextSize(TEXT_SIZE);
        strokePaint.setTypeface(Typeface.DEFAULT_BOLD);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4);
        strokePaint.setColor(0xCC000000);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        handler.post(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        handler.removeCallbacks(frameCallback);
    }

    public void addDanmaku(String text, String colorHex) {
        if (items.size() > MAX_VISIBLE) return;
        int color = 0xFFFFFFFF;

        float y = 40 + random.nextInt(Math.max(1, getHeight() - 80));
        DanmakuItem item = new DanmakuItem(text, getWidth(), y, color);
        items.add(item);
    }

    private void updatePositions() {
        Iterator<DanmakuItem> it = items.iterator();
        while (it.hasNext()) {
            DanmakuItem item = it.next();
            item.x -= SPEED;
            if (item.x + item.width < 0) {
                it.remove();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (DanmakuItem item : items) {
            // Measure width if not yet
            if (item.width <= 0) {
                item.width = textPaint.measureText(item.text);
            }
            // Draw stroke
            strokePaint.setColor(0xCC000000);
            canvas.drawText(item.text, item.x - 1, item.y, strokePaint);
            canvas.drawText(item.text, item.x + 1, item.y, strokePaint);
            canvas.drawText(item.text, item.x, item.y - 1, strokePaint);
            canvas.drawText(item.text, item.x, item.y + 1, strokePaint);
            // Draw text
            textPaint.setColor(item.color);
            canvas.drawText(item.text, item.x, item.y, textPaint);
        }
    }

    private static class DanmakuItem {
        String text;
        float x, y;
        int color;
        float width;

        DanmakuItem(String text, float x, float y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.width = 0;
        }
    }
}

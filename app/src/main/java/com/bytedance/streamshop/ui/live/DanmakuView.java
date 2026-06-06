package com.bytedance.streamshop.ui.live;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class DanmakuView extends View {
    private static final int MAX_VISIBLE = 20;
    private static final int TEXT_SIZE = 28;
    private static final float SPEED_PX_PER_SEC = 106f; // same visual speed as before
    private static final int FRAME_INTERVAL_MS = 16; // ~60fps throttle

    private final List<DanmakuItem> items = new ArrayList<>();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private boolean running = false;
    private boolean frameScheduled = false;

    private long lastFrameTime = 0;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frameScheduled = false;
            if (!running) return;

            long now = System.nanoTime();
            if (lastFrameTime == 0) lastFrameTime = now;
            float deltaMs = (now - lastFrameTime) / 1_000_000f;

            // Throttle to ~60fps — skip tiny deltas from burst callbacks
            if (deltaMs >= FRAME_INTERVAL_MS * 0.5f) {
                lastFrameTime = now;
                updatePositions(deltaMs);
                if (!items.isEmpty()) {
                    invalidate();
                }
            }

            if (running && (!items.isEmpty() || frameScheduled)) {
                scheduleFrame();
            }
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
        textPaint.setAntiAlias(true);
        strokePaint.setTextSize(TEXT_SIZE);
        strokePaint.setTypeface(Typeface.DEFAULT_BOLD);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4);
        strokePaint.setColor(0xCC000000);
        strokePaint.setAntiAlias(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
    }

    public void addDanmaku(String text, String colorHex) {
        if (items.size() > MAX_VISIBLE) return;
        float y = 40 + random.nextInt(Math.max(1, getHeight() - 80));
        items.add(new DanmakuItem(text, getWidth(), y, 0xFFFFFFFF));
        if (!frameScheduled) {
            scheduleFrame();
        }
    }

    public void clearDanmaku() {
        items.clear();
    }

    private void scheduleFrame() {
        if (!frameScheduled && running) {
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }
    }

    private void updatePositions(float deltaMs) {
        float deltaSeconds = deltaMs / 1000f;
        Iterator<DanmakuItem> it = items.iterator();
        while (it.hasNext()) {
            DanmakuItem item = it.next();
            item.x -= SPEED_PX_PER_SEC * deltaSeconds;
            if (item.x + item.width < 0) {
                it.remove();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (DanmakuItem item : items) {
            if (item.width <= 0) {
                item.width = textPaint.measureText(item.text);
            }
            // Draw stroke outline with 4 offset draws for crisp border
            strokePaint.setColor(0xCC000000);
            canvas.drawText(item.text, item.x - 1, item.y, strokePaint);
            canvas.drawText(item.text, item.x + 1, item.y, strokePaint);
            canvas.drawText(item.text, item.x, item.y - 1, strokePaint);
            canvas.drawText(item.text, item.x, item.y + 1, strokePaint);
            // Draw main text on top
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

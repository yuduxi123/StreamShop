package com.bytedance.streamshop.ui.feed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import com.bytedance.streamshop.domain.model.Danmaku;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class VideoDanmakuView extends View {
    private static final int MAX_VISIBLE = 20;
    private static final int TEXT_SIZE = 28;
    private static final float SPEED = 2.5f;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<DanmakuItem> activeItems = new ArrayList<>();
    private final List<Danmaku> pendingDanmaku = new ArrayList<>();
    private int pendingIndex = 0;
    private long playbackPositionMs = 0;
    private long lastFrameTime = 0;
    private boolean playing = false;
    private boolean running = false;

    private final Runnable frameCallback = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            if (lastFrameTime > 0 && playing) {
                long deltaMs = now - lastFrameTime;
                playbackPositionMs += deltaMs;
                releasePendingDanmaku();
            }
            lastFrameTime = now;
            updatePositions();
            invalidate();
            handler.postDelayed(this, 16);
        }
    };

    public VideoDanmakuView(Context context) {
        super(context);
        init();
    }

    public VideoDanmakuView(Context context, AttributeSet attrs) {
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
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastFrameTime = System.currentTimeMillis();
        handler.post(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        handler.removeCallbacks(frameCallback);
    }

    public void loadDanmaku(List<Danmaku> list) {
        pendingDanmaku.clear();
        pendingDanmaku.addAll(list);
        pendingIndex = 0;
    }

    public void addRealtimeDanmaku(Danmaku danmaku) {
        // Only add to active items for immediate display.
        // Historical replay is handled by loadDanmaku() from backend.
        addActiveDanmaku(danmaku.getContent(), danmaku.getColor());
    }

    public void setPlaybackPosition(long positionMs) {
        playbackPositionMs = positionMs;
        releasePendingDanmaku();
    }

    public void play() {
        playing = true;
        lastFrameTime = System.currentTimeMillis();
    }

    public void pause() {
        playing = false;
    }

    public void seekTo(long positionMs) {
        playbackPositionMs = positionMs;
        activeItems.clear();
        pendingIndex = 0;
        releasePendingDanmaku();
    }

    public void release() {
        running = false;
        playing = false;
        handler.removeCallbacks(frameCallback);
        activeItems.clear();
        pendingDanmaku.clear();
        pendingIndex = 0;
    }

    private void releasePendingDanmaku() {
        while (pendingIndex < pendingDanmaku.size()) {
            Danmaku d = pendingDanmaku.get(pendingIndex);
            if (d.getTimestampMs() <= playbackPositionMs) {
                addActiveDanmaku(d.getContent(), d.getColor());
                pendingIndex++;
            } else {
                break;
            }
        }
    }

    private void addActiveDanmaku(String text, String colorHex) {
        if (activeItems.size() > MAX_VISIBLE) return;
        int color = 0xFFFFFFFF;

        float y = 40 + random.nextInt(Math.max(1, getHeight() - 80));
        DanmakuItem item = new DanmakuItem(text, getWidth(), y, color);
        item.width = textPaint.measureText(item.text);
        activeItems.add(item);
    }

    private void updatePositions() {
        Iterator<DanmakuItem> it = activeItems.iterator();
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
        for (DanmakuItem item : activeItems) {
            strokePaint.setColor(0xCC000000);
            canvas.drawText(item.text, item.x - 1, item.y, strokePaint);
            canvas.drawText(item.text, item.x + 1, item.y, strokePaint);
            canvas.drawText(item.text, item.x, item.y - 1, strokePaint);
            canvas.drawText(item.text, item.x, item.y + 1, strokePaint);
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
        }
    }
}

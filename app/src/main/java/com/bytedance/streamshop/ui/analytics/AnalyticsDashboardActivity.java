package com.bytedance.streamshop.ui.analytics;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bytedance.streamshop.R;
import com.bytedance.streamshop.data.remote.ApiService;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsDashboardActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar loadingView;
    private TextView errorView;
    private View contentView;
    private TextView kpiViews, kpiOrders, kpiRevenue;
    private LineChart trendChart;
    private LinearLayout funnelContainer;
    private HorizontalBarChart gmvChart;
    private HorizontalBarChart productsChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics_dashboard);

        swipeRefresh = findViewById(R.id.analytics_swipe);
        swipeRefresh.setOnRefreshListener(this::loadData);
        loadingView = findViewById(R.id.analytics_loading);
        errorView = findViewById(R.id.analytics_error);
        contentView = findViewById(R.id.analytics_content);
        kpiViews = findViewById(R.id.kpi_views);
        kpiOrders = findViewById(R.id.kpi_orders);
        kpiRevenue = findViewById(R.id.kpi_revenue);
        trendChart = findViewById(R.id.chart_trend);
        funnelContainer = findViewById(R.id.funnel_container);
        gmvChart = findViewById(R.id.chart_gmv);
        productsChart = findViewById(R.id.chart_products);

        ImageButton backBtn = findViewById(R.id.analytics_back);
        backBtn.setOnClickListener(v -> finish());

        loadData();
    }

    private void loadData() {
        loadingView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        contentView.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                Map<String, Object> data = new ApiService().getDashboardStats();
                runOnUiThread(() -> onDataLoaded(data));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    loadingView.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                    errorView.setText("加载失败: " + e.getMessage() + "\n点击重试");
                    errorView.setOnClickListener(v -> loadData());
                });
            }
        }).start();
    }

    @SuppressWarnings("unchecked")
    private void onDataLoaded(Map<String, Object> data) {
        swipeRefresh.setRefreshing(false);
        loadingView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);

        // KPI
        Map<String, Object> kpi = (Map<String, Object>) data.get("kpi");
        if (kpi != null) {
            kpiViews.setText(formatLargeNum(toDouble(kpi.get("totalViews"))));
            kpiOrders.setText(formatLargeNum(toDouble(kpi.get("totalOrders"))));
            kpiRevenue.setText("¥" + formatLargeNum(toDouble(kpi.get("totalRevenue"))));
        }

        // Trend chart
        List<Map<String, Object>> viewsTrend = (List<Map<String, Object>>) data.get("viewsTrend");
        if (viewsTrend != null) {
            setupTrendChart(viewsTrend);
        }

        // Funnel
        Map<String, Object> funnel = (Map<String, Object>) data.get("productFunnel");
        if (funnel != null) {
            setupFunnel(funnel);
        }

        // GMV ranking
        List<Map<String, Object>> gmvRanking = (List<Map<String, Object>>) data.get("gmvRanking");
        if (gmvRanking != null && !gmvRanking.isEmpty()) {
            setupGMVChart(gmvRanking);
        }

        // Top products
        List<Map<String, Object>> topProducts = (List<Map<String, Object>>) data.get("topProducts");
        if (topProducts != null && !topProducts.isEmpty()) {
            setupProductsChart(topProducts);
        }
    }

    private void setupTrendChart(List<Map<String, Object>> data) {
        List<Entry> viewsEntries = new ArrayList<>();
        List<Entry> likesEntries = new ArrayList<>();
        List<Entry> commentsEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> item = data.get(i);
            String date = (String) item.get("date");
            labels.add(date.substring(5)); // MM-DD

            viewsEntries.add(new Entry(i, toFloat(item.get("views"))));
            likesEntries.add(new Entry(i, toFloat(item.get("likes"))));
            commentsEntries.add(new Entry(i, toFloat(item.get("comments"))));
        }

        LineDataSet viewsSet = new LineDataSet(viewsEntries, "播放");
        viewsSet.setColor(Color.parseColor("#2196F3"));
        viewsSet.setCircleColor(Color.parseColor("#2196F3"));
        viewsSet.setLineWidth(2f);
        viewsSet.setCircleRadius(3f);
        viewsSet.setDrawValues(false);

        LineDataSet likesSet = new LineDataSet(likesEntries, "点赞");
        likesSet.setColor(Color.parseColor("#FF3B30"));
        likesSet.setCircleColor(Color.parseColor("#FF3B30"));
        likesSet.setLineWidth(2f);
        likesSet.setCircleRadius(3f);
        likesSet.setDrawValues(false);

        LineDataSet commentsSet = new LineDataSet(commentsEntries, "评论");
        commentsSet.setColor(Color.parseColor("#4CAF50"));
        commentsSet.setCircleColor(Color.parseColor("#4CAF50"));
        commentsSet.setLineWidth(2f);
        commentsSet.setCircleRadius(3f);
        commentsSet.setDrawValues(false);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(viewsSet);
        dataSets.add(likesSet);
        dataSets.add(commentsSet);

        LineData lineData = new LineData(dataSets);
        trendChart.setData(lineData);

        XAxis xAxis = trendChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setTextSize(10f);

        trendChart.getAxisRight().setEnabled(false);
        trendChart.getAxisLeft().setValueFormatter(new LargeValueFormatter());
        trendChart.getLegend().setTextSize(12f);
        trendChart.setDescription(null);
        trendChart.setTouchEnabled(true);
        trendChart.setDragEnabled(true);
        trendChart.setScaleEnabled(true);
        trendChart.animateX(800);
        trendChart.invalidate();
    }

    private void setupFunnel(Map<String, Object> funnel) {
        funnelContainer.removeAllViews();

        double exposure = toDouble(funnel.get("exposure"));
        double click = toDouble(funnel.get("click"));
        double addToCart = toDouble(funnel.get("addToCart"));
        double order = toDouble(funnel.get("order"));

        double max = Math.max(exposure, 1);
        addFunnelBar(funnelContainer, "曝光", exposure, exposure, max, "#2196F3");
        addFunnelBar(funnelContainer, "点击", click, (exposure > 0 ? click / exposure * 100 : 0), max, "#03A9F4");
        addFunnelBar(funnelContainer, "加购", addToCart, (exposure > 0 ? addToCart / exposure * 100 : 0), max, "#FF9800");
        addFunnelBar(funnelContainer, "下单", order, (exposure > 0 ? order / exposure * 100 : 0), max, "#4CAF50");
    }

    private void addFunnelBar(LinearLayout container, String label, double value,
                              double rate, double max, String color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 6, 0, 6);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.parseColor("#333333"));
        labelView.setTextSize(13f);
        labelView.setGravity(Gravity.END);
        labelView.setWidth(dpToPx(40));
        row.addView(labelView);

        LinearLayout barContainer = new LinearLayout(this);
        barContainer.setOrientation(LinearLayout.HORIZONTAL);
        int barWidth = (int) (max > 0 ? (value / max * (getScreenWidth() - dpToPx(130))) : 0);
        barWidth = Math.max(barWidth, dpToPx(4));

        View bar = new View(this);
        bar.setBackgroundColor(Color.parseColor(color));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                barWidth, dpToPx(24));
        barParams.setMargins(dpToPx(8), 0, 0, 0);
        bar.setLayoutParams(barParams);
        barContainer.addView(bar);

        TextView numView = new TextView(this);
        numView.setText(formatLargeNum(value));
        numView.setTextColor(Color.parseColor("#666666"));
        numView.setTextSize(12f);
        numView.setPadding(dpToPx(8), 0, 0, 0);
        barContainer.addView(numView);

        row.addView(barContainer);
        container.addView(row);

        TextView rateView = new TextView(this);
        rateView.setText(String.format(Locale.getDefault(), "%.1f%%", rate));
        rateView.setTextColor(Color.parseColor("#999999"));
        rateView.setTextSize(11f);
        rateView.setPadding(dpToPx(56), 0, 0, dpToPx(4));
        container.addView(rateView);
    }

    private void setupGMVChart(List<Map<String, Object>> data) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> item = data.get(i);
            String name = (String) item.get("name");
            double gmv = toDouble(item.get("gmv"));
            entries.add(new BarEntry(i, (float) gmv));
            labels.add(truncate(name, 10));
        }

        BarDataSet dataSet = new BarDataSet(entries, "GMV (¥)");
        dataSet.setColor(Color.parseColor("#FF9800"));
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new LargeValueFormatter());

        BarData barData = new BarData(dataSet);
        gmvChart.setData(barData);

        XAxis xAxis = gmvChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(10f);

        gmvChart.getAxisRight().setEnabled(false);
        gmvChart.getAxisLeft().setValueFormatter(new LargeValueFormatter());
        gmvChart.getLegend().setTextSize(12f);
        gmvChart.setDescription(null);
        gmvChart.setFitBars(true);
        gmvChart.animateY(800);
        gmvChart.invalidate();
    }

    private void setupProductsChart(List<Map<String, Object>> data) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int[] colors = new int[]{
                Color.parseColor("#FF3B30"),
                Color.parseColor("#FF9800"),
                Color.parseColor("#FFC107"),
                Color.parseColor("#4CAF50"),
                Color.parseColor("#2196F3"),
                Color.parseColor("#673AB7"),
                Color.parseColor("#009688"),
                Color.parseColor("#E91E63"),
                Color.parseColor("#3F51B5"),
                Color.parseColor("#795548"),
        };

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> item = data.get(i);
            String title = (String) item.get("title");
            double sales = toDouble(item.get("salesCount"));
            entries.add(new BarEntry(i, (float) sales));
            labels.add(truncate(title, 8));
        }

        BarDataSet dataSet = new BarDataSet(entries, "销量");
        dataSet.setColors(colors);
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new LargeValueFormatter());

        BarData barData = new BarData(dataSet);
        productsChart.setData(barData);

        XAxis xAxis = productsChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(10f);

        productsChart.getAxisRight().setEnabled(false);
        productsChart.getAxisLeft().setValueFormatter(new LargeValueFormatter());
        productsChart.getLegend().setTextSize(12f);
        productsChart.setDescription(null);
        productsChart.setFitBars(true);
        productsChart.animateY(800);
        productsChart.invalidate();
    }

    // ---- helpers ----

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (Exception ignored) {}
        }
        return 0;
    }

    private float toFloat(Object val) {
        return (float) toDouble(val);
    }

    private String formatLargeNum(double n) {
        if (n >= 1_0000_0000) return String.format(Locale.getDefault(), "%.1f亿", n / 1_0000_0000);
        if (n >= 10000) return String.format(Locale.getDefault(), "%.1f万", n / 10000);
        if (n >= 1000) return String.format(Locale.getDefault(), "%.1fk", n / 1000);
        return String.valueOf((long) n);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    static class LargeValueFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            if (value >= 1_0000_0000) return String.format(Locale.getDefault(), "%.1f亿", value / 1_0000_0000);
            if (value >= 10000) return String.format(Locale.getDefault(), "%.1f万", value / 10000);
            if (value >= 1000) return String.format(Locale.getDefault(), "%.1fk", value / 1000);
            return String.valueOf((int) value);
        }
    }
}

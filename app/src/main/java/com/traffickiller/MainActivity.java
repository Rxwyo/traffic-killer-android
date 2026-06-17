package com.traffickiller;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {

    private Spinner serverSpinner;
    private EditText customUrlInput;
    private EditText maxTrafficInput;
    private TextView tvThreadCount;
    private Button btnToggle;
    private TextView tvSpeed, tvTotal, tvTime;
    private Switch swFloatWindow;
    private View speedChart;

    private int threadCount = 8;
    private boolean isRunning = false;

    // 速度历史数据（用于画图表）
    private static final int CHART_POINTS = 60;
    private long[] speedHistory = new long[CHART_POINTS];
    private int speedHistoryIndex = 0;

    // 预设服务器列表 — 亚洲优先，推荐高速HTTP节点
    private static final String[] SERVER_NAMES = {
        "Vultr东京 [Asia·Japan]",
        "Linode新加坡 [Asia·SG]",
        "Linode东京 [Asia·Japan]",
        "DigitalOcean新加坡 [Asia·SG]",
        "SoftLayer香港 [Asia·HK]",
        "LeaseWeb香港 [Asia·HK]",
        "Speedtest.tele2.net [Europe]",
        "自定义地址..."
    };

    private static final String[] SERVER_URLS = {
        "http://hnd-jp-ping.vultr.com/vultr.com.100MB.bin",
        "http://speedtest-sin1.linode.com/100MB-sin.zip",
        "http://speedtest-tok1.linode.com/100MB-tok.zip",
        "http://speedtest-sgp1.digitalocean.com/100MB-sgp-test.bin",
        "http://speedtest-sin2.softlayer.com/downloads/speedtest/Speedtest_10MB.zip",
        "http://mirror.hk.leaseweb.net/speedtest/100MB.bin",
        "http://speedtest.tele2.net/100MB.zip",
        null  // custom
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
    }

    private void initViews() {
        serverSpinner = findViewById(R.id.serverSpinner);
        customUrlInput = findViewById(R.id.customUrlInput);
        maxTrafficInput = findViewById(R.id.maxTrafficInput);
        tvThreadCount = findViewById(R.id.tvThreadCount);
        btnToggle = findViewById(R.id.btnToggle);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvTotal = findViewById(R.id.tvTotal);
        tvTime = findViewById(R.id.tvTime);
        swFloatWindow = findViewById(R.id.swFloatWindow);
        speedChart = findViewById(R.id.speedChart);

        // 设置 Spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, SERVER_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serverSpinner.setAdapter(adapter);

        // Spinner 选择事件：自定义时显示输入框
        serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == SERVER_NAMES.length - 1) {
                    customUrlInput.setVisibility(View.VISIBLE);
                } else {
                    customUrlInput.setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        // 默认隐藏自定义输入框
        customUrlInput.setVisibility(View.GONE);

        // 初始绘制空白图表
        speedChart.post(() -> drawSpeedChart());

        // 恢复状态
        if (DownloadService.isRunning()) {
            isRunning = true;
            btnToggle.setText("停止测试");
            btnToggle.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_light, null));
            startUiUpdater();
        }
    }

    private void setupListeners() {
        findViewById(R.id.btnThreadMinus).setOnClickListener(v -> changeThread(-1));
        findViewById(R.id.btnThreadPlus).setOnClickListener(v -> changeThread(1));
        btnToggle.setOnClickListener(v -> toggleRun());
        swFloatWindow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                requestFloatPermission();
            } else {
                DownloadService.hideFloatWindow(this);
            }
        });
    }

    private void changeThread(int delta) {
        threadCount = Math.max(1, Math.min(64, threadCount + delta));
        tvThreadCount.setText(String.valueOf(threadCount));
    }

    private String getSelectedUrl() {
        int pos = serverSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= SERVER_URLS.length) return null;

        if (SERVER_URLS[pos] == null) {
            // 自定义地址
            String url = customUrlInput.getText().toString().trim();
            if (url.length() > 0 && url.startsWith("http")) return url;
            Toast.makeText(this, "请输入有效的下载地址", Toast.LENGTH_SHORT).show();
            return null;
        }
        return SERVER_URLS[pos];
    }

    private long getMaxTrafficBytes() {
        try {
            double gb = Double.parseDouble(maxTrafficInput.getText().toString());
            return (long)(gb * 1073741824L);
        } catch (Exception e) {
            return 0;
        }
    }

    private void toggleRun() {
        if (isRunning) {
            stopDownload();
        } else {
            startDownload();
        }
    }

    private void startDownload() {
        String url = getSelectedUrl();
        if (url == null) return;

        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra("url", url);
        intent.putExtra("threads", threadCount);
        intent.putExtra("maxTraffic", getMaxTrafficBytes());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        isRunning = true;
        btnToggle.setText("停止测试");
        btnToggle.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_light, null));

        // 重置速度历史
        speedHistory = new long[CHART_POINTS];
        speedHistoryIndex = 0;

        startUiUpdater();
    }

    private void stopDownload() {
        stopService(new Intent(this, DownloadService.class));
        isRunning = false;
        btnToggle.setText("开始测试");
        btnToggle.setBackgroundTintList(getResources().getColorStateList(0xFF7C3AED, null));
        stopUiUpdater();
    }

    private java.util.Timer uiTimer;
    private void startUiUpdater() {
        stopUiUpdater();
        uiTimer = new java.util.Timer();
        uiTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> updateUI());
            }
        }, 0, 500);
    }

    private void stopUiUpdater() {
        if (uiTimer != null) {
            uiTimer.cancel();
            uiTimer = null;
        }
    }

    private void updateUI() {
        if (!DownloadService.isRunning()) return;

        long total = DownloadService.getTotalBytes();
        long speed = DownloadService.getCurrentSpeed();
        long elapsed = DownloadService.getElapsedTime();

        tvSpeed.setText(formatSpeed(speed));
        tvTotal.setText(formatSize(total));
        tvTime.setText(formatTime(elapsed));

        // 记录速度数据并画图
        speedHistory[speedHistoryIndex % CHART_POINTS] = speed;
        speedHistoryIndex++;
        drawSpeedChart();
    }

    /**
     * 在 speedChart View 上绘制速度折线图
     * speedChart 是一个 View，通过 setBackground + Canvas 绘制
     */
    private void drawSpeedChart() {
        View chart = speedChart;
        if (chart == null || chart.getWidth() == 0) return;

        int w = chart.getWidth();
        int h = chart.getHeight();

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        // 背景
        canvas.drawColor(0xFFF8FAFC);

        // 网格线
        android.graphics.Paint gridPaint = new android.graphics.Paint();
        gridPaint.setColor(0xFFE2E8F0);
        gridPaint.setStyle(android.graphics.Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1);

        // 水平网格线（5条）
        for (int i = 1; i < 5; i++) {
            float y = h * i / 5f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }
        // 垂直网格线（6条）
        for (int i = 1; i < 6; i++) {
            float x = w * i / 6f;
            canvas.drawLine(x, 0, x, h, gridPaint);
        }

        // Y轴标签
        long maxSpeed = 0;
        int count = Math.min(speedHistoryIndex, CHART_POINTS);
        for (int i = 0; i < count; i++) {
            if (speedHistory[i] > maxSpeed) maxSpeed = speedHistory[i];
        }
        if (maxSpeed == 0) maxSpeed = 1; // 避免除以0

        android.graphics.Paint labelPaint = new android.graphics.Paint();
        labelPaint.setColor(0xFF94A3B8);
        labelPaint.setTextSize(24);
        labelPaint.setAntiAlias(true);

        // 顶部标签
        canvas.drawText(formatSpeed(maxSpeed), 4, 28, labelPaint);
        // 底部标签
        canvas.drawText("0 B/s", 4, h - 4, labelPaint);

        // 绘制折线
        if (count > 1) {
            int padding = 8;
            float chartW = w - padding * 2;
            float chartH = h - 50;

            // 渐变填充区域
            android.graphics.Paint fillPaint = new android.graphics.Paint();
            fillPaint.setAntiAlias(true);
            fillPaint.setStyle(android.graphics.Paint.Style.FILL);

            // 构建路径
            android.graphics.Path linePath = new android.graphics.Path();
            android.graphics.Path fillPath = new android.graphics.Path();

            int startIdx = speedHistoryIndex >= CHART_POINTS ? speedHistoryIndex - CHART_POINTS : 0;
            float baseY = h - 20;

            for (int i = 0; i < count; i++) {
                int dataIdx = (startIdx + i) % CHART_POINTS;
                float x = padding + (chartW * i / (float)(CHART_POINTS - 1));
                float y = baseY - (float)(speedHistory[dataIdx] / (double)maxSpeed * chartH);

                if (i == 0) {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, y);
                } else {
                    linePath.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
            }

            // 填充区域（闭合到底部）
            float lastX = padding + (chartW * (count - 1) / (float)(CHART_POINTS - 1));
            fillPath.lineTo(lastX, baseY);
            fillPath.lineTo(padding, baseY);
            fillPath.close();

            // 使用渐变色填充
            android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(0, 0, 0, baseY,
                0x407C3AED, 0x087C3AED, android.graphics.Shader.TileMode.CLAMP);
            fillPaint.setShader(gradient);
            canvas.drawPath(fillPath, fillPaint);

            // 画线条
            android.graphics.Paint linePaint = new android.graphics.Paint();
            linePaint.setColor(0xFF7C3AED);
            linePaint.setStyle(android.graphics.Paint.Style.STROKE);
            linePaint.setStrokeWidth(3);
            linePaint.setAntiAlias(true);
            linePaint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            linePaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            canvas.drawPath(linePath, linePaint);

            // 最后一个点画圆点
            if (count > 0) {
                int lastDataIdx = (startIdx + count - 1) % CHART_POINTS;
                float cx = padding + (chartW * (count - 1) / (float)(CHART_POINTS - 1));
                float cy = baseY - (float)(speedHistory[lastDataIdx] / (double)maxSpeed * chartH);

                android.graphics.Paint dotPaint = new android.graphics.Paint();
                dotPaint.setColor(0xFFFFFFFF);
                dotPaint.setStyle(android.graphics.Paint.Style.FILL);
                dotPaint.setAntiAlias(true);
                canvas.drawCircle(cx, cy, 6, dotPaint);
                dotPaint.setColor(0xFF7C3AED);
                canvas.drawCircle(cx, cy, 4, dotPaint);
            }
        } else {
            // 无数据时显示提示
            android.graphics.Paint hintPaint = new android.graphics.Paint();
            hintPaint.setColor(0xFFCBD5E1);
            hintPaint.setTextSize(32);
            hintPaint.setAntiAlias(true);
            hintPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            canvas.drawText("开始测试后将显示速度图表", w / 2f, h / 2f, hintPaint);
        }

        // 设置背景
        chart.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap));
    }

    private String formatSpeed(long bytesPerSec) {
        if (bytesPerSec < 1024) return bytesPerSec + " B/s";
        if (bytesPerSec < 1048576) return String.format("%.1f KB/s", bytesPerSec / 1024.0);
        return String.format("%.1f MB/s", bytesPerSec / 1048576.0);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }

    private String formatTime(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        if (h > 0) return String.format("%02d:%02d:%02d", h, m % 60, s % 60);
        return String.format("%02d:%02d", m, s);
    }

    private void requestFloatPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1001);
        } else {
            DownloadService.showFloatWindow(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                DownloadService.showFloatWindow(this);
            } else {
                swFloatWindow.setChecked(false);
                Toast.makeText(this, "需要悬浮窗权限才能显示", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        swFloatWindow.setOnCheckedChangeListener(null);
        swFloatWindow.setChecked(DownloadService.isFloatShowing());
        setupListeners();

        if (DownloadService.isRunning()) {
            if (!isRunning) {
                isRunning = true;
                btnToggle.setText("停止测试");
                btnToggle.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_light, null));
                startUiUpdater();
            }
            updateUI();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUiUpdater();
    }
}

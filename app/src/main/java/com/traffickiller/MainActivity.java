package com.traffickiller;

import android.app.Activity;
import android.content.Intent;
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

    private int threadCount = 8;
    private boolean isRunning = false;

    // 预设服务器列表
    private static final String[] SERVER_NAMES = {
        "百度CDN [高速]",
        "七牛云CDN [高速]",
        "阿里CDN",
        "腾讯CDN",
        "字节跳动",
        "京东",
        "网易",
        "小米",
        "Cloudflare Speed [Global]",
        "Cachefly Test [Global]"
    };

    private static final String[] SERVER_URLS = {
        "https://issuecdn.baidupcs.com/issue/netdisk/apk/BaiduNetdiskSetup_wap_share.apk",
        "https://kodo-toolbox.qiniu.com/kodo-browser-Linux-x64-v1.0.17.zip",
        "https://img.alicdn.com/imgextra/i1/O1CN01xA4P9S1JsW2WEg0e1_!!6000000001084-2-tps-2880-560.png",
        "https://game.gtimg.cn/images/nz/web202106/index/bc_part1.gif",
        "https://lf9-cdn-tos.bytecdntp.com/cdn/yuntu-index/1.0.4/case/maiteng/detailbg.png",
        "https://img10.360buyimg.com/live/jfs/t1/128947/12/26918/1361527/6260e71bE0ee85af5/ecaa17ea8dd3dddb.jpg",
        "https://pic-bucket.ws.126.net/photo/0003/2022-04-24/H5N2082C00AJ0003NOS.jpg",
        "https://cnbj0.fds.api.xiaomi.com/b2c-data-mishop/9b9d95e1ece27d5ec75205e5fe719ba5.apk",
        "https://speed.cloudflare.com/__down?bytes=1073741824",
        "https://cachefly.cachefly.net/100mb.test"
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

        // 设置 Spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, SERVER_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serverSpinner.setAdapter(adapter);

        // 恢复状态
        if (DownloadService.isRunning()) {
            isRunning = true;
            btnToggle.setText("停止测试");
            btnToggle.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_light, null));
        }
    }

    private void setupListeners() {
        // 线程数加减
        findViewById(R.id.btnThreadMinus).setOnClickListener(v -> changeThread(-1));
        findViewById(R.id.btnThreadPlus).setOnClickListener(v -> changeThread(1));

        // 开始/停止按钮
        btnToggle.setOnClickListener(v -> toggleRun());

        // 悬浮窗开关
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
        if (pos == 0) { // 自定义地址
            String url = customUrlInput.getText().toString().trim();
            return (url.length() > 0 && url.startsWith("http")) ? url : null;
        }
        return SERVER_URLS[pos];
    }

    private long getMaxTrafficBytes() {
        try {
            double gb = Double.parseDouble(maxTrafficInput.getText().toString());
            return (long)(gb * 1073741824L);
        } catch (Exception e) {
            return 0; // 不限制
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
        if (url == null) {
            Toast.makeText(this, "请输入有效的下载地址", Toast.LENGTH_SHORT).show();
            return;
        }

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

        // 启动 UI 更新定时器
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
        if (!isRunning && !DownloadService.isRunning()) return;

        long total = DownloadService.getTotalBytes();
        long speed = DownloadService.getCurrentSpeed();
        long elapsed = DownloadService.getElapsedTime();

        tvSpeed.setText(formatSpeed(speed));
        tvTotal.setText(formatSize(total));
        tvTime.setText(formatTime(elapsed));
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
        // 更新悬浮窗开关状态
        swFloatWindow.setOnCheckedChangeListener(null); // 临时移除监听
        swFloatWindow.setChecked(DownloadService.isFloatShowing());
        setupListeners(); // 重新添加

        // 如果服务在运行，更新 UI
        if (DownloadService.isRunning()) {
            updateUI();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUiUpdater();
    }
}

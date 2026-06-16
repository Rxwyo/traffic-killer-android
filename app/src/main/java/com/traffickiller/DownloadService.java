package com.traffickiller;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadService extends Service {

    // 静态状态
    private static boolean sRunning = false;
    private static AtomicLong sTotalBytes = new AtomicLong(0);
    private static AtomicLong sCurrentSpeed = new AtomicLong(0);
    private static long sStartTime = 0;
    private static int sThreadCount = 8;
    private static long sMaxTraffic = 0;

    // 悬浮窗
    private static WindowManager sWindowManager;
    private static View sFloatView;
    private static boolean sFloatShowing = false;

    // 下载线程
    private List<Thread> downloadThreads = new ArrayList<>();
    private Handler uiHandler;
    private Runnable speedCalcRunnable;

    // 通知
    private static final int NOTIFICATION_ID = 1001;
    private NotificationManager notificationManager;

    // ===== 公共静态方法 =====

    public static boolean isRunning() { return sRunning; }
    public static long getTotalBytes() { return sTotalBytes.get(); }
    public static long getCurrentSpeed() { return sCurrentSpeed.get(); }
    public static long getElapsedTime() { return sRunning ? System.currentTimeMillis() - sStartTime : 0; }
    public static boolean isFloatShowing() { return sFloatShowing; }

    public static void showFloatWindow(Context context) {
        if (sFloatView != null) return;
        try {
            sWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            LayoutInflater inflater = LayoutInflater.from(context);
            sFloatView = inflater.inflate(R.layout.layout_float, null);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 200;

            // 拖动支持
            View titleBar = sFloatView.findViewById(R.id.floatTitleBar);
            setupDrag(titleBar, params);

            // 关闭按钮
            sFloatView.findViewById(R.id.btnFloatClose).setOnClickListener(v -> hideFloatWindow(context));

            sWindowManager.addView(sFloatView, params);
            sFloatShowing = true;

            // 启动悬浮窗更新
            startFloatUpdater(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void hideFloatWindow(Context context) {
        if (sFloatView != null && sWindowManager != null) {
            try { sWindowManager.removeView(sFloatView); } catch (Exception ignored) {}
            sFloatView = null;
            sFloatShowing = false;
        }
    }

    private static Timer floatTimer;
    private static void startFloatUpdater(final Context context) {
        stopFloatUpdater();
        floatTimer = new Timer();
        floatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (sFloatView == null) { cancel(); return; }
                // 在主线程更新 UI
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (sFloatView == null) return;
                    TextView tvSpeed = sFloatView.findViewById(R.id.tvFloatSpeed);
                    TextView tvTotal = sFloatView.findViewById(R.id.tvFloatTotal);
                    if (tvSpeed != null)
                        tvSpeed.setText("↓ " + formatSpeedStatic(sCurrentSpeed.get()));
                    if (tvTotal != null)
                        tvTotal.setText("总流量: " + formatSizeStatic(sTotalBytes.get()));
                });
            }
        }, 0, 500);
    }

    private static void stopFloatUpdater() {
        if (floatTimer != null) { floatTimer.cancel(); floatTimer = null; }
    }

    private static String formatSpeedStatic(long bps) {
        if (bps < 1024) return bps + " B/s";
        if (bps < 1048576) return String.format("%.1f KB/s", bps / 1024.0);
        return String.format("%.1f MB/s", bps / 1048576.0);
    }

    private static String formatSizeStatic(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }

    private static void setupDrag(View view, WindowManager.LayoutParams params) {
        // 简化：通过 OnTouchListener 实现拖动（略）
    }

    // ===== Service 生命周期 =====

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        uiHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url = intent.getStringExtra("url");
        sThreadCount = intent.getIntExtra("threads", 8);
        sMaxTraffic = intent.getLongExtra("maxTraffic", 0);

        startForeground(NOTIFICATION_ID, buildNotification("准备中...", "正在连接服务器"));

        sRunning = true;
        sTotalBytes.set(0);
        sCurrentSpeed.set(0);
        sStartTime = System.currentTimeMillis();

        startDownload(url);
        startSpeedCalculator();

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sRunning = false;
        stopDownloadThreads();
        if (speedCalcRunnable != null) uiHandler.removeCallbacks(speedCalcRunnable);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopFloatUpdater();
    }

    // ===== 通知 =====

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "download_channel",
                "下载服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("后台下载服务");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String content) {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, "download_channel")
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String content) {
        notificationManager.notify(NOTIFICATION_ID,
            buildNotification("流量杀手 - 运行中", content));
    }

    // ===== 下载核心 =====

    private long lastSpeedBytes = 0;
    private long lastSpeedTime = 0;

    private void startDownload(final String url) {
        for (int i = 0; i < sThreadCount; i++) {
            final int idx = i;
            Thread t = new Thread(() -> downloadLoop(url, idx), "DL-" + idx);
            downloadThreads.add(t);
            t.start();
        }
    }

    private void downloadLoop(String url, int threadIdx) {
        while (sRunning) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Connection", "keep-alive");
                // 禁用缓存确保每次下载真实数据
                conn.setUseCaches(false);
                conn.setRequestProperty("Cache-Control", "no-cache");

                int responseCode = conn.getResponseCode();
                if (responseCode != 200 && responseCode != 206) {
                    conn.disconnect();
                    Thread.sleep(1000);
                    continue;
                }

                java.io.InputStream in = conn.getInputStream();
                byte[] buffer = new byte[65536]; // 64KB buffer for higher throughput
                int bytesRead;
                while (sRunning && (bytesRead = in.read(buffer)) != -1) {
                    sTotalBytes.addAndGet(bytesRead);

                    // 检查流量上限
                    if (sMaxTraffic > 0 && sTotalBytes.get() >= sMaxTraffic) {
                        sRunning = false;
                        break;
                    }
                }
                in.close();
                conn.disconnect();

                // 文件下载完毕，立即重新开始
                if (sRunning) {
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
                if (sRunning) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void stopDownloadThreads() {
        sRunning = false;
        for (Thread t : downloadThreads) {
            try { t.interrupt(); } catch (Exception ignored) {}
        }
        downloadThreads.clear();
    }

    private void startSpeedCalculator() {
        lastSpeedBytes = sTotalBytes.get();
        lastSpeedTime = System.currentTimeMillis();

        speedCalcRunnable = () -> {
            if (!sRunning) return;
            long now = System.currentTimeMillis();
            long currentBytes = sTotalBytes.get();
            long elapsed = now - lastSpeedTime;

            if (elapsed > 0) {
                long speed = (currentBytes - lastSpeedBytes) * 1000 / elapsed;
                sCurrentSpeed.set(speed);
            }

            lastSpeedBytes = currentBytes;
            lastSpeedTime = now;

            updateNotification(formatSizeStatic(sTotalBytes.get()) + " | " + formatSpeedStatic(sCurrentSpeed.get()));

            uiHandler.postDelayed(speedCalcRunnable, 500);
        };
        uiHandler.postDelayed(speedCalcRunnable, 500);
    }
}

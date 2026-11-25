package com.instalarm.app;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InstalarmAccessibilityService extends AccessibilityService {

    private static final String TAG = "InstalarmAccessibility";
    private static InstalarmAccessibilityService instance;

    // Mission State
    private static volatile boolean sMissionActive = false;
    private static volatile int sTargetReels = 10;
    private static volatile int sWatchedReels = 0;
    private static volatile long sCurrentAlarmId = -1;
    private static volatile long sNextReelAllowedTime = 0;
    private static volatile long sLastCountTime = 0;

    private SharedPreferences prefs;
    private long lastBlockToastTime = 0;

    // Daily Instagram Usage & Lock Tracking
    private boolean isInInstagram = false;
    private final Handler usageHandler = new Handler(Looper.getMainLooper());
    private final Runnable usageTicker = new Runnable() {
        @Override
        public void run() {
            if (!isInInstagram || prefs == null) return;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean screenOn = pm == null || pm.isInteractive();
            if (screenOn && !sMissionActive) {
                int usedSeconds = incrementTodayUsageSecond(prefs);
                if (isInstagramBlocked(prefs, usedSeconds)) {
                    blockInstagramNow();
                    return;
                }
            }
            usageHandler.postDelayed(this, 1000);
        }
    };

    public static int getTodayUsageSeconds(SharedPreferences prefs) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String savedDate = prefs.getString("insta_usage_date", "");
        if (!today.equals(savedDate)) {
            prefs.edit().putString("insta_usage_date", today).putInt("insta_usage_seconds", 0).apply();
            return 0;
        }
        return prefs.getInt("insta_usage_seconds", 0);
    }

    private static int incrementTodayUsageSecond(SharedPreferences prefs) {
        int current = getTodayUsageSeconds(prefs) + 1;
        prefs.edit().putInt("insta_usage_seconds", current).apply();
        return current;
    }

    public static boolean isInstagramBlocked(SharedPreferences prefs, int usedSeconds) {
        if (prefs.getBoolean("insta_lock_now", false)) {
            return true;
        }
        int limitMins = prefs.getInt("insta_daily_limit_mins", 0);
        return limitMins > 0 && usedSeconds >= limitMins * 60;
    }

    private void blockInstagramNow() {
        isInInstagram = false;
        usageHandler.removeCallbacks(usageTicker);
        performGlobalAction(GLOBAL_ACTION_HOME);
        boolean lockedNow = prefs.getBoolean("insta_lock_now", false);
        notifyBlockAction(lockedNow
                ? "Instagram is locked in Instalarm."
                : "Daily Instagram limit reached. Locked until tomorrow.");
    }

    // Overlay Window Elements
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams overlayParams;
    private TextView tvOverlayCount;
    private TextView tvOverlayPercent;
    private TextView tvOverlayStatus;
    private ProgressBar pbOverlayProgress;
    private CountDownTimer countdownTimer;

    public static boolean isServiceRunning() {
        return instance != null;
    }

    public static boolean isMissionActive() {
        return sMissionActive;
    }

    public static void startReelsMission(Context context, int targetReels, long alarmId) {
        sMissionActive = true;
        sTargetReels = targetReels > 0 ? targetReels : 10;
        sWatchedReels = 0;
        sCurrentAlarmId = alarmId;
        sLastCountTime = 0;
        sNextReelAllowedTime = System.currentTimeMillis() + 5000L;

        // Mute loud alarm initially so reel audio is clear
        AlarmService.muteSoundAndVibration();

        if (instance != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                instance.showOverlay();
                instance.startCountdownTicker();
            });
        }

        // Launch Native Instagram App directly to Reels
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/reels/"));
        intent.setPackage("com.instagram.android");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback to launcher intent
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.instagram.android");
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("alarm_settings", Context.MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "InstalarmAccessibilityService connected and ready.");
        if (sMissionActive) {
            showOverlay();
            startCountdownTicker();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || prefs == null) return;

        CharSequence packageName = event.getPackageName();
        CharSequence className = event.getClassName();

        String pkg = packageName != null ? packageName.toString().toLowerCase() : "";
        String cls = className != null ? className.toString().toLowerCase() : "";
        int eventType = event.getEventType();

        // 1. REELS MISSION TRACKING (Native Instagram App)
        if (sMissionActive) {
            handleReelsMissionEvent(eventType, pkg);
        } else {
            // 1b. DAILY INSTAGRAM LIMIT & LOCK ENFORCEMENT
            handleInstagramUsageAndLock(eventType, pkg);
        }

        // 2. DETECT POWER-OFF / SHUTDOWN DIALOGS
        boolean preventPowerOff = prefs.getBoolean("prevent_power_off", false);
        if (preventPowerOff) {
            boolean isPowerMenu = cls.contains("globalactions") ||
                                  cls.contains("powerdialog") ||
                                  cls.contains("shutdown") ||
                                  cls.contains("poweroff") ||
                                  (pkg.contains("systemui") && cls.contains("actionsdialog"));

            if (isPowerMenu) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                notifyBlockAction("Power-off action blocked by Instalarm!");

                int blocks = prefs.getInt("power_off_block_count", 0) + 1;
                prefs.edit().putInt("power_off_block_count", blocks).apply();
                return;
            }
        }

        // 3. DETECT UNINSTALL / FORCE STOP ATTEMPTS
        boolean preventUninstall = prefs.getBoolean("prevent_uninstall", false);
        if (preventUninstall) {
            boolean isPackageManagement = pkg.contains("packageinstaller") ||
                                          (pkg.contains("settings") && cls.contains("appdetail"));

            if (isPackageManagement) {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText("Instalarm");
                    if (nodes != null && !nodes.isEmpty()) {
                        List<AccessibilityNodeInfo> uninstallButtons = rootNode.findAccessibilityNodeInfosByText("Uninstall");
                        if (uninstallButtons == null || uninstallButtons.isEmpty()) {
                            uninstallButtons = rootNode.findAccessibilityNodeInfosByText("Force stop");
                        }

                        if (uninstallButtons != null && !uninstallButtons.isEmpty()) {
                            performGlobalAction(GLOBAL_ACTION_BACK);
                            notifyBlockAction("App uninstall is locked by Instalarm!");

                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                        }
                    }
                }
            }
        }
    }

    private void handleInstagramUsageAndLock(int eventType, String pkg) {
        if (pkg.equals("com.instagram.android")) {
            int usedSeconds = getTodayUsageSeconds(prefs);
            if (isInstagramBlocked(prefs, usedSeconds)) {
                blockInstagramNow();
                return;
            }
            if (!isInInstagram) {
                isInInstagram = true;
                usageHandler.removeCallbacks(usageTicker);
                usageHandler.postDelayed(usageTicker, 1000);
            }
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && !pkg.isEmpty()
                && !pkg.contains("systemui")
                && !pkg.contains("inputmethod")) {
            if (isInInstagram) {
                isInInstagram = false;
                usageHandler.removeCallbacks(usageTicker);
            }
        }
    }

    private synchronized void handleReelsMissionEvent(int eventType, String pkg) {
        // App switch detection
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!pkg.contains("instagram") && !pkg.contains("instalarm")) {
                // User navigated away from Instagram! Unmute alarm loudly
                AlarmService.unmuteSoundAndVibration();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (tvOverlayStatus != null) {
                        tvOverlayStatus.setText("🚨 Alarm Ringing! Return to Instagram!");
                        tvOverlayStatus.setTextColor(Color.parseColor("#FF5252"));
                    }
                });
            } else if (pkg.contains("instagram")) {
                // User is in Instagram, soften alarm so reel audio is audible
                AlarmService.muteSoundAndVibration();
            }
        }

        // Scroll event detection in Instagram
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && pkg.contains("instagram")) {
            long now = System.currentTimeMillis();
            if (now >= sNextReelAllowedTime) {
                sNextReelAllowedTime = now + 5000L; // 5-second anti-cheat gap
                sWatchedReels++;
                sLastCountTime = now;

                new Handler(Looper.getMainLooper()).post(() -> {
                    updateOverlayUI();
                    triggerHapticFeedback();

                    if (sWatchedReels >= sTargetReels) {
                        completeMission();
                    } else {
                        startCountdownTicker();
                    }
                });
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showOverlay() {
        if (overlayView != null) return;

        try {
            LayoutInflater inflater = LayoutInflater.from(this);
            overlayView = inflater.inflate(R.layout.layout_reels_mission_overlay, null);

            tvOverlayCount = overlayView.findViewById(R.id.tvOverlayCount);
            tvOverlayPercent = overlayView.findViewById(R.id.tvOverlayPercent);
            tvOverlayStatus = overlayView.findViewById(R.id.tvOverlayStatus);
            pbOverlayProgress = overlayView.findViewById(R.id.pbOverlayProgress);

            pbOverlayProgress.setMax(sTargetReels);
            updateOverlayUI();

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );

            overlayParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            overlayParams.y = 120; // Position below status bar

            // Draggable touch listener
            overlayView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = overlayParams.x;
                            initialY = overlayParams.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            overlayParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                            overlayParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                            if (windowManager != null && overlayView != null) {
                                windowManager.updateViewLayout(overlayView, overlayParams);
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(overlayView, overlayParams);
        } catch (Exception e) {
            Log.e(TAG, "Error adding overlay: " + e.getMessage(), e);
        }
    }

    private void updateOverlayUI() {
        if (overlayView == null) return;

        tvOverlayCount.setText(String.format(Locale.getDefault(), "Watched: %d / %d Reels", sWatchedReels, sTargetReels));
        int percent = (int) (((float) sWatchedReels / (float) sTargetReels) * 100);
        if (percent > 100) percent = 100;
        tvOverlayPercent.setText(percent + "%");
        pbOverlayProgress.setProgress(sWatchedReels);
    }

    private void startCountdownTicker() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }

        countdownTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (tvOverlayStatus != null && sMissionActive) {
                    long sec = (millisUntilFinished / 1000) + 1;
                    tvOverlayStatus.setText(String.format(Locale.getDefault(), "⏳ Watch current reel (%ds)...", sec));
                    tvOverlayStatus.setTextColor(Color.parseColor("#E2E8F0"));
                }
            }

            @Override
            public void onFinish() {
                if (tvOverlayStatus != null && sMissionActive) {
                    tvOverlayStatus.setText("✅ Ready! Scroll down for +1 ⬇️");
                    tvOverlayStatus.setTextColor(Color.parseColor("#00E676"));
                }
            }
        }.start();
    }

    private void triggerHapticFeedback() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(80);
            }
        } catch (Exception ignored) {}
    }

    private void completeMission() {
        sMissionActive = false;
        if (countdownTimer != null) countdownTimer.cancel();

        // Stop alarm sound and vibration completely
        AlarmService.stopAlarm(getApplicationContext());

        // Update database if repeating or one-time
        if (sCurrentAlarmId > 0) {
            AlarmDatabase db = AlarmDatabase.getInstance(getApplicationContext());
            AlarmModel alarm = db.getAlarmById(sCurrentAlarmId);
            if (alarm != null) {
                if ("Once".equalsIgnoreCase(alarm.getDays()) || alarm.getDays().isEmpty()) {
                    alarm.setEnabled(false);
                    db.updateAlarm(alarm);
                } else {
                    AlarmScheduler.scheduleAlarm(getApplicationContext(), alarm);
                }
            }
        }

        // Show victory status in HUD
        if (tvOverlayStatus != null) {
            tvOverlayStatus.setText("🎉 10/10 REELS COMPLETE! ALARM OFF!");
            tvOverlayStatus.setTextColor(Color.parseColor("#FFD700"));
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            hideOverlay();

            // Return user to Instalarm with victory celebration
            Intent victoryIntent = new Intent(this, MainActivity.class);
            victoryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            victoryIntent.putExtra("mission_accomplished", true);
            startActivity(victoryIntent);
        }, 1800);
    }

    private void hideOverlay() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    private void notifyBlockAction(String message) {
        long now = System.currentTimeMillis();
        if (now - lastBlockToastTime > 2500) {
            lastBlockToastTime = now;
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(getApplicationContext(), "🛡️ " + message, Toast.LENGTH_SHORT).show()
            );
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        usageHandler.removeCallbacks(usageTicker);
        hideOverlay();
        instance = null;
    }
}

package com.instalarm.app;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
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
import android.view.accessibility.AccessibilityWindowInfo;
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
    private static volatile String sAlarmLabel = "Alarm";
    private static volatile String sSoundUri = "";
    private static volatile boolean sVibrate = true;
    private static volatile int sSnoozeDuration = 5;
    private static volatile String sRemainingTasks = "";
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
        startReelsMission(context, targetReels, alarmId, "Alarm", "", true, 5, "", true);
    }

    public static void startReelsMission(Context context, int targetReels, long alarmId,
                                         String label, String soundUri, boolean vibrate,
                                         int snoozeDuration, String remainingTasks) {
        startReelsMission(context, targetReels, alarmId, label, soundUri, vibrate, snoozeDuration, remainingTasks, false);
    }

    public static void startReelsMission(Context context, int targetReels, long alarmId,
                                         String label, String soundUri, boolean vibrate,
                                         int snoozeDuration, String remainingTasks, boolean openInstagramNow) {
        sMissionActive = true;
        sTargetReels = targetReels > 0 ? targetReels : 10;
        sWatchedReels = 0;
        sCurrentAlarmId = alarmId;
        sAlarmLabel = label != null ? label : "Alarm";
        sSoundUri = soundUri != null ? soundUri : "";
        sVibrate = vibrate;
        sSnoozeDuration = snoozeDuration > 0 ? snoozeDuration : 5;
        sRemainingTasks = remainingTasks != null ? remainingTasks : "";
        sLastCountTime = 0;
        sNextReelAllowedTime = System.currentTimeMillis() + 5000L;

        if (instance != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                instance.showOverlay();
                instance.startAudioMonitor();
                if (openInstagramNow) {
                    instance.startCountdownTicker();
                } else if (instance.tvOverlayStatus != null) {
                    instance.tvOverlayStatus.setText("Tap to open Instagram");
                    instance.tvOverlayStatus.setTextColor(Color.parseColor("#38BDF8"));
                }
            });
        }

        if (openInstagramNow) {
            openInstagramReels(context);
        }
    }

    public static void openInstagramReels(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/reels/"));
        intent.setPackage("com.instagram.android");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.instagram.android");
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            }
        }
    }

    private final Handler audioMonitorHandler = new Handler(Looper.getMainLooper());
    private long audioStopTimestamp = 0;
    private long currentSilenceWaitMs = 5000L;
    private final Runnable audioMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sMissionActive) return;
            evaluateInstagramVsAlarmAudio();
            audioMonitorHandler.postDelayed(this, 300);
        }
    };

    private void evaluateInstagramVsAlarmAudio() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;

        int mediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean mediaActive = am.isMusicActive();
        if (!mediaActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (AudioPlaybackConfiguration cfg : am.getActivePlaybackConfigurations()) {
                if (cfg.getAudioAttributes().getUsage() == AudioAttributes.USAGE_MEDIA) {
                    mediaActive = true;
                    break;
                }
            }
        }

        boolean mediaChannelPlaying = mediaVol > 0 && mediaActive;
        long now = System.currentTimeMillis();

        if (mediaChannelPlaying) {
            // Media channel is actively producing sound: mute alarm immediately
            AlarmService.muteSoundAndVibration();
            if (audioStopTimestamp > 0) {
                // Sound returned before grace window expired: reset timer to 2s for next pause
                audioStopTimestamp = 0;
                currentSilenceWaitMs = 2000L;
            }
        } else {
            // Media channel is silent or volume is 0: wait 5s (or 2s after quick reset) before ringing
            if (audioStopTimestamp == 0) {
                audioStopTimestamp = now;
            } else if (now - audioStopTimestamp >= currentSilenceWaitMs) {
                AlarmService.unmuteSoundAndVibration();
            }
        }
    }

    private void startAudioMonitor() {
        audioStopTimestamp = 0;
        currentSilenceWaitMs = 5000L;
        audioMonitorHandler.removeCallbacks(audioMonitorRunnable);
        evaluateInstagramVsAlarmAudio();
        audioMonitorHandler.postDelayed(audioMonitorRunnable, 300);
    }

    public static void cancelActiveMission() {
        sMissionActive = false;
        sRemainingTasks = "";
        if (instance != null) {
            new Handler(Looper.getMainLooper()).post(() -> instance.hideOverlay());
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
            startAudioMonitor();
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

        // 2. DETECT POWER-OFF / SHUTDOWN DIALOGS (Samsung One UI & stock Android)
        boolean preventPowerOff = prefs.getBoolean("prevent_power_off", false);
        if (preventPowerOff && !"com.android.settings".equals(pkg)) {
            boolean isPowerMenu = pkg.contains("globalactions") ||
                                  cls.contains("globalactions") ||
                                  cls.contains("powerdialog") ||
                                  cls.contains("shutdown") ||
                                  cls.contains("poweroff") ||
                                  (pkg.contains("systemui") && cls.contains("actionsdialog"));

            if (!isPowerMenu && (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    || (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && (pkg.contains("systemui") || pkg.contains("samsung") || "android".equals(pkg))))) {
                if (hasPowerMenuInWindow(getRootInActiveWindow())) {
                    isPowerMenu = true;
                } else {
                    List<AccessibilityWindowInfo> windows = getWindows();
                    if (windows != null) {
                        for (AccessibilityWindowInfo w : windows) {
                            if (w != null && hasPowerMenuInWindow(w.getRoot())) {
                                isPowerMenu = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (isPowerMenu) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                new Handler(Looper.getMainLooper()).postDelayed(() ->
                        performGlobalAction(GLOBAL_ACTION_BACK), 150);
                notifyBlockAction("Power-off action blocked by InstaClock!");

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

    private boolean hasPowerMenuInWindow(AccessibilityNodeInfo root) {
        if (root == null) return false;
        String rootPkg = root.getPackageName() != null ? root.getPackageName().toString().toLowerCase(Locale.US) : "";
        if ("com.android.settings".equals(rootPkg) || "com.instalarm.app".equals(rootPkg)) return false;

        List<AccessibilityNodeInfo> powerNodes = root.findAccessibilityNodeInfosByText("Power off");
        List<AccessibilityNodeInfo> restartNodes = root.findAccessibilityNodeInfosByText("Restart");
        List<AccessibilityNodeInfo> sideBtnNodes = root.findAccessibilityNodeInfosByText("Side button settings");
        boolean hasPowerAndRestart = powerNodes != null && !powerNodes.isEmpty() && restartNodes != null && !restartNodes.isEmpty();
        boolean hasSideBtnAndPower = powerNodes != null && !powerNodes.isEmpty() && sideBtnNodes != null && !sideBtnNodes.isEmpty();
        return hasPowerAndRestart || hasSideBtnAndPower;
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
        // App switch detection (ignore transient overlays, systemui & inputmethod)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if ("com.instagram.android".equals(pkg)) {
                isInInstagram = true;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (tvOverlayStatus != null && sMissionActive) {
                        if (System.currentTimeMillis() >= sNextReelAllowedTime) {
                            tvOverlayStatus.setText("Scroll next");
                            tvOverlayStatus.setTextColor(Color.parseColor("#00E676"));
                        } else {
                            startCountdownTicker();
                        }
                    }
                });
            } else if (!pkg.isEmpty()
                    && !pkg.contains("instalarm")
                    && !pkg.contains("systemui")
                    && !pkg.contains("inputmethod")) {
                isInInstagram = false;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (tvOverlayStatus != null && sMissionActive) {
                        tvOverlayStatus.setText("Tap to open Instagram");
                        tvOverlayStatus.setTextColor(Color.parseColor("#FF5252"));
                    }
                });
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
                        case MotionEvent.ACTION_UP:
                            float dx = Math.abs(event.getRawX() - initialTouchX);
                            float dy = Math.abs(event.getRawY() - initialTouchY);
                            if (dx < 12 && dy < 12) {
                                openInstagramReels(getApplicationContext());
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

        tvOverlayCount.setText(String.format(Locale.getDefault(), "%d / %d Reels", sWatchedReels, sTargetReels));
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
                    tvOverlayStatus.setText(String.format(Locale.getDefault(), "Watch (%ds)", sec));
                    tvOverlayStatus.setTextColor(Color.parseColor("#CBD5E1"));
                }
            }

            @Override
            public void onFinish() {
                if (tvOverlayStatus != null && sMissionActive) {
                    tvOverlayStatus.setText("Scroll next");
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

        // Check if there are remaining missions in the chain
        if (sRemainingTasks != null && !sRemainingTasks.trim().isEmpty()) {
            final String nextChain = sRemainingTasks.trim();
            sRemainingTasks = "";
            if (tvOverlayStatus != null) {
                tvOverlayStatus.setText("Reels done. Starting next mission...");
                tvOverlayStatus.setTextColor(Color.parseColor("#00E676"));
            }
            AlarmService.unmuteSoundAndVibration();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                hideOverlay();
                AlarmReceiver.launchNextMission(
                        getApplicationContext(),
                        sCurrentAlarmId,
                        sAlarmLabel,
                        sSoundUri,
                        sVibrate,
                        sSnoozeDuration,
                        nextChain
                );
            }, 900);
            return;
        }

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

        // Show completion status in HUD (dynamic target count)
        if (tvOverlayStatus != null) {
            tvOverlayStatus.setText(String.format(Locale.getDefault(), "%d/%d complete. Alarm off.", sWatchedReels, sTargetReels));
            tvOverlayStatus.setTextColor(Color.parseColor("#00E676"));
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
        audioMonitorHandler.removeCallbacks(audioMonitorRunnable);
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

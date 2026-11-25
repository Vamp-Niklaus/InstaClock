package com.instalarm.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra("alarm_id", 0);
        String label = intent.getStringExtra("alarm_label");
        if (label == null || label.trim().isEmpty()) label = "Alarm";
        String dismissTask = intent.getStringExtra("dismiss_task");
        if (dismissTask == null) dismissTask = "reels_10";
        String soundUri = intent.getStringExtra("sound_uri");
        boolean vibrate = intent.getBooleanExtra("vibrate", true);
        int snoozeDuration = intent.getIntExtra("snooze_duration", 5);

        Log.d(TAG, "Alarm triggered! id=" + alarmId + ", task=" + dismissTask);

        // 1. Start background alarm service (plays sound & starts vibration)
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("alarm_id", alarmId);
        serviceIntent.putExtra("alarm_label", label);
        serviceIntent.putExtra("sound_uri", soundUri);
        serviceIntent.putExtra("vibrate", vibrate);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // 2. Launch appropriate full-screen activity or native Instagram Reels mission
        if (dismissTask.startsWith("reels") && isInstagramInstalled(context)) {
            int targetReels = 10;
            if (dismissTask.contains("_")) {
                try {
                    targetReels = Integer.parseInt(dismissTask.split("_")[1]);
                } catch (Exception ignored) {}
            }
            InstalarmAccessibilityService.startReelsMission(context, targetReels, alarmId);
            return;
        }

        Intent fullScreenIntent = new Intent(context, RingingActivity.class);

        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        fullScreenIntent.putExtra("alarm_id", alarmId);
        fullScreenIntent.putExtra("alarm_label", label);
        fullScreenIntent.putExtra("dismiss_task", dismissTask);
        fullScreenIntent.putExtra("sound_uri", soundUri);
        fullScreenIntent.putExtra("vibrate", vibrate);
        fullScreenIntent.putExtra("snooze_duration", snoozeDuration);

        context.startActivity(fullScreenIntent);
    }

    private boolean isInstagramInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.instagram.android", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

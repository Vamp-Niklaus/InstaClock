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
        String dismissTask = intent.getStringExtra("dismiss_task");
        String soundUri = intent.getStringExtra("sound_uri");
        boolean vibrate = intent.getBooleanExtra("vibrate", true);
        int snoozeDuration = intent.getIntExtra("snooze_duration", 5);

        if (alarmId > 0) {
            AlarmModel dbAlarm = AlarmDatabase.getInstance(context).getAlarmById(alarmId);
            if (dbAlarm != null) {
                label = dbAlarm.getLabel();
                dismissTask = dbAlarm.getDismissTask();
                soundUri = dbAlarm.getSoundUri();
                vibrate = dbAlarm.isVibrate();
                snoozeDuration = dbAlarm.getSnoozeDuration();
            }
        }
        if (label == null || label.trim().isEmpty()) label = "Alarm";
        if (dismissTask == null) dismissTask = "reels_10";

        Log.d(TAG, "Alarm triggered! id=" + alarmId + ", task=" + dismissTask);

        // 1. Start background alarm service (plays sound & starts vibration)
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("alarm_id", alarmId);
        serviceIntent.putExtra("alarm_label", label);
        serviceIntent.putExtra("dismiss_task", dismissTask);
        serviceIntent.putExtra("sound_uri", soundUri);
        serviceIntent.putExtra("vibrate", vibrate);
        serviceIntent.putExtra("snooze_duration", snoozeDuration);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // 2. Launch first mission in chain (supports up to 3 missions separated by '|')
        launchNextMission(context, alarmId, label, soundUri, vibrate, snoozeDuration, dismissTask);
    }

    public static void launchNextMission(Context context, long alarmId, String label,
                                         String soundUri, boolean vibrate, int snoozeDuration,
                                         String missionChain) {
        String firstTask = "none";
        String remainingChain = "";

        if (missionChain != null && !missionChain.trim().isEmpty()) {
            String[] parts = missionChain.split("\\|", 2);
            firstTask = parts[0].trim();
            if (parts.length > 1) {
                remainingChain = parts[1].trim();
            }
        }

        if (firstTask.startsWith("reels") && isInstagramInstalled(context)) {
            int targetReels = 10;
            if (firstTask.contains("_")) {
                try {
                    targetReels = Integer.parseInt(firstTask.split("_")[1]);
                } catch (Exception ignored) {}
            }
            // Start HUD overlay without auto-opening Instagram over lock screen
            InstalarmAccessibilityService.startReelsMission(
                    context, targetReels, alarmId, label, soundUri, vibrate, snoozeDuration, remainingChain, false);
        }

        Intent fullScreenIntent = new Intent(context, RingingActivity.class);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        fullScreenIntent.putExtra("alarm_id", alarmId);
        fullScreenIntent.putExtra("alarm_label", label);
        fullScreenIntent.putExtra("dismiss_task", firstTask);
        fullScreenIntent.putExtra("remaining_tasks", remainingChain);
        fullScreenIntent.putExtra("sound_uri", soundUri);
        fullScreenIntent.putExtra("vibrate", vibrate);
        fullScreenIntent.putExtra("snooze_duration", snoozeDuration);

        context.startActivity(fullScreenIntent);
    }

    private static boolean isInstagramInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.instagram.android", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

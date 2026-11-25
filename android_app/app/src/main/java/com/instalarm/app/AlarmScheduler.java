package com.instalarm.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";

    public static void scheduleAlarm(Context context, AlarmModel alarm) {
        if (!alarm.isEnabled()) {
            cancelAlarm(context, alarm.getId());
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarm: permission missing.");
            }
        }

        long triggerTime = calculateNextTriggerTime(alarm.getHour(), alarm.getMinute(), alarm.getDays());

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("com.instalarm.app.ACTION_TRIGGER_ALARM");
        intent.putExtra("alarm_id", alarm.getId());
        intent.putExtra("alarm_label", alarm.getLabel());
        intent.putExtra("dismiss_task", alarm.getDismissTask());
        intent.putExtra("sound_uri", alarm.getSoundUri());
        intent.putExtra("vibrate", alarm.isVibrate());
        intent.putExtra("snooze_duration", alarm.getSnoozeDuration());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) alarm.getId(),
                intent,
                flags
        );

        Intent showIntent = new Intent(context, MainActivity.class);
        PendingIntent showPendingIntent = PendingIntent.getActivity(
                context,
                (int) alarm.getId() + 10000,
                showIntent,
                flags
        );

        AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent);
        alarmManager.setAlarmClock(clockInfo, pendingIntent);

        Log.d(TAG, "Exact alarm scheduled for: " + alarm.getFormattedTime() + " " + alarm.getAmPm() + " at " + triggerTime);
        UpcomingAlarmNotificationManager.updateUpcomingAlarmNotification(context);
    }

    public static void scheduleSnooze(Context context, AlarmModel alarm, int snoozeMinutes) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        long triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L);

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("com.instalarm.app.ACTION_TRIGGER_ALARM");
        intent.putExtra("alarm_id", alarm.getId());
        intent.putExtra("alarm_label", alarm.getLabel() + " (Snoozed)");
        intent.putExtra("dismiss_task", alarm.getDismissTask());
        intent.putExtra("sound_uri", alarm.getSoundUri());
        intent.putExtra("vibrate", alarm.isVibrate());
        intent.putExtra("snooze_duration", snoozeMinutes);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) alarm.getId(),
                intent,
                flags
        );

        Intent showIntent = new Intent(context, MainActivity.class);
        PendingIntent showPendingIntent = PendingIntent.getActivity(
                context,
                (int) alarm.getId() + 10000,
                showIntent,
                flags
        );

        AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent);
        alarmManager.setAlarmClock(clockInfo, pendingIntent);
    }

    public static void cancelAlarm(Context context, long alarmId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("com.instalarm.app.ACTION_TRIGGER_ALARM");

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) alarmId,
                intent,
                flags
        );

        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        Log.d(TAG, "Alarm cancelled for id: " + alarmId);
        UpcomingAlarmNotificationManager.updateUpcomingAlarmNotification(context);
    }

    public static long calculateNextTriggerTime(int hour, int minute, String days) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (days == null || days.trim().isEmpty() || "Once".equalsIgnoreCase(days)) {
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }
            return target.getTimeInMillis();
        }

        if ("Daily".equalsIgnoreCase(days) || "Every day".equalsIgnoreCase(days)) {
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }
            return target.getTimeInMillis();
        }

        for (int i = 0; i < 7; i++) {
            Calendar candidate = (Calendar) target.clone();
            candidate.add(Calendar.DAY_OF_YEAR, i);

            if (candidate.after(now) && isDayMatching(candidate.get(Calendar.DAY_OF_WEEK), days)) {
                return candidate.getTimeInMillis();
            }
        }

        target.add(Calendar.DAY_OF_YEAR, 7);
        return target.getTimeInMillis();
    }

    private static boolean isDayMatching(int dayOfWeek, String days) {
        String dayName = "";
        switch (dayOfWeek) {
            case Calendar.SUNDAY: dayName = "Sun"; break;
            case Calendar.MONDAY: dayName = "Mon"; break;
            case Calendar.TUESDAY: dayName = "Tue"; break;
            case Calendar.WEDNESDAY: dayName = "Wed"; break;
            case Calendar.THURSDAY: dayName = "Thu"; break;
            case Calendar.FRIDAY: dayName = "Fri"; break;
            case Calendar.SATURDAY: dayName = "Sat"; break;
        }
        return days.contains(dayName);
    }
}

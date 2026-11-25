package com.instalarm.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.Collections;
import java.util.List;

public class UpcomingAlarmNotificationManager {

    private static final String CHANNEL_ID = "instalarm_upcoming_channel";
    private static final int NOTIFICATION_ID = 2001;

    public static void updateUpcomingAlarmNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        createNotificationChannel(context, nm);

        AlarmDatabase db = AlarmDatabase.getInstance(context);
        List<AlarmModel> alarms = db.getAllAlarms();

        AlarmModel nextAlarm = null;
        long earliestTrigger = Long.MAX_VALUE;

        for (AlarmModel alarm : alarms) {
            if (alarm.isEnabled()) {
                long trigger = AlarmScheduler.calculateNextTriggerTime(alarm.getHour(), alarm.getMinute(), alarm.getDays());
                if (trigger < earliestTrigger) {
                    earliestTrigger = trigger;
                    nextAlarm = alarm;
                }
            }
        }

        if (nextAlarm == null) {
            // No enabled alarms, remove notification
            nm.cancel(NOTIFICATION_ID);
            return;
        }

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, flags);

        String daysStr = nextAlarm.getDays();
        if (daysStr == null || daysStr.isEmpty() || "Once".equalsIgnoreCase(daysStr)) {
            daysStr = "Once";
        }

        String contentText = String.format("Next alarm at %s %s (%s) • Protection active",
                nextAlarm.getFormattedTime(), nextAlarm.getAmPm(), daysStr);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⏰ Instalarm Active")
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    private static void createNotificationChannel(Context context, NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Instalarm Upcoming Alarm",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows ongoing status of your scheduled alarms so you know Instalarm is active.");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
    }
}

package com.instalarm.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            Log.d(TAG, "Device rebooted. Rescheduling all active alarms...");

            AlarmDatabase db = AlarmDatabase.getInstance(context);
            List<AlarmModel> alarms = db.getAllAlarms();

            for (AlarmModel alarm : alarms) {
                if (alarm.isEnabled()) {
                    AlarmScheduler.scheduleAlarm(context, alarm);
                }
            }
        }
    }
}

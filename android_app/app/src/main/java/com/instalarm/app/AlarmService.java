package com.instalarm.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AlarmService extends Service {

    private static final String TAG = "AlarmService";
    private static final String CHANNEL_ID = "alarm_ringing_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static AlarmService activeInstance;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private boolean isMuted = false;

    public static boolean isAlarmRunning() {
        return activeInstance != null;
    }

    public static void muteSoundAndVibration() {
        if (activeInstance != null) {
            activeInstance.isMuted = true;
            if (activeInstance.mediaPlayer != null) {
                try {
                    activeInstance.mediaPlayer.setVolume(0.0f, 0.0f);
                } catch (Exception ignored) {}
            }
            activeInstance.stopVibration();
            Log.d(TAG, "Muted alarm sound & vibration for video playback");
        }
    }

    public static void unmuteSoundAndVibration() {
        if (activeInstance != null) {
            activeInstance.isMuted = false;
            if (activeInstance.mediaPlayer != null) {
                try {
                    activeInstance.mediaPlayer.setVolume(1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
            activeInstance.startVibration();
            Log.d(TAG, "Unmuted alarm sound & vibration");
        }
    }

    public static void stopAlarm(Context context) {
        if (activeInstance != null) {
            activeInstance.stopSelf();
        } else {
            Intent stopIntent = new Intent(context, AlarmService.class);
            context.stopService(stopIntent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmApp:AlarmWakeLock");
            wakeLock.acquire(15 * 60 * 1000L); // 15 min safety timeout
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long alarmId = 0;
        String label = "Alarm";
        String soundUri = null;
        boolean vibrate = true;

        if (intent != null) {
            alarmId = intent.getLongExtra("alarm_id", 0);
            label = intent.getStringExtra("alarm_label");
            if (label == null || label.trim().isEmpty()) label = "Alarm";
            soundUri = intent.getStringExtra("sound_uri");
            vibrate = intent.getBooleanExtra("vibrate", true);
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification(alarmId, label));
        startAlarmAudio(soundUri);

        if (vibrate) {
            startVibration();
        }

        return START_STICKY;
    }

    private void startAlarmAudio(String customSoundUri) {
        try {
            Uri alarmUri = null;
            if (customSoundUri != null && !customSoundUri.trim().isEmpty()) {
                try {
                    alarmUri = Uri.parse(customSoundUri);
                } catch (Exception ignored) {}
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }

            if (mediaPlayer != null) {
                try {
                    mediaPlayer.release();
                } catch (Exception ignored) {}
            }

            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(this, alarmUri);
            } catch (Exception fallbackEx) {
                Uri defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (defaultUri == null) defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                mediaPlayer.setDataSource(this, defaultUri);
            }

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            mediaPlayer.setAudioAttributes(audioAttributes);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start media player for alarm: " + e.getMessage());
        }
    }

    private void startVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = new long[]{0, 800, 400, 800, 400};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private Notification buildForegroundNotification(long alarmId, String label) {
        Intent fullScreenIntent = new Intent(this, MainActivity.class);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        fullScreenIntent.putExtra("alarm_id", alarmId);
        fullScreenIntent.putExtra("alarm_label", label);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                4001,
                fullScreenIntent,
                flags
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Instalarm: " + label)
            .setContentText("Alarm is ringing! Complete mission to dismiss.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alarm Ringing Service",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Foreground service channel for active ringing alarms");
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setBypassDnd(true);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activeInstance = null;

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }

        stopVibration();

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
            wakeLock = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

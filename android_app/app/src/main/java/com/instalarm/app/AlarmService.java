package com.instalarm.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
    private AudioManager audioManager;
    private int lockedAlarmVolume = -1;
    private long activeAlarmId = Long.MIN_VALUE;
    private boolean isMuted = false;

    private final Handler volumeGuardHandler = new Handler(Looper.getMainLooper());
    private final Runnable volumeGuardRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeInstance != null && !isMuted && audioManager != null && lockedAlarmVolume > 0) {
                try {
                    int current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                    if (current != lockedAlarmVolume) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, lockedAlarmVolume, 0);
                    }
                } catch (Exception ignored) {}
            }
            volumeGuardHandler.postDelayed(this, 500);
        }
    };

    public static long getCurrentAlarmId() {
        return activeInstance != null ? activeInstance.activeAlarmId : Long.MIN_VALUE;
    }

    public static boolean isAlarmRunning() {
        return activeInstance != null;
    }

    public static void muteSoundAndVibration() {
        if (activeInstance != null && !activeInstance.isMuted) {
            activeInstance.isMuted = true;
            if (activeInstance.mediaPlayer != null) {
                try {
                    if (activeInstance.mediaPlayer.isPlaying()) {
                        activeInstance.mediaPlayer.pause();
                    }
                } catch (Exception ignored) {}
            }
            activeInstance.stopVibration();
            Log.d(TAG, "Paused alarm sound & vibration for Instagram audio");
        }
    }

    public static void unmuteSoundAndVibration() {
        if (activeInstance != null && activeInstance.isMuted) {
            activeInstance.isMuted = false;
            activeInstance.enforceConfiguredAlarmVolume();
            if (activeInstance.mediaPlayer != null) {
                try {
                    if (!activeInstance.mediaPlayer.isPlaying()) {
                        activeInstance.mediaPlayer.start();
                    }
                    activeInstance.mediaPlayer.setVolume(1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
            activeInstance.startVibration();
            Log.d(TAG, "Resumed alarm sound & vibration");
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
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        enforceConfiguredAlarmVolume();
        volumeGuardHandler.postDelayed(volumeGuardRunnable, 500);
        createNotificationChannel();
    }

    private void enforceConfiguredAlarmVolume() {
        if (audioManager == null) return;
        try {
            SharedPreferences prefs = getSharedPreferences("alarm_settings", MODE_PRIVATE);
            int percent = prefs.getInt("alarm_volume_percent", 80);
            if (percent < 40) percent = 40;
            if (percent > 100) percent = 100;

            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int minAllowed = (int) Math.ceil(maxVol * 0.40);
            lockedAlarmVolume = Math.max(minAllowed, Math.round(maxVol * (percent / 100.0f)));
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, lockedAlarmVolume, 0);
        } catch (Exception e) {
            Log.e(TAG, "Failed to lock alarm volume: " + e.getMessage());
        }
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

        boolean alreadyPlayingSame = mediaPlayer != null && activeAlarmId == alarmId && (mediaPlayer.isPlaying() || isMuted);
        activeAlarmId = alarmId;
        startForeground(NOTIFICATION_ID, buildForegroundNotification(alarmId, label));

        if (!alreadyPlayingSame) {
            isMuted = false;
            enforceConfiguredAlarmVolume();
            startAlarmAudio(soundUri);
            if (vibrate) {
                startVibration();
            }
        }

        return START_STICKY;
    }

    private boolean trySetDataSource(MediaPlayer mp, Uri uri) {
        if (uri == null) return false;
        try {
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                java.io.File file = new java.io.File(uri.getPath());
                if (!file.exists()) return false;
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    mp.setDataSource(fis.getFD());
                }
                return true;
            }
            mp.setDataSource(this, uri);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Could not load sound URI " + uri + ": " + e.getMessage());
            return false;
        }
    }

    private void startAlarmAudio(String customSoundUri) {
        try {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.release();
                } catch (Exception ignored) {}
            }

            mediaPlayer = new MediaPlayer();
            boolean loaded = false;

            if (customSoundUri != null && !customSoundUri.trim().isEmpty()) {
                loaded = trySetDataSource(mediaPlayer, Uri.parse(customSoundUri));
            }
            if (!loaded) {
                SharedPreferences prefs = getSharedPreferences("alarm_settings", MODE_PRIVATE);
                String defaultCustomUri = prefs.getString("default_sound_uri", "");
                if (!defaultCustomUri.trim().isEmpty()) {
                    loaded = trySetDataSource(mediaPlayer, Uri.parse(defaultCustomUri));
                }
            }
            if (!loaded) {
                Uri sysDefault = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (sysDefault == null) sysDefault = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                loaded = trySetDataSource(mediaPlayer, sysDefault);
            }

            if (!loaded) return;

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            mediaPlayer.setAudioAttributes(audioAttributes);
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
            .setContentTitle("InstaClock: " + label)
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
        volumeGuardHandler.removeCallbacks(volumeGuardRunnable);
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

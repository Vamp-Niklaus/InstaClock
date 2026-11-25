package com.instalarm.app;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_RINGTONE_PICKER = 888;
    private SharedPreferences prefs;

    private TextView tvSettingsDefaultSnooze;
    private TextView tvSettingsDefaultSound;
    private TextView tvSettingsExactAlarmStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("alarm_settings", MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSettingsDefaultSnooze = findViewById(R.id.tvSettingsDefaultSnooze);
        tvSettingsDefaultSound = findViewById(R.id.tvSettingsDefaultSound);
        tvSettingsExactAlarmStatus = findViewById(R.id.tvSettingsExactAlarmStatus);

        findViewById(R.id.rowDefaultSnooze).setOnClickListener(v -> showSnoozePicker());
        findViewById(R.id.rowDefaultSound).setOnClickListener(v -> showSoundPicker());
        findViewById(R.id.rowExactAlarmPermission).setOnClickListener(v -> openExactAlarmSettings());

        updateViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateViews();
    }

    private void updateViews() {
        int snooze = prefs.getInt("default_snooze", 5);
        tvSettingsDefaultSnooze.setText(snooze + " minutes");

        String soundName = prefs.getString("default_sound_name", "Default alarm");
        tvSettingsDefaultSound.setText(soundName);

        boolean hasExactAlarm = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            hasExactAlarm = am != null && am.canScheduleExactAlarms();
        }

        if (hasExactAlarm) {
            tvSettingsExactAlarmStatus.setText("Granted (alarms fire precisely)");
            tvSettingsExactAlarmStatus.setTextColor(0xFF4CAF50);
        } else {
            tvSettingsExactAlarmStatus.setText("Not granted (alarms may be delayed)");
            tvSettingsExactAlarmStatus.setTextColor(0xFFFF5252);
        }
    }

    private void showSnoozePicker() {
        final int[] options = new int[]{3, 5, 10, 15, 20};
        String[] labels = new String[]{"3 minutes", "5 minutes", "10 minutes", "15 minutes", "20 minutes"};
        int current = prefs.getInt("default_snooze", 5);
        int selectedIndex = 1;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Default Snooze Duration")
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    prefs.edit().putInt("default_snooze", options[which]).apply();
                    updateViews();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSoundPicker() {
        Intent ringtoneIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);

        String uriStr = prefs.getString("default_sound_uri", "");
        if (!uriStr.isEmpty()) {
            ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(uriStr));
        }

        startActivityForResult(ringtoneIntent, REQUEST_RINGTONE_PICKER);
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Exact alarm permission is automatically granted on this Android version.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RINGTONE_PICKER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                String uriStr = uri.toString();
                Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
                String name = ringtone != null ? ringtone.getTitle(this) : "Custom Ringtone";
                prefs.edit().putString("default_sound_uri", uriStr).putString("default_sound_name", name).apply();
            } else {
                prefs.edit().putString("default_sound_uri", "").putString("default_sound_name", "Default alarm").apply();
            }
            updateViews();
        }
    }
}

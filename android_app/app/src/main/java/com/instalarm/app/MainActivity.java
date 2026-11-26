package com.instalarm.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AlarmsAdapter.OnAlarmActionListener {

    private static final int REQUEST_RINGTONE_PICKER = 777;
    private static final int REQUEST_AUDIO_FILE_PICKER = 778;
    private static final int REQUEST_DEVICE_ADMIN = 888;

    // View containers
    private View tabAlarmsView;
    private View tabSettingsView;
    private MaterialToolbar topAppBar;
    private FloatingActionButton fabAddAlarm;
    private View fabMenuOverlay;
    private boolean isFabMenuOpen = false;

    // Top upcoming alarm ticker
    private LinearLayout layoutNextAlarmCountdown;
    private TextView tvNextAlarmCountdown;

    // Bottom Navigation
    private View navBtnAlarms, navBtnSettings;
    private TextView navTextAlarms, navTextSettings;

    // Alarms Tab Views
    private RecyclerView rvAlarms;
    private AlarmsAdapter alarmsAdapter;
    private final List<AlarmModel> alarmList = new ArrayList<>();
    private AlarmDatabase alarmDatabase;
    private TextView tvEmptyAlarms;
    private View cardExactAlarmPermission;

    // Settings Views
    private SharedPreferences settingsPrefs;
    private TextView tvSettingSnoozeValue;
    private TextView tvSettingSoundValue;
    private TextView tvSettingExactAlarmStatus;
    private SwitchMaterial switchSettingPreventPowerOff;
    private SwitchMaterial switchSettingPreventUninstall;
    private TextView tvSettingPowerOffStatus;
    private TextView tvSettingUninstallStatus;
    private TextView tvSettingInstaLimitStatus;
    private TextView tvSettingInstaLockStatus;
    private SwitchMaterial switchSettingInstaLockNow;
    private View layoutDeveloperSection;
    private TextView tvDeveloperChevron;
    private boolean isDeveloperExpanded = false;

    // Device Admin component
    private ComponentName deviceAdminComponent;
    private DevicePolicyManager devicePolicyManager;

    // State for sound picker & preview
    private TextView currentDialogSoundTextView;
    private String currentSelectedSoundUri = "";
    private String currentSelectedSoundName = "Default alarm";
    private MediaPlayer previewPlayer;
    private TextView activePreviewButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        alarmDatabase = AlarmDatabase.getInstance(this);
        settingsPrefs = getSharedPreferences("alarm_settings", MODE_PRIVATE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, InstalarmDeviceAdminReceiver.class);

        initViews();
        setupBottomNav();
        setupSettingsTab();
        checkPermissions();
        loadAlarms();
        AppUpdater.checkForUpdate(this, false, settingsPrefs);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkVictoryCelebration(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkExactAlarmBanner();
        updateSettingsDisplay();
        loadAlarms();
        checkVictoryCelebration(getIntent());
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSoundPreview();
    }

    private void checkVictoryCelebration(Intent intent) {
        if (intent != null && intent.getBooleanExtra("mission_accomplished", false)) {
            intent.removeExtra("mission_accomplished");
            new AlertDialog.Builder(this)
                    .setTitle("🎉 Mission Accomplished!")
                    .setMessage("You watched all required Instagram Reels!\nYou are now awake, and your alarm has been turned off.\nHave an awesome day!")
                    .setPositiveButton("Awesome", null)
                    .show();
        }
    }

    private void initViews() {
        topAppBar = findViewById(R.id.topAppBar);
        tabAlarmsView = findViewById(R.id.tabAlarmsView);
        tabSettingsView = findViewById(R.id.tabSettingsView);
        fabAddAlarm = findViewById(R.id.fabAddAlarm);
        fabMenuOverlay = findViewById(R.id.fabMenuOverlay);

        layoutNextAlarmCountdown = findViewById(R.id.layoutNextAlarmCountdown);
        tvNextAlarmCountdown = findViewById(R.id.tvNextAlarmCountdown);

        rvAlarms = findViewById(R.id.rvAlarms);
        rvAlarms.setLayoutManager(new LinearLayoutManager(this));
        alarmsAdapter = new AlarmsAdapter(this, alarmList, this);
        rvAlarms.setAdapter(alarmsAdapter);

        tvEmptyAlarms = findViewById(R.id.tvEmptyAlarms);
        cardExactAlarmPermission = findViewById(R.id.cardExactAlarmPermission);

        View btnFixPermission = findViewById(R.id.btnFixExactAlarmPermission);
        if (btnFixPermission != null) {
            btnFixPermission.setOnClickListener(v -> openExactAlarmSettings());
        }

        fabAddAlarm.setOnClickListener(v -> toggleFabMenu(!isFabMenuOpen));
        if (fabMenuOverlay != null) {
            fabMenuOverlay.setOnClickListener(v -> toggleFabMenu(false));
        }
        View btnQuick = findViewById(R.id.btnFabQuickAlarm);
        if (btnQuick != null) {
            btnQuick.setOnClickListener(v -> {
                toggleFabMenu(false);
                showQuickAlarmDialog();
            });
        }
        View btnRegular = findViewById(R.id.btnFabRegularAlarm);
        if (btnRegular != null) {
            btnRegular.setOnClickListener(v -> {
                toggleFabMenu(false);
                showAlarmEditDialog(null);
            });
        }
        setupSwipeToDelete();
    }

    private void toggleFabMenu(boolean open) {
        isFabMenuOpen = open;
        if (fabMenuOverlay != null) {
            fabMenuOverlay.setVisibility(open ? View.VISIBLE : View.GONE);
        }
        if (fabAddAlarm != null) {
            fabAddAlarm.setImageResource(open
                    ? android.R.drawable.ic_menu_close_clear_cancel
                    : android.R.drawable.ic_input_add);
        }
    }

    private void setupBottomNav() {
        navBtnAlarms = findViewById(R.id.navBtnAlarms);
        navBtnSettings = findViewById(R.id.navBtnSettings);

        navTextAlarms = findViewById(R.id.navTextAlarms);
        navTextSettings = findViewById(R.id.navTextSettings);

        navBtnAlarms.setOnClickListener(v -> switchTab(0));
        navBtnSettings.setOnClickListener(v -> switchTab(1));
    }

    private void switchTab(int tabIndex) {
        tabAlarmsView.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        tabSettingsView.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);

        fabAddAlarm.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);

        int activeColor = ContextCompat.getColor(this, R.color.accent_insta);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_muted);

        navTextAlarms.setTextColor(tabIndex == 0 ? activeColor : inactiveColor);
        navTextSettings.setTextColor(tabIndex == 1 ? activeColor : inactiveColor);

        if (tabIndex == 0) {
            topAppBar.setTitle("Alarm");
            updateUpcomingAlarmTicker();
        } else {
            topAppBar.setTitle("Setting");
            updateSettingsDisplay();
        }
    }

    private void setupSettingsTab() {
        tvSettingSnoozeValue = findViewById(R.id.tvSettingSnoozeValue);
        tvSettingSoundValue = findViewById(R.id.tvSettingSoundValue);
        tvSettingExactAlarmStatus = findViewById(R.id.tvSettingExactAlarmStatus);

        tvSettingPowerOffStatus = findViewById(R.id.tvSettingPowerOffStatus);
        tvSettingUninstallStatus = findViewById(R.id.tvSettingUninstallStatus);

        tvSettingInstaLimitStatus = findViewById(R.id.tvSettingInstaLimitStatus);
        tvSettingInstaLockStatus = findViewById(R.id.tvSettingInstaLockStatus);
        switchSettingInstaLockNow = findViewById(R.id.switchSettingInstaLockNow);

        layoutDeveloperSection = findViewById(R.id.layoutDeveloperSection);
        tvDeveloperChevron = findViewById(R.id.tvDeveloperChevron);

        switchSettingPreventPowerOff = findViewById(R.id.switchSettingPreventPowerOff);
        switchSettingPreventUninstall = findViewById(R.id.switchSettingPreventUninstall);

        // Developer section toggle (hidden by default)
        View rowDeveloperToggle = findViewById(R.id.rowSettingDeveloperToggle);
        if (rowDeveloperToggle != null) {
            rowDeveloperToggle.setOnClickListener(v -> {
                isDeveloperExpanded = !isDeveloperExpanded;
                if (layoutDeveloperSection != null) {
                    layoutDeveloperSection.setVisibility(isDeveloperExpanded ? View.VISIBLE : View.GONE);
                }
                if (tvDeveloperChevron != null) {
                    tvDeveloperChevron.setText(isDeveloperExpanded ? "Hide ⌄" : "Show ›");
                }
            });
        }

        // Instagram status row tap (opens Accessibility setup if needed)
        View rowInstaStatus = findViewById(R.id.rowSettingInstagramStatus);
        if (rowInstaStatus != null) {
            rowInstaStatus.setOnClickListener(v -> {
                if (isInstagramInstalled() && !isAccessibilityServiceEnabled(this, InstalarmAccessibilityService.class)) {
                    requestPreventPowerOffActivation();
                }
            });
        }

        // Daily Instagram Limit picker
        View rowInstaLimit = findViewById(R.id.rowSettingInstaLimit);
        if (rowInstaLimit != null) {
            rowInstaLimit.setOnClickListener(v -> showInstagramLimitPicker());
        }

        // Prevent power-off toggle
        switchSettingPreventPowerOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                requestPreventPowerOffActivation();
            } else {
                settingsPrefs.edit().putBoolean("prevent_power_off", false).apply();
                updateSettingsDisplay();
            }
        });

        // Prevent uninstall toggle
        switchSettingPreventUninstall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                requestDeviceAdminActivation();
            } else {
                if (devicePolicyManager != null && devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                    devicePolicyManager.removeActiveAdmin(deviceAdminComponent);
                }
                settingsPrefs.edit().putBoolean("prevent_uninstall", false).apply();
                updateSettingsDisplay();
            }
        });

        findViewById(R.id.rowSettingSnooze).setOnClickListener(v -> showSnoozePicker(null));
        findViewById(R.id.rowSettingSound).setOnClickListener(v -> {
            currentDialogSoundTextView = null;
            openRingtonePicker(null);
        });
        View btnPreviewDefault = findViewById(R.id.btnPreviewDefaultSound);
        if (btnPreviewDefault instanceof TextView) {
            btnPreviewDefault.setOnClickListener(v -> {
                String uri = settingsPrefs.getString("default_sound_uri", "");
                toggleSoundPreview(uri, (TextView) btnPreviewDefault);
            });
        }

        SeekBar seekBarVolume = findViewById(R.id.seekBarAlarmVolume);
        TextView tvSettingVolumeValue = findViewById(R.id.tvSettingVolumeValue);
        if (seekBarVolume != null && tvSettingVolumeValue != null) {
            int savedVol = Math.max(40, Math.min(100, settingsPrefs.getInt("alarm_volume_percent", 80)));
            seekBarVolume.setProgress(savedVol - 40);
            tvSettingVolumeValue.setText(savedVol + "%");
            seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int pct = 40 + progress;
                    tvSettingVolumeValue.setText(pct + "%");
                    if (fromUser) {
                        settingsPrefs.edit().putInt("alarm_volume_percent", pct).apply();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        findViewById(R.id.rowSettingExactAlarm).setOnClickListener(v -> openExactAlarmSettings());

        View rowAppUpdate = findViewById(R.id.rowSettingAppUpdate);
        if (rowAppUpdate != null) {
            rowAppUpdate.setOnClickListener(v -> AppUpdater.checkForUpdate(this, true, settingsPrefs));
        }

        // Open native Instagram App (only if detected)
        View btnOpenInsta = findViewById(R.id.btnOpenNativeInstaApp);
        if (btnOpenInsta != null) {
            btnOpenInsta.setOnClickListener(v -> {
                if (!isInstagramInstalled()) {
                    Toast.makeText(this, "Instagram is not installed", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = getPackageManager().getLaunchIntentForPackage("com.instagram.android");
                if (intent != null) {
                    startActivity(intent);
                }
            });
        }

        // Test Native App Mission (inside Developer section)
        findViewById(R.id.btnTestReelsMission).setOnClickListener(v -> {
            if (!isInstagramInstalled()) {
                Toast.makeText(this, "Instagram is not installed", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean isAccessOn = isAccessibilityServiceEnabled(this, InstalarmAccessibilityService.class);
            if (!isAccessOn) {
                requestPreventPowerOffActivation();
                return;
            }
            Toast.makeText(this, "Launching Instagram Reels Mission...", Toast.LENGTH_SHORT).show();
            InstalarmAccessibilityService.startReelsMission(this, 5, -1);
        });

        updateSettingsDisplay();
    }

    private boolean isInstagramInstalled() {
        try {
            getPackageManager().getPackageInfo("com.instagram.android", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isMorningEditWindow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 10;
    }

    private void showMorningLockDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Locked Until Morning")
                .setMessage("To protect your focus, Instagram limits can only be relaxed or turned off between 6:00 AM and 10:00 AM.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showInstagramLimitPicker() {
        String[] options = new String[]{"Off", "15 min / day", "30 min / day", "45 min / day", "60 min / day"};
        int[] values = new int[]{0, 15, 30, 45, 60};
        int currentLimit = settingsPrefs.getInt("insta_daily_limit_mins", 0);

        new AlertDialog.Builder(this)
                .setTitle("Daily Instagram Limit")
                .setItems(options, (dialog, which) -> {
                    int chosen = values[which];
                    boolean isWeakening = currentLimit > 0 && (chosen == 0 || chosen > currentLimit);
                    if (isWeakening && !isMorningEditWindow()) {
                        showMorningLockDialog();
                        return;
                    }
                    if (chosen > 0 && !isAccessibilityServiceEnabled(this, InstalarmAccessibilityService.class)) {
                        requestPreventPowerOffActivation();
                    }
                    settingsPrefs.edit().putInt("insta_daily_limit_mins", chosen).apply();
                    updateSettingsDisplay();
                })
                .show();
    }

    private void requestPreventPowerOffActivation() {
        // 1. Check Accessibility Service
        boolean isAccessibilityEnabled = isAccessibilityServiceEnabled(this, InstalarmAccessibilityService.class);
        // 2. Check Overlay Permission
        boolean hasOverlay = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasOverlay = Settings.canDrawOverlays(this);
        }

        if (!hasOverlay) {
            new AlertDialog.Builder(this)
                    .setTitle("Display Over Apps Permission")
                    .setMessage("To detect and display the warning screen over the power menu, please grant 'Display over other apps'.")
                    .setPositiveButton("Allow", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        switchSettingPreventPowerOff.setChecked(false);
                    })
                    .show();
            return;
        }

        if (!isAccessibilityEnabled) {
            new AlertDialog.Builder(this)
                    .setTitle("Accessibility Permission")
                    .setMessage("To detect when the power-off or shutdown menu is opened, please enable 'InstaClock' in Accessibility Settings.")
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        switchSettingPreventPowerOff.setChecked(false);
                    })
                    .setOnCancelListener(dialog -> switchSettingPreventPowerOff.setChecked(false))
                    .show();
            return;
        }

        settingsPrefs.edit().putBoolean("prevent_power_off", true).apply();
        updateSettingsDisplay();
        Toast.makeText(this, "🛡️ Prevent Power-off is Active", Toast.LENGTH_SHORT).show();
    }

    private void requestDeviceAdminActivation() {
        if (devicePolicyManager != null && !devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Activate Device Administrator to prevent Instalarm from being uninstalled while alarms and missions are active.");
            startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
        } else {
            settingsPrefs.edit().putBoolean("prevent_uninstall", true).apply();
            updateSettingsDisplay();
            Toast.makeText(this, "🔒 Prevent Uninstall is Active", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo enabledService : enabledServices) {
            ComponentName enabledServiceComponentName = ComponentName.unflattenFromString(enabledService.getId());
            if (enabledServiceComponentName != null &&
                    enabledServiceComponentName.getClassName().equals(serviceClass.getName())) {
                return true;
            }
        }
        return false;
    }

    private void updateSettingsDisplay() {
        int snooze = settingsPrefs.getInt("default_snooze", 5);
        if (tvSettingSnoozeValue != null) {
            tvSettingSnoozeValue.setText(snooze + " minutes");
        }

        String soundName = settingsPrefs.getString("default_sound_name", "Default alarm");
        if (tvSettingSoundValue != null) {
            tvSettingSoundValue.setText(soundName);
        }

        boolean hasExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            hasExact = am != null && am.canScheduleExactAlarms();
        }
        if (tvSettingExactAlarmStatus != null) {
            tvSettingExactAlarmStatus.setText(hasExact ? "Granted (Alarms fire accurately)" : "Not granted (Tap to fix in Settings)");
            tvSettingExactAlarmStatus.setTextColor(ContextCompat.getColor(this, hasExact ? R.color.success : R.color.primary));
        }

        // Prevent power-off status (strictly requires Overlay + Accessibility permissions)
        boolean hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        boolean hasAccess = isAccessibilityServiceEnabled(this, InstalarmAccessibilityService.class);
        boolean isPowerOffEnabled = settingsPrefs.getBoolean("prevent_power_off", false) && hasOverlay && hasAccess;
        if (settingsPrefs.getBoolean("prevent_power_off", false) && (!hasOverlay || !hasAccess)) {
            settingsPrefs.edit().putBoolean("prevent_power_off", false).apply();
        }
        int blockCount = settingsPrefs.getInt("power_off_block_count", 0);
        if (switchSettingPreventPowerOff != null) {
            switchSettingPreventPowerOff.setOnCheckedChangeListener(null);
            switchSettingPreventPowerOff.setChecked(isPowerOffEnabled);
            switchSettingPreventPowerOff.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) requestPreventPowerOffActivation();
                else {
                    settingsPrefs.edit().putBoolean("prevent_power_off", false).apply();
                    updateSettingsDisplay();
                }
            });
        }
        if (tvSettingPowerOffStatus != null) {
            tvSettingPowerOffStatus.setText(isPowerOffEnabled ? "Active • Blocked " + blockCount + " times" : "Disabled • Tap to configure");
            tvSettingPowerOffStatus.setTextColor(ContextCompat.getColor(this, isPowerOffEnabled ? R.color.success : R.color.text_secondary));
        }

        // Prevent uninstall status (strictly requires active Device Admin)
        boolean isAdminActive = devicePolicyManager != null && devicePolicyManager.isAdminActive(deviceAdminComponent);
        if (settingsPrefs.getBoolean("prevent_uninstall", false) != isAdminActive) {
            settingsPrefs.edit().putBoolean("prevent_uninstall", isAdminActive).apply();
        }
        boolean isUninstallEnabled = isAdminActive;
        if (switchSettingPreventUninstall != null) {
            switchSettingPreventUninstall.setOnCheckedChangeListener(null);
            switchSettingPreventUninstall.setChecked(isUninstallEnabled);
            switchSettingPreventUninstall.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) requestDeviceAdminActivation();
                else {
                    if (devicePolicyManager != null && devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                        devicePolicyManager.removeActiveAdmin(deviceAdminComponent);
                    }
                    settingsPrefs.edit().putBoolean("prevent_uninstall", false).apply();
                    updateSettingsDisplay();
                }
            });
        }
        if (tvSettingUninstallStatus != null) {
            tvSettingUninstallStatus.setText(isUninstallEnabled ? "Active (Device Admin Locked)" : "Device Admin protection");
            tvSettingUninstallStatus.setTextColor(ContextCompat.getColor(this, isUninstallEnabled ? R.color.success : R.color.text_secondary));
        }

        TextView tvSettingAppVersion = findViewById(R.id.tvSettingAppVersion);
        if (tvSettingAppVersion != null) {
            tvSettingAppVersion.setText("Version " + AppUpdater.getCurrentVersion(this) + " • Tap to check");
        }

        // Instagram Native App Status (minimal)
        boolean isInstaInstalled = isInstagramInstalled();
        boolean isAccessOn = isAccessibilityServiceEnabled(this, InstalarmAccessibilityService.class);

        TextView tvSettingInstaAppStatus = findViewById(R.id.tvSettingInstaAppStatus);
        View btnOpenInsta = findViewById(R.id.btnOpenNativeInstaApp);
        if (tvSettingInstaAppStatus != null) {
            if (isInstaInstalled && isAccessOn) {
                tvSettingInstaAppStatus.setText("Connected");
                tvSettingInstaAppStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
            } else if (isInstaInstalled) {
                tvSettingInstaAppStatus.setText("Accessibility required");
                tvSettingInstaAppStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_insta));
            } else {
                tvSettingInstaAppStatus.setText("Not installed");
                tvSettingInstaAppStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            }
        }
        if (btnOpenInsta != null) {
            btnOpenInsta.setVisibility(isInstaInstalled ? View.VISIBLE : View.GONE);
            btnOpenInsta.setEnabled(isInstaInstalled);
        }

        // Daily Usage Limit display
        int limitMins = settingsPrefs.getInt("insta_daily_limit_mins", 0);
        int usedMins = InstalarmAccessibilityService.getTodayUsageSeconds(settingsPrefs) / 60;
        if (tvSettingInstaLimitStatus != null) {
            if (limitMins <= 0) {
                tvSettingInstaLimitStatus.setText("Off");
                tvSettingInstaLimitStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            } else if (usedMins >= limitMins) {
                tvSettingInstaLimitStatus.setText(String.format("Limit reached (%dm / %dm today)", usedMins, limitMins));
                tvSettingInstaLimitStatus.setTextColor(ContextCompat.getColor(this, R.color.primary));
            } else {
                tvSettingInstaLimitStatus.setText(String.format("%dm / %dm used today", usedMins, limitMins));
                tvSettingInstaLimitStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
            }
        }

        // Lock Instagram Now switch
        boolean isInstaLocked = settingsPrefs.getBoolean("insta_lock_now", false);
        if (switchSettingInstaLockNow != null) {
            switchSettingInstaLockNow.setOnCheckedChangeListener(null);
            switchSettingInstaLockNow.setChecked(isInstaLocked);
            switchSettingInstaLockNow.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) {
                    if (!isAccessibilityServiceEnabled(this, InstalarmAccessibilityService.class)) {
                        requestPreventPowerOffActivation();
                    }
                    settingsPrefs.edit().putBoolean("insta_lock_now", true).apply();
                    updateSettingsDisplay();
                } else {
                    if (!isMorningEditWindow()) {
                        switchSettingInstaLockNow.setOnCheckedChangeListener(null);
                        switchSettingInstaLockNow.setChecked(true);
                        updateSettingsDisplay();
                        showMorningLockDialog();
                        return;
                    }
                    settingsPrefs.edit().putBoolean("insta_lock_now", false).apply();
                    updateSettingsDisplay();
                }
            });
        }
        if (tvSettingInstaLockStatus != null) {
            tvSettingInstaLockStatus.setText(isInstaLocked
                    ? "Locked • Unlockable 6 AM – 10 AM"
                    : "Off • Editable only 6 AM – 10 AM when locked");
            tvSettingInstaLockStatus.setTextColor(ContextCompat.getColor(this,
                    isInstaLocked ? R.color.primary : R.color.text_secondary));
        }
    }

    private void updateUpcomingAlarmTicker() {
        if (layoutNextAlarmCountdown == null || tvNextAlarmCountdown == null) return;

        AlarmModel nextAlarm = null;
        long earliestTrigger = Long.MAX_VALUE;

        for (AlarmModel a : alarmList) {
            if (a.isEnabled()) {
                long trigger = AlarmScheduler.calculateNextTriggerTime(a.getHour(), a.getMinute(), a.getDays());
                if (trigger < earliestTrigger) {
                    earliestTrigger = trigger;
                    nextAlarm = a;
                }
            }
        }

        if (nextAlarm != null && earliestTrigger != Long.MAX_VALUE) {
            long diffMillis = earliestTrigger - System.currentTimeMillis();
            if (diffMillis > 0) {
                long totalMins = diffMillis / (1000 * 60);
                long hours = totalMins / 60;
                long mins = totalMins % 60;

                String text;
                if (hours == 0) {
                    text = "Ring in " + mins + " min";
                } else if (mins == 0) {
                    text = "Ring in " + hours + " hr";
                } else {
                    text = "Ring in " + hours + " hr. " + mins + " min";
                }

                tvNextAlarmCountdown.setText(text);
                layoutNextAlarmCountdown.setVisibility(View.VISIBLE);
                return;
            }
        }

        layoutNextAlarmCountdown.setVisibility(View.GONE);
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < alarmList.size()) {
                    AlarmModel alarm = alarmList.get(position);
                    confirmAndDeleteAlarm(alarm, position);
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvAlarms);
    }

    private void checkExactAlarmBanner() {
        boolean hasExactAlarm = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            hasExactAlarm = am != null && am.canScheduleExactAlarms();
        }
        cardExactAlarmPermission.setVisibility(hasExactAlarm ? View.GONE : View.VISIBLE);
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void loadAlarms() {
        alarmList.clear();
        alarmList.addAll(alarmDatabase.getAllAlarms());
        Collections.sort(alarmList, (a, b) -> {
            int comp = Integer.compare(a.getHour(), b.getHour());
            if (comp != 0) return comp;
            return Integer.compare(a.getMinute(), b.getMinute());
        });

        if (alarmsAdapter != null) {
            alarmsAdapter.notifyDataSetChanged();
        }

        tvEmptyAlarms.setVisibility(alarmList.isEmpty() ? View.VISIBLE : View.GONE);
        updateUpcomingAlarmTicker();
        UpcomingAlarmNotificationManager.updateUpcomingAlarmNotification(this);
    }

    @Override
    public void onToggle(AlarmModel alarm, boolean isEnabled) {
        alarm.setEnabled(isEnabled);
        alarmDatabase.updateAlarm(alarm);

        if (isEnabled) {
            AlarmScheduler.scheduleAlarm(this, alarm);
            Toast.makeText(this, "Alarm set for " + alarm.getFormattedTime() + " " + alarm.getAmPm(), Toast.LENGTH_SHORT).show();
        } else {
            AlarmScheduler.cancelAlarm(this, alarm.getId());
            Toast.makeText(this, "Alarm disabled", Toast.LENGTH_SHORT).show();
        }

        loadAlarms();
    }

    @Override
    public void onDelete(AlarmModel alarm, int position) {
        confirmAndDeleteAlarm(alarm, position);
    }

    @Override
    public void onEdit(AlarmModel alarm) {
        showAlarmEditDialog(alarm);
    }

    @Override
    public void onPreview(AlarmModel alarm) {
        Intent previewIntent = new Intent(this, RingingActivity.class);
        previewIntent.putExtra("alarm_id", alarm.getId());
        previewIntent.putExtra("alarm_label", alarm.getLabel());
        previewIntent.putExtra("dismiss_task", "none");
        previewIntent.putExtra("sound_uri", alarm.getSoundUri());
        previewIntent.putExtra("vibrate", alarm.isVibrate());
        previewIntent.putExtra("snooze_duration", alarm.getSnoozeDuration());
        startActivity(previewIntent);
    }

    @Override
    public void onSkipOnce(AlarmModel alarm) {
        alarm.setEnabled(false);
        alarmDatabase.updateAlarm(alarm);
        AlarmScheduler.cancelAlarm(this, alarm.getId());
        Toast.makeText(this, "Alarm skipped", Toast.LENGTH_SHORT).show();
        loadAlarms();
    }

    @Override
    public void onDuplicate(AlarmModel alarm) {
        AlarmModel copy = new AlarmModel(
                System.currentTimeMillis(),
                alarm.getHour(),
                alarm.getMinute(),
                true,
                alarm.getDays(),
                alarm.getLabel(),
                alarm.getDismissTask(),
                alarm.isVibrate(),
                alarm.getSoundUri(),
                alarm.getSoundName(),
                alarm.getSnoozeDuration()
        );
        alarmDatabase.insertAlarm(copy);
        AlarmScheduler.scheduleAlarm(this, copy);
        Toast.makeText(this, "Alarm duplicated", Toast.LENGTH_SHORT).show();
        loadAlarms();
    }

    private void confirmAndDeleteAlarm(AlarmModel alarm, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Alarm")
                .setMessage("Are you sure you want to delete this alarm?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    AlarmScheduler.cancelAlarm(this, alarm.getId());
                    alarmDatabase.deleteAlarm(alarm.getId());
                    alarmList.remove(position);
                    alarmsAdapter.notifyItemRemoved(position);
                    tvEmptyAlarms.setVisibility(alarmList.isEmpty() ? View.VISIBLE : View.GONE);
                    updateUpcomingAlarmTicker();
                    Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    alarmsAdapter.notifyItemChanged(position);
                })
                .setCancelable(false)
                .show();
    }

    private void showQuickAlarmDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.dialog_quick_alarm);

        TextView tvQuickMinutes = sheet.findViewById(R.id.tvQuickMinutes);
        TextView tvQuickRingAt = sheet.findViewById(R.id.tvQuickRingAt);
        TextView tvQuickSoundName = sheet.findViewById(R.id.tvQuickSoundName);
        SwitchMaterial switchQuickVibrate = sheet.findViewById(R.id.switchQuickVibrate);

        final int[] addedMinutes = {5};
        currentDialogSoundTextView = tvQuickSoundName;
        currentSelectedSoundUri = settingsPrefs.getString("default_sound_uri", "");
        currentSelectedSoundName = settingsPrefs.getString("default_sound_name", "Default alarm");
        if (tvQuickSoundName != null) {
            tvQuickSoundName.setText(currentSelectedSoundName);
        }

        Runnable updateQuickDisplay = () -> {
            if (tvQuickMinutes != null) {
                tvQuickMinutes.setText(String.valueOf(addedMinutes[0]));
            }
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, addedMinutes[0]);
            SimpleDateFormat fmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            if (tvQuickRingAt != null) {
                tvQuickRingAt.setText("Ring at " + fmt.format(cal.getTime()).toLowerCase(Locale.getDefault()));
            }
        };
        updateQuickDisplay.run();

        View.OnClickListener addPreset = v -> {
            int delta = 0;
            int id = v.getId();
            if (id == R.id.btnQuick1m) delta = 1;
            else if (id == R.id.btnQuick5m) delta = 5;
            else if (id == R.id.btnQuick10m) delta = 10;
            else if (id == R.id.btnQuick15m) delta = 15;
            else if (id == R.id.btnQuick30m) delta = 30;
            else if (id == R.id.btnQuick60m) delta = 60;
            addedMinutes[0] += delta;
            updateQuickDisplay.run();
        };

        sheet.findViewById(R.id.btnQuick1m).setOnClickListener(addPreset);
        sheet.findViewById(R.id.btnQuick5m).setOnClickListener(addPreset);
        sheet.findViewById(R.id.btnQuick10m).setOnClickListener(addPreset);
        sheet.findViewById(R.id.btnQuick15m).setOnClickListener(addPreset);
        sheet.findViewById(R.id.btnQuick30m).setOnClickListener(addPreset);
        sheet.findViewById(R.id.btnQuick60m).setOnClickListener(addPreset);

        sheet.findViewById(R.id.btnQuickReset).setOnClickListener(v -> {
            addedMinutes[0] = 0;
            updateQuickDisplay.run();
        });

        sheet.findViewById(R.id.rowQuickSound).setOnClickListener(v -> openRingtonePicker(currentSelectedSoundUri));
        sheet.findViewById(R.id.btnQuickAlarmClose).setOnClickListener(v -> sheet.dismiss());

        sheet.findViewById(R.id.btnQuickSave).setOnClickListener(v -> {
            int mins = Math.max(1, addedMinutes[0]);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, mins);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            boolean vibrate = switchQuickVibrate != null && switchQuickVibrate.isChecked();

            AlarmModel alarm = new AlarmModel(
                    System.currentTimeMillis(),
                    hour,
                    minute,
                    true,
                    "Once",
                    "Quick alarm",
                    "none",
                    vibrate,
                    currentSelectedSoundUri,
                    currentSelectedSoundName,
                    settingsPrefs.getInt("default_snooze", 5)
            );
            alarmDatabase.insertAlarm(alarm);
            AlarmScheduler.scheduleAlarm(this, alarm);
            Toast.makeText(this, "Quick alarm in " + mins + " min", Toast.LENGTH_SHORT).show();
            sheet.dismiss();
            loadAlarms();
        });

        sheet.show();
    }

    private void styleWheelPicker(NumberPicker picker) {
        if (picker == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setSelectionDividerHeight(0);
            picker.setTextColor(android.graphics.Color.WHITE);
            picker.setTextSize(68f);
        }
    }

    private void showAlarmEditDialog(@Nullable AlarmModel alarmToEdit) {
        Dialog dialog = new Dialog(this, R.style.Theme_Instalarm_Dialog);
        dialog.setContentView(R.layout.dialog_alarm_edit);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setStatusBarColor(0xFF0D0F14);
            dialog.getWindow().setNavigationBarColor(0xFF0D0F14);
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        TextView tvTitle = dialog.findViewById(R.id.tvEditDialogTitle);
        TextView tvEditRingCountdown = dialog.findViewById(R.id.tvEditRingCountdown);
        NumberPicker npHour = dialog.findViewById(R.id.npHour);
        NumberPicker npMinute = dialog.findViewById(R.id.npMinute);
        NumberPicker npAmPm = dialog.findViewById(R.id.npAmPm);

        npHour.setMinValue(1);
        npHour.setMaxValue(12);
        npHour.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));

        npMinute.setMinValue(0);
        npMinute.setMaxValue(59);
        npMinute.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));

        npAmPm.setMinValue(0);
        npAmPm.setMaxValue(1);
        npAmPm.setDisplayedValues(new String[]{"a.m.", "p.m."});
        npAmPm.setWrapSelectorWheel(false);

        styleWheelPicker(npHour);
        styleWheelPicker(npMinute);
        styleWheelPicker(npAmPm);

        EditText etLabel = dialog.findViewById(R.id.etAlarmLabel);
        CheckBox cbDaily = dialog.findViewById(R.id.cbDaily);
        SwitchMaterial switchVibrate = dialog.findViewById(R.id.switchVibrate);

        ToggleButton btnSun = dialog.findViewById(R.id.btnDaySun);
        ToggleButton btnMon = dialog.findViewById(R.id.btnDayMon);
        ToggleButton btnTue = dialog.findViewById(R.id.btnDayTue);
        ToggleButton btnWed = dialog.findViewById(R.id.btnDayWed);
        ToggleButton btnThu = dialog.findViewById(R.id.btnDayThu);
        ToggleButton btnFri = dialog.findViewById(R.id.btnDayFri);
        ToggleButton btnSat = dialog.findViewById(R.id.btnDaySat);

        final String[] missionSlots = new String[]{"reels_10", "", ""};
        TextView tvMissionCountBadge = dialog.findViewById(R.id.tvMissionCountBadge);
        TextView[] slotIcons = new TextView[]{
                dialog.findViewById(R.id.tvMissionSlot1Icon),
                dialog.findViewById(R.id.tvMissionSlot2Icon),
                dialog.findViewById(R.id.tvMissionSlot3Icon)
        };
        TextView[] slotLabels = new TextView[]{
                dialog.findViewById(R.id.tvMissionSlot1Label),
                dialog.findViewById(R.id.tvMissionSlot2Label),
                dialog.findViewById(R.id.tvMissionSlot3Label)
        };

        Runnable updateMissionSlotUI = () -> {
            int activeCount = 0;
            for (int i = 0; i < 3; i++) {
                String t = missionSlots[i] != null ? missionSlots[i].trim() : "";
                if (t.isEmpty() || "none".equalsIgnoreCase(t)) {
                    slotIcons[i].setText("+");
                    slotIcons[i].setTextColor(0xFF64748B);
                    slotLabels[i].setText("");
                } else {
                    activeCount++;
                    slotIcons[i].setTextColor(0xFFFFFFFF);
                    if (t.startsWith("reels")) {
                        String count = t.contains("_") ? t.split("_")[1] : "10";
                        slotIcons[i].setText("📸");
                        slotLabels[i].setText(count + " Reels");
                    } else if ("math".equalsIgnoreCase(t)) {
                        slotIcons[i].setText("🧮");
                        slotLabels[i].setText("Math");
                    } else if ("shake".equalsIgnoreCase(t)) {
                        slotIcons[i].setText("📳");
                        slotLabels[i].setText("Shake");
                    }
                }
            }
            tvMissionCountBadge.setText(activeCount + "/3");
        };

        dialog.findViewById(R.id.cardMissionSlot1).setOnClickListener(v ->
                showMissionPickerSheet(0, missionSlots, updateMissionSlotUI));
        dialog.findViewById(R.id.cardMissionSlot2).setOnClickListener(v ->
                showMissionPickerSheet(1, missionSlots, updateMissionSlotUI));
        dialog.findViewById(R.id.cardMissionSlot3).setOnClickListener(v ->
                showMissionPickerSheet(2, missionSlots, updateMissionSlotUI));

        TextView tvSelectedSound = dialog.findViewById(R.id.tvSelectedSound);
        TextView tvSelectedSnooze = dialog.findViewById(R.id.tvSelectedSnooze);

        final int[] chosenSnooze = {alarmToEdit != null ? alarmToEdit.getSnoozeDuration() : settingsPrefs.getInt("default_snooze", 5)};
        tvSelectedSnooze.setText(chosenSnooze[0] + " minutes");

        dialog.findViewById(R.id.btnSelectSnoozeDuration).setOnClickListener(v -> {
            showSnoozePicker(mins -> {
                chosenSnooze[0] = mins;
                tvSelectedSnooze.setText(mins + " minutes");
            });
        });

        currentDialogSoundTextView = tvSelectedSound;
        if (alarmToEdit != null) {
            currentSelectedSoundUri = alarmToEdit.getSoundUri();
            currentSelectedSoundName = alarmToEdit.getSoundName();
        } else {
            currentSelectedSoundUri = settingsPrefs.getString("default_sound_uri", "");
            currentSelectedSoundName = settingsPrefs.getString("default_sound_name", "Default alarm");
        }
        tvSelectedSound.setText(currentSelectedSoundName);

        dialog.findViewById(R.id.btnSelectSound).setOnClickListener(v -> openRingtonePicker(currentSelectedSoundUri));
        TextView btnPreviewDialog = dialog.findViewById(R.id.btnPreviewDialogSound);
        if (btnPreviewDialog != null) {
            btnPreviewDialog.setOnClickListener(v -> toggleSoundPreview(currentSelectedSoundUri, btnPreviewDialog));
        }
        dialog.setOnDismissListener(d -> stopSoundPreview());

        // Runnable to recalculate "Ring in X hr Y min" ticker inside dialog
        Runnable recalculateCountdown = () -> {
            int h12 = npHour.getValue();
            boolean isPm = npAmPm.getValue() == 1;
            int h = (h12 == 12) ? (isPm ? 12 : 0) : (isPm ? h12 + 12 : h12);
            int m = npMinute.getValue();

            List<String> activeDays = new ArrayList<>();
            if (btnSun.isChecked()) activeDays.add("Sun");
            if (btnMon.isChecked()) activeDays.add("Mon");
            if (btnTue.isChecked()) activeDays.add("Tue");
            if (btnWed.isChecked()) activeDays.add("Wed");
            if (btnThu.isChecked()) activeDays.add("Thu");
            if (btnFri.isChecked()) activeDays.add("Fri");
            if (btnSat.isChecked()) activeDays.add("Sat");

            String daysFormatted = activeDays.isEmpty() ? "Once" : (activeDays.size() == 7 ? "Daily" : String.join(",", activeDays));
            long nextTime = AlarmScheduler.calculateNextTriggerTime(h, m, daysFormatted);
            long diff = nextTime - System.currentTimeMillis();

            if (diff > 0) {
                long totalMins = diff / (1000 * 60);
                long hours = totalMins / 60;
                long mins = totalMins % 60;
                tvEditRingCountdown.setText("Ring in " + (hours > 0 ? hours + " hr. " : "") + mins + " min");
            }
        };

        NumberPicker.OnValueChangeListener wheelListener = (picker, oldVal, newVal) -> recalculateCountdown.run();
        npHour.setOnValueChangedListener(wheelListener);
        npMinute.setOnValueChangedListener(wheelListener);
        npAmPm.setOnValueChangedListener(wheelListener);

        // Daily CheckBox Listener
        cbDaily.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                btnSun.setChecked(isChecked);
                btnMon.setChecked(isChecked);
                btnTue.setChecked(isChecked);
                btnWed.setChecked(isChecked);
                btnThu.setChecked(isChecked);
                btnFri.setChecked(isChecked);
                btnSat.setChecked(isChecked);
                recalculateCountdown.run();
            }
        });

        // Day Buttons Listeners with visual sync
        View.OnClickListener dayClickListener = v -> {
            boolean allChecked = btnSun.isChecked() && btnMon.isChecked() && btnTue.isChecked()
                    && btnWed.isChecked() && btnThu.isChecked() && btnFri.isChecked() && btnSat.isChecked();
            cbDaily.setChecked(allChecked);
            recalculateCountdown.run();
        };

        btnSun.setOnClickListener(dayClickListener);
        btnMon.setOnClickListener(dayClickListener);
        btnTue.setOnClickListener(dayClickListener);
        btnWed.setOnClickListener(dayClickListener);
        btnThu.setOnClickListener(dayClickListener);
        btnFri.setOnClickListener(dayClickListener);
        btnSat.setOnClickListener(dayClickListener);

        if (alarmToEdit != null) {
            tvTitle.setText("Edit Alarm");
            int h24 = alarmToEdit.getHour();
            npAmPm.setValue(h24 >= 12 ? 1 : 0);
            int h12 = h24 % 12;
            npHour.setValue(h12 == 0 ? 12 : h12);
            npMinute.setValue(alarmToEdit.getMinute());
            etLabel.setText(alarmToEdit.getLabel());
            switchVibrate.setChecked(alarmToEdit.isVibrate());

            String days = alarmToEdit.getDays();
            boolean isDaily = "Daily".equals(days);
            btnSun.setChecked(days.contains("Sun") || isDaily);
            btnMon.setChecked(days.contains("Mon") || isDaily);
            btnTue.setChecked(days.contains("Tue") || isDaily);
            btnWed.setChecked(days.contains("Wed") || isDaily);
            btnThu.setChecked(days.contains("Thu") || isDaily);
            btnFri.setChecked(days.contains("Fri") || isDaily);
            btnSat.setChecked(days.contains("Sat") || isDaily);

            cbDaily.setChecked(isDaily || (btnSun.isChecked() && btnMon.isChecked() && btnTue.isChecked()
                    && btnWed.isChecked() && btnThu.isChecked() && btnFri.isChecked() && btnSat.isChecked()));

            String rawTask = alarmToEdit.getDismissTask();
            missionSlots[0] = "";
            missionSlots[1] = "";
            missionSlots[2] = "";
            if (rawTask != null && !"none".equalsIgnoreCase(rawTask)) {
                String[] parts = rawTask.split("\\|");
                for (int i = 0; i < Math.min(3, parts.length); i++) {
                    String p = parts[i].trim();
                    if (!p.isEmpty() && !"none".equalsIgnoreCase(p)) {
                        missionSlots[i] = p;
                    }
                }
            }
        } else {
            tvTitle.setText("Wake-up alarm");
            npAmPm.setValue(0);
            npHour.setValue(7);
            npMinute.setValue(0);
            etLabel.setText("Wake up early");
            switchVibrate.setChecked(true);
            missionSlots[0] = "reels_10";
            missionSlots[1] = "";
            missionSlots[2] = "";

            // Default to Weekdays checked
            btnMon.setChecked(true);
            btnTue.setChecked(true);
            btnWed.setChecked(true);
            btnThu.setChecked(true);
            btnFri.setChecked(true);
            btnSun.setChecked(false);
            btnSat.setChecked(false);
            cbDaily.setChecked(false);
        }

        updateMissionSlotUI.run();
        recalculateCountdown.run();

        View.OnClickListener saveAction = v -> {
            int h12Val = npHour.getValue();
            boolean isPmVal = npAmPm.getValue() == 1;
            int hour = (h12Val == 12) ? (isPmVal ? 12 : 0) : (isPmVal ? h12Val + 12 : h12Val);
            int minute = npMinute.getValue();
            String label = etLabel.getText().toString().trim();
            if (label.isEmpty()) label = "Wake up early";

            List<String> activeDays = new ArrayList<>();
            if (btnSun.isChecked()) activeDays.add("Sun");
            if (btnMon.isChecked()) activeDays.add("Mon");
            if (btnTue.isChecked()) activeDays.add("Tue");
            if (btnWed.isChecked()) activeDays.add("Wed");
            if (btnThu.isChecked()) activeDays.add("Thu");
            if (btnFri.isChecked()) activeDays.add("Fri");
            if (btnSat.isChecked()) activeDays.add("Sat");

            String daysFormatted;
            if (activeDays.isEmpty()) {
                daysFormatted = "Once";
            } else if (activeDays.size() == 7) {
                daysFormatted = "Daily";
            } else {
                daysFormatted = String.join(",", activeDays);
            }

            List<String> chosenTasks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String t = missionSlots[i] != null ? missionSlots[i].trim() : "";
                if (!t.isEmpty() && !"none".equalsIgnoreCase(t)) {
                    chosenTasks.add(t);
                }
            }
            String dismissTask = chosenTasks.isEmpty() ? "none" : String.join("|", chosenTasks);
            boolean vibrate = switchVibrate.isChecked();

            AlarmModel alarm;
            if (alarmToEdit != null) {
                alarm = alarmToEdit;
                alarm.setHour(hour);
                alarm.setMinute(minute);
                alarm.setLabel(label);
                alarm.setDays(daysFormatted);
                alarm.setDismissTask(dismissTask);
                alarm.setVibrate(vibrate);
                alarm.setSoundUri(currentSelectedSoundUri);
                alarm.setSoundName(currentSelectedSoundName);
                alarm.setSnoozeDuration(chosenSnooze[0]);
                alarm.setEnabled(true);
                alarmDatabase.updateAlarm(alarm);
            } else {
                alarm = new AlarmModel(
                        System.currentTimeMillis(),
                        hour,
                        minute,
                        true,
                        daysFormatted,
                        label,
                        dismissTask,
                        vibrate,
                        currentSelectedSoundUri,
                        currentSelectedSoundName,
                        chosenSnooze[0]
                );
                alarmDatabase.insertAlarm(alarm);
            }

            AlarmScheduler.scheduleAlarm(this, alarm);
            Toast.makeText(this, "Alarm set for " + alarm.getFormattedTime() + " " + alarm.getAmPm(), Toast.LENGTH_SHORT).show();

            dialog.dismiss();
            loadAlarms();
        };

        dialog.findViewById(R.id.btnCancelAlarm).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnSaveAlarmBottom).setOnClickListener(saveAction);

        dialog.show();
    }

    private void showMissionPickerSheet(int slotIndex, String[] missionSlots, Runnable onUpdated) {
        BottomSheetDialog picker = new BottomSheetDialog(this);
        picker.setContentView(R.layout.dialog_mission_picker);

        picker.findViewById(R.id.btnMissionPickerClose).setOnClickListener(v -> picker.dismiss());

        picker.findViewById(R.id.rowMissionReels).setOnClickListener(v -> {
            picker.dismiss();
            showReelsCountDrawer(slotIndex, missionSlots, onUpdated);
        });

        picker.findViewById(R.id.rowMissionMath).setOnClickListener(v -> {
            missionSlots[slotIndex] = "math";
            onUpdated.run();
            picker.dismiss();
        });

        picker.findViewById(R.id.rowMissionShake).setOnClickListener(v -> {
            missionSlots[slotIndex] = "shake";
            onUpdated.run();
            picker.dismiss();
        });

        picker.findViewById(R.id.rowMissionNone).setOnClickListener(v -> {
            missionSlots[slotIndex] = "";
            onUpdated.run();
            picker.dismiss();
        });

        picker.show();
    }

    private void showReelsCountDrawer(int slotIndex, String[] missionSlots, Runnable onUpdated) {
        BottomSheetDialog drawer = new BottomSheetDialog(this);
        drawer.setContentView(R.layout.dialog_mission_reels_config);

        NumberPicker npReelCount = drawer.findViewById(R.id.npReelCount);
        int currentCount = 10;
        if (missionSlots[slotIndex] != null && missionSlots[slotIndex].startsWith("reels_")) {
            try {
                currentCount = Integer.parseInt(missionSlots[slotIndex].split("_")[1]);
            } catch (Exception ignored) {}
        }
        if (npReelCount != null) {
            npReelCount.setMinValue(1);
            npReelCount.setMaxValue(100);
            npReelCount.setValue(Math.max(1, Math.min(100, currentCount)));
            npReelCount.setWrapSelectorWheel(false);
        }

        drawer.findViewById(R.id.btnReelsConfigBack).setOnClickListener(v -> {
            drawer.dismiss();
            showMissionPickerSheet(slotIndex, missionSlots, onUpdated);
        });
        drawer.findViewById(R.id.btnReelsConfigClose).setOnClickListener(v -> drawer.dismiss());
        drawer.findViewById(R.id.btnReelsConfigComplete).setOnClickListener(v -> {
            int count = npReelCount != null ? npReelCount.getValue() : 10;
            missionSlots[slotIndex] = "reels_" + count;
            onUpdated.run();
            drawer.dismiss();
        });

        drawer.show();
    }

    interface OnSnoozeSelectedListener {
        void onSelected(int minutes);
    }

    private void showSnoozePicker(@Nullable OnSnoozeSelectedListener callback) {
        String[] options = new String[]{"3 minutes", "5 minutes", "10 minutes", "15 minutes", "20 minutes"};
        int[] values = new int[]{3, 5, 10, 15, 20};

        new AlertDialog.Builder(this)
                .setTitle("Select Snooze Duration")
                .setItems(options, (dialog, which) -> {
                    int chosen = values[which];
                    if (callback != null) {
                        callback.onSelected(chosen);
                    } else {
                        settingsPrefs.edit().putInt("default_snooze", chosen).apply();
                        updateSettingsDisplay();
                    }
                })
                .show();
    }

    private void openRingtonePicker(String currentUriStr) {
        // Show cached sounds + File Manager audio picker + Default alarm
        List<String> labels = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        List<String> names = new ArrayList<>();

        labels.add("📁 Choose audio from File Manager");
        uris.add("__PICK_FILE__");
        names.add("");

        labels.add("🔔 Default alarm");
        uris.add("");
        names.add("Default alarm");

        try {
            String jsonStr = settingsPrefs.getString("cached_sounds_json", "[]");
            org.json.JSONArray arr = new org.json.JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String n = obj.optString("name", "");
                String u = obj.optString("uri", "");
                if (!n.isEmpty() && !u.isEmpty()) {
                    java.io.File f = new java.io.File(Uri.parse(u).getPath());
                    if (f.exists()) {
                        labels.add("🎵 " + n);
                        uris.add(u);
                        names.add(n);
                    }
                }
            }
        } catch (Exception ignored) {}

        new AlertDialog.Builder(this)
                .setTitle("Alarm Sound")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String chosenUri = uris.get(which);
                    if ("__PICK_FILE__".equals(chosenUri)) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("audio/*");
                        startActivityForResult(intent, REQUEST_AUDIO_FILE_PICKER);
                    } else {
                        String chosenName = names.get(which);
                        applySelectedSound(chosenUri, chosenName);
                    }
                })
                .show();
    }

    private void applySelectedSound(String uriStr, String name) {
        currentSelectedSoundUri = uriStr;
        currentSelectedSoundName = name;

        if (currentDialogSoundTextView != null) {
            currentDialogSoundTextView.setText(name);
        }

        settingsPrefs.edit()
                .putString("default_sound_uri", currentSelectedSoundUri)
                .putString("default_sound_name", currentSelectedSoundName)
                .apply();

        updateSettingsDisplay();
    }

    private void cacheAndSelectAudioFile(Uri sourceUri) {
        try {
            String displayName = "Custom Sound";
            try (android.database.Cursor cursor = getContentResolver().query(sourceUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx);
                    }
                }
            }

            java.io.File soundsDir = new java.io.File(getFilesDir(), "sounds");
            if (!soundsDir.exists()) soundsDir.mkdirs();
            java.io.File destFile = new java.io.File(soundsDir, "alarm_" + System.currentTimeMillis() + ".audio");

            try (java.io.InputStream in = getContentResolver().openInputStream(sourceUri);
                 java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {
                if (in == null) {
                    Toast.makeText(this, "Could not read audio file", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            String cachedUri = Uri.fromFile(destFile).toString();
            saveCachedSoundEntry(displayName, cachedUri);
            applySelectedSound(cachedUri, displayName);
            Toast.makeText(this, "Sound cached: " + displayName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load audio file", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCachedSoundEntry(String name, String uri) {
        try {
            org.json.JSONArray existing = new org.json.JSONArray(settingsPrefs.getString("cached_sounds_json", "[]"));
            org.json.JSONArray updated = new org.json.JSONArray();
            org.json.JSONObject newest = new org.json.JSONObject();
            newest.put("name", name);
            newest.put("uri", uri);
            updated.put(newest);

            for (int i = 0; i < existing.length() && updated.length() < 10; i++) {
                org.json.JSONObject obj = existing.getJSONObject(i);
                if (!name.equals(obj.optString("name"))) {
                    updated.put(obj);
                }
            }
            settingsPrefs.edit().putString("cached_sounds_json", updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_AUDIO_FILE_PICKER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                cacheAndSelectAudioFile(uri);
            }
        } else if (requestCode == REQUEST_RINGTONE_PICKER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
                String name = ringtone != null ? ringtone.getTitle(this) : "Selected Sound";
                applySelectedSound(uri.toString(), name);
            }
        } else if (requestCode == REQUEST_DEVICE_ADMIN) {
            boolean isAdminActive = devicePolicyManager != null && devicePolicyManager.isAdminActive(deviceAdminComponent);
            settingsPrefs.edit().putBoolean("prevent_uninstall", isAdminActive).apply();
            updateSettingsDisplay();
            if (isAdminActive) {
                Toast.makeText(this, "🔒 Device Administrator Activated", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void toggleSoundPreview(String uriStr, TextView btn) {
        if (previewPlayer != null && previewPlayer.isPlaying()) {
            boolean sameButton = (activePreviewButton == btn);
            stopSoundPreview();
            if (sameButton) return;
        }
        try {
            Uri soundUri = (uriStr != null && !uriStr.trim().isEmpty())
                    ? Uri.parse(uriStr)
                    : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            previewPlayer = new MediaPlayer();
            if ("file".equals(soundUri.getScheme()) && soundUri.getPath() != null) {
                java.io.FileInputStream fis = new java.io.FileInputStream(soundUri.getPath());
                previewPlayer.setDataSource(fis.getFD());
                fis.close();
            } else {
                previewPlayer.setDataSource(this, soundUri);
            }
            previewPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            previewPlayer.prepare();
            previewPlayer.start();
            activePreviewButton = btn;
            if (btn != null) btn.setText("⏹");
            previewPlayer.setOnCompletionListener(mp -> stopSoundPreview());
        } catch (Exception e) {
            stopSoundPreview();
            Toast.makeText(this, "Could not play sound preview", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopSoundPreview() {
        if (previewPlayer != null) {
            try {
                if (previewPlayer.isPlaying()) previewPlayer.stop();
                previewPlayer.release();
            } catch (Exception ignored) {}
            previewPlayer = null;
        }
        if (activePreviewButton != null) {
            activePreviewButton.setText("▶");
            activePreviewButton = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (isFabMenuOpen) {
            toggleFabMenu(false);
        } else if (tabAlarmsView.getVisibility() != View.VISIBLE) {
            switchTab(0);
        } else {
            super.onBackPressed();
        }
    }
}

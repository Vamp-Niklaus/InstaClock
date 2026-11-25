package com.instalarm.app;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class RingingActivity extends AppCompatActivity implements SensorEventListener {

    private long alarmId;
    private String label;
    private String dismissTask;
    private String remainingTasks;
    private String soundUri;
    private boolean vibrate;
    private int snoozeDuration;

    private TextView tvRingingTime;
    private TextView tvRingingDate;
    private TextView tvRingingLabel;
    private Button btnRingingSnooze;
    private Button btnRingingDismiss;

    // Math Task Views
    private View layoutMathTask;
    private TextView tvMathProgress;
    private TextView tvMathEquation;
    private TextView tvMathInput;
    private String mathCurrentInput = "";
    private int mathProblemIndex = 0;
    private final List<MathItem> mathProblems = new ArrayList<>();

    // Shake Task Views
    private View layoutShakeTask;
    private TextView tvShakeCount;
    private ProgressBar pbShakeProgress;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int shakeCount = 0;
    private long lastShakeTime = 0;
    private static final int TARGET_SHAKES = 20;

    private View layoutTaskCompleted;
    private boolean isTaskCompleted = false;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_ringing);

        extractIntentData();
        initViews();
        setupDismissTask();
        startAlarmAudioAndVibration();
        updateClock();
        clockHandler.postDelayed(clockRunnable, 1000);
    }

    private void extractIntentData() {
        Intent intent = getIntent();
        alarmId = intent.getLongExtra("alarm_id", 0);
        label = intent.getStringExtra("alarm_label");
        if (label == null || label.trim().isEmpty()) label = "Alarm";
        dismissTask = intent.getStringExtra("dismiss_task");
        if (dismissTask == null) dismissTask = "none";
        remainingTasks = intent.getStringExtra("remaining_tasks");
        if (remainingTasks == null) remainingTasks = "";
        soundUri = intent.getStringExtra("sound_uri");
        vibrate = intent.getBooleanExtra("vibrate", true);
        snoozeDuration = intent.getIntExtra("snooze_duration", 5);
        if (snoozeDuration <= 0) snoozeDuration = 5;
    }

    private void initViews() {
        tvRingingTime = findViewById(R.id.tvRingingTime);
        tvRingingDate = findViewById(R.id.tvRingingDate);
        tvRingingLabel = findViewById(R.id.tvRingingLabel);
        btnRingingSnooze = findViewById(R.id.btnRingingSnooze);
        btnRingingDismiss = findViewById(R.id.btnRingingDismiss);

        layoutMathTask = findViewById(R.id.layoutMathTask);
        tvMathProgress = findViewById(R.id.tvMathProgress);
        tvMathEquation = findViewById(R.id.tvMathEquation);
        tvMathInput = findViewById(R.id.tvMathInput);

        layoutShakeTask = findViewById(R.id.layoutShakeTask);
        tvShakeCount = findViewById(R.id.tvShakeCount);
        pbShakeProgress = findViewById(R.id.pbShakeProgress);

        layoutTaskCompleted = findViewById(R.id.layoutTaskCompleted);

        tvRingingLabel.setText(label);
        btnRingingSnooze.setText("Snooze (" + snoozeDuration + "m)");

        btnRingingSnooze.setOnClickListener(v -> onSnoozeClicked());
        btnRingingDismiss.setOnClickListener(v -> onDismissClicked());
    }

    private void updateClock() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        Date now = new Date();
        tvRingingTime.setText(timeFormat.format(now));
        tvRingingDate.setText(dateFormat.format(now));
    }

    private void startAlarmAudioAndVibration() {
        Intent serviceIntent = new Intent(this, AlarmService.class);
        serviceIntent.putExtra("alarm_id", (int) alarmId);
        serviceIntent.putExtra("alarm_label", label);
        serviceIntent.putExtra("sound_uri", soundUri);
        serviceIntent.putExtra("vibrate", vibrate);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void setupDismissTask() {
        if ("math".equalsIgnoreCase(dismissTask)) {
            isTaskCompleted = false;
            btnRingingDismiss.setEnabled(false);
            btnRingingDismiss.setAlpha(0.4f);
            layoutMathTask.setVisibility(View.VISIBLE);
            layoutShakeTask.setVisibility(View.GONE);
            generateMathProblems(3);
            setupMathKeypad();
            displayCurrentMathProblem();
        } else if ("shake".equalsIgnoreCase(dismissTask)) {
            isTaskCompleted = false;
            btnRingingDismiss.setEnabled(false);
            btnRingingDismiss.setAlpha(0.4f);
            layoutMathTask.setVisibility(View.GONE);
            layoutShakeTask.setVisibility(View.VISIBLE);
            setupShakeSensor();
        } else {
            // No task required
            isTaskCompleted = true;
            btnRingingDismiss.setEnabled(true);
            btnRingingDismiss.setAlpha(1.0f);
            layoutMathTask.setVisibility(View.GONE);
            layoutShakeTask.setVisibility(View.GONE);
        }
    }

    // --- MATH TASK LOGIC ---
    private void generateMathProblems(int count) {
        mathProblems.clear();
        Random rng = new Random();
        for (int i = 0; i < count; i++) {
            int op = rng.nextInt(3); // 0 = +, 1 = -, 2 = *
            int n1, n2, ans;
            String symbol;
            if (op == 0) {
                n1 = rng.nextInt(35) + 12;
                n2 = rng.nextInt(35) + 11;
                ans = n1 + n2;
                symbol = "+";
            } else if (op == 1) {
                n1 = rng.nextInt(40) + 20;
                n2 = rng.nextInt(n1 - 10) + 5;
                ans = n1 - n2;
                symbol = "-";
            } else {
                n1 = rng.nextInt(8) + 2;
                n2 = rng.nextInt(8) + 2;
                ans = n1 * n2;
                symbol = "×";
            }
            mathProblems.add(new MathItem(n1, n2, symbol, ans));
        }
        mathProblemIndex = 0;
    }

    private void displayCurrentMathProblem() {
        if (mathProblemIndex < mathProblems.size()) {
            MathItem item = mathProblems.get(mathProblemIndex);
            tvMathProgress.setText(String.format(Locale.getDefault(), "Solve 3 math problems (%d/3)", mathProblemIndex + 1));
            tvMathEquation.setText(String.format(Locale.getDefault(), "%d %s %d =", item.n1, item.symbol, item.n2));
            mathCurrentInput = "";
            tvMathInput.setText("?");
        }
    }

    private void setupMathKeypad() {
        int[] buttonIds = new int[]{
                R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4,
                R.id.btnKey5, R.id.btnKey6, R.id.btnKey7, R.id.btnKey8, R.id.btnKey9
        };

        for (int id : buttonIds) {
            Button b = findViewById(id);
            if (b != null) {
                b.setOnClickListener(v -> {
                    if (mathCurrentInput.length() < 5) {
                        mathCurrentInput += ((Button) v).getText().toString();
                        tvMathInput.setText(mathCurrentInput);
                    }
                });
            }
        }

        Button btnClear = findViewById(R.id.btnKeyClear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                mathCurrentInput = "";
                tvMathInput.setText("?");
            });
        }

        Button btnOk = findViewById(R.id.btnKeyOk);
        if (btnOk != null) {
            btnOk.setOnClickListener(v -> {
                if (mathCurrentInput.isEmpty()) return;
                try {
                    int answer = Integer.parseInt(mathCurrentInput);
                    MathItem current = mathProblems.get(mathProblemIndex);
                    if (answer == current.answer) {
                        mathProblemIndex++;
                        if (mathProblemIndex >= mathProblems.size()) {
                            onTaskFullyCompleted();
                        } else {
                            displayCurrentMathProblem();
                        }
                    } else {
                        Toast.makeText(this, "Incorrect, try again!", Toast.LENGTH_SHORT).show();
                        mathCurrentInput = "";
                        tvMathInput.setText("?");
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    // --- SHAKE TASK LOGIC ---
    private void setupShakeSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            }
        }
        shakeCount = 0;
        tvShakeCount.setText(shakeCount + " / " + TARGET_SHAKES);
        pbShakeProgress.setProgress(0);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isTaskCompleted) return;

        float x = event.values[0] / 9.80665f;
        float y = event.values[1] / 9.80665f;
        float z = event.values[2] / 9.80665f;

        float gForce = (float) Math.sqrt(x * x + y * y + z * z);

        if (gForce > 2.3f) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > 250) {
                lastShakeTime = now;
                shakeCount++;
                tvShakeCount.setText(shakeCount + " / " + TARGET_SHAKES);
                pbShakeProgress.setProgress(shakeCount);

                if (shakeCount >= TARGET_SHAKES) {
                    if (sensorManager != null) {
                        sensorManager.unregisterListener(this);
                    }
                    onTaskFullyCompleted();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void onTaskFullyCompleted() {
        isTaskCompleted = true;
        layoutMathTask.setVisibility(View.GONE);
        layoutShakeTask.setVisibility(View.GONE);
        layoutTaskCompleted.setVisibility(View.VISIBLE);

        btnRingingDismiss.setEnabled(true);
        btnRingingDismiss.setAlpha(1.0f);
        Toast.makeText(this, "Task completed! You can now dismiss the alarm.", Toast.LENGTH_SHORT).show();
    }

    private void onSnoozeClicked() {
        AlarmService.stopAlarm(this);

        AlarmDatabase db = AlarmDatabase.getInstance(this);
        AlarmModel alarm = db.getAlarmById(alarmId);
        if (alarm != null) {
            AlarmScheduler.scheduleSnooze(this, alarm, snoozeDuration);
        }

        Toast.makeText(this, "Alarm snoozed for " + snoozeDuration + " minutes", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onDismissClicked() {
        if (!isTaskCompleted) {
            Toast.makeText(this, "Please complete the task to dismiss!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (remainingTasks != null && !remainingTasks.trim().isEmpty()) {
            String nextChain = remainingTasks.trim();
            remainingTasks = "";
            AlarmReceiver.launchNextMission(this, alarmId, label, soundUri, vibrate, snoozeDuration, nextChain);
            finish();
            return;
        }

        AlarmService.stopAlarm(this);

        AlarmDatabase db = AlarmDatabase.getInstance(this);
        AlarmModel alarm = db.getAlarmById(alarmId);
        if (alarm != null) {
            if ("Once".equalsIgnoreCase(alarm.getDays()) || alarm.getDays().isEmpty()) {
                alarm.setEnabled(false);
                db.updateAlarm(alarm);
            } else {
                AlarmScheduler.scheduleAlarm(this, alarm);
            }
        }

        Toast.makeText(this, "Alarm dismissed. Good morning!", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacksAndMessages(null);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent back press from dismissing alarm before task is completed
        if (isTaskCompleted) {
            super.onBackPressed();
        }
    }

    private static class MathItem {
        int n1, n2;
        String symbol;
        int answer;

        MathItem(int n1, int n2, String symbol, int answer) {
            this.n1 = n1;
            this.n2 = n2;
            this.symbol = symbol;
            this.answer = answer;
        }
    }
}

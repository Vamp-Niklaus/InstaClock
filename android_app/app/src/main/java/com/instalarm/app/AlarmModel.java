package com.instalarm.app;

import java.io.Serializable;
import java.util.Locale;

public class AlarmModel implements Serializable {
    private long id;
    private int hour;
    private int minute;
    private boolean isEnabled;
    private String days; // e.g. "Mon,Tue,Wed,Thu,Fri", "Daily", "Once"
    private String label;
    private String dismissTask; // "reels_10", "reels_25", "reels_50", "math", "shake", "none"
    private boolean vibrate;
    private String soundUri;
    private String soundName;
    private int snoozeDuration; // minutes, default 5

    public AlarmModel() {
        this.id = System.currentTimeMillis();
        this.hour = 7;
        this.minute = 0;
        this.isEnabled = true;
        this.days = "Daily";
        this.label = "Alarm";
        this.dismissTask = "reels_10";
        this.vibrate = true;
        this.soundUri = "";
        this.soundName = "Default alarm";
        this.snoozeDuration = 5;
    }

    public AlarmModel(long id, int hour, int minute, boolean isEnabled, String days, String label,
                      String dismissTask, boolean vibrate, String soundUri, String soundName,
                      int snoozeDuration) {
        this.id = id;
        this.hour = hour;
        this.minute = minute;
        this.isEnabled = isEnabled;
        this.days = days;
        this.label = label;
        this.dismissTask = dismissTask != null ? dismissTask : "reels_10";
        this.vibrate = vibrate;
        this.soundUri = soundUri != null ? soundUri : "";
        this.soundName = soundName != null ? soundName : "Default alarm";
        this.snoozeDuration = snoozeDuration > 0 ? snoozeDuration : 5;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getHour() { return hour; }
    public void setHour(int hour) { this.hour = hour; }

    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    public String getDays() { return days; }
    public void setDays(String days) { this.days = days; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDismissTask() { return dismissTask != null ? dismissTask : "reels_10"; }
    public void setDismissTask(String dismissTask) { this.dismissTask = dismissTask; }

    public boolean isVibrate() { return vibrate; }
    public void setVibrate(boolean vibrate) { this.vibrate = vibrate; }

    public String getSoundUri() { return soundUri; }
    public void setSoundUri(String soundUri) { this.soundUri = soundUri; }

    public String getSoundName() { return soundName; }
    public void setSoundName(String soundName) { this.soundName = soundName; }

    public int getSnoozeDuration() { return snoozeDuration > 0 ? snoozeDuration : 5; }
    public void setSnoozeDuration(int snoozeDuration) { this.snoozeDuration = snoozeDuration; }

    public String getFormattedTime() {
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format(Locale.getDefault(), "%d:%02d", displayHour, minute);
    }

    public String getAmPm() {
        return hour >= 12 ? "pm" : "am";
    }

    public String getTaskDisplayName() {
        if (dismissTask == null || "none".equalsIgnoreCase(dismissTask) || dismissTask.trim().isEmpty()) return "None";
        String[] parts = dismissTask.split("\\|");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String t = part.trim();
            if (t.isEmpty() || "none".equalsIgnoreCase(t)) continue;
            if (sb.length() > 0) sb.append(" + ");
            if (t.startsWith("reels")) {
                String count = t.contains("_") ? t.split("_")[1] : "10";
                sb.append("Reels (").append(count).append(")");
            } else if ("math".equalsIgnoreCase(t)) {
                sb.append("Math");
            } else if ("shake".equalsIgnoreCase(t)) {
                sb.append("Shake");
            } else {
                sb.append(t);
            }
        }
        return sb.length() > 0 ? sb.toString() : "None";
    }

    public String getDaysSummary() {
        if (days == null || days.trim().isEmpty() || "Once".equalsIgnoreCase(days)) return "Once";
        if ("Daily".equalsIgnoreCase(days)) return "Every day";
        if ("Mon,Tue,Wed,Thu,Fri".equalsIgnoreCase(days)) return "Mon–Fri";
        if ("Sat,Sun".equalsIgnoreCase(days) || "Sun,Sat".equalsIgnoreCase(days)) return "Weekends";
        return days;
    }
}

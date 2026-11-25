package com.instalarm.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class AlarmDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "alarms.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_ALARMS = "alarms";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_HOUR = "hour";
    public static final String COLUMN_MINUTE = "minute";
    public static final String COLUMN_IS_ENABLED = "is_enabled";
    public static final String COLUMN_DAYS = "days";
    public static final String COLUMN_LABEL = "label";
    public static final String COLUMN_DISMISS_TASK = "dismiss_task";
    public static final String COLUMN_VIBRATE = "vibrate";
    public static final String COLUMN_SOUND_URI = "sound_uri";
    public static final String COLUMN_SOUND_NAME = "sound_name";
    public static final String COLUMN_SNOOZE_DURATION = "snooze_duration";

    private static AlarmDatabase instance;

    public static synchronized AlarmDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new AlarmDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private AlarmDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_ALARMS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY, "
                + COLUMN_HOUR + " INTEGER NOT NULL, "
                + COLUMN_MINUTE + " INTEGER NOT NULL, "
                + COLUMN_IS_ENABLED + " INTEGER NOT NULL DEFAULT 1, "
                + COLUMN_DAYS + " TEXT NOT NULL, "
                + COLUMN_LABEL + " TEXT, "
                + COLUMN_DISMISS_TASK + " TEXT NOT NULL DEFAULT 'none', "
                + COLUMN_VIBRATE + " INTEGER NOT NULL DEFAULT 1, "
                + COLUMN_SOUND_URI + " TEXT, "
                + COLUMN_SOUND_NAME + " TEXT, "
                + COLUMN_SNOOZE_DURATION + " INTEGER NOT NULL DEFAULT 5"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ALARMS);
        onCreate(db);
    }

    public long insertAlarm(AlarmModel alarm) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, alarm.getId());
        values.put(COLUMN_HOUR, alarm.getHour());
        values.put(COLUMN_MINUTE, alarm.getMinute());
        values.put(COLUMN_IS_ENABLED, alarm.isEnabled() ? 1 : 0);
        values.put(COLUMN_DAYS, alarm.getDays());
        values.put(COLUMN_LABEL, alarm.getLabel());
        values.put(COLUMN_DISMISS_TASK, alarm.getDismissTask());
        values.put(COLUMN_VIBRATE, alarm.isVibrate() ? 1 : 0);
        values.put(COLUMN_SOUND_URI, alarm.getSoundUri());
        values.put(COLUMN_SOUND_NAME, alarm.getSoundName());
        values.put(COLUMN_SNOOZE_DURATION, alarm.getSnoozeDuration());

        return db.insertWithOnConflict(TABLE_ALARMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int updateAlarm(AlarmModel alarm) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_HOUR, alarm.getHour());
        values.put(COLUMN_MINUTE, alarm.getMinute());
        values.put(COLUMN_IS_ENABLED, alarm.isEnabled() ? 1 : 0);
        values.put(COLUMN_DAYS, alarm.getDays());
        values.put(COLUMN_LABEL, alarm.getLabel());
        values.put(COLUMN_DISMISS_TASK, alarm.getDismissTask());
        values.put(COLUMN_VIBRATE, alarm.isVibrate() ? 1 : 0);
        values.put(COLUMN_SOUND_URI, alarm.getSoundUri());
        values.put(COLUMN_SOUND_NAME, alarm.getSoundName());
        values.put(COLUMN_SNOOZE_DURATION, alarm.getSnoozeDuration());

        return db.update(TABLE_ALARMS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(alarm.getId())});
    }

    public int deleteAlarm(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_ALARMS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public List<AlarmModel> getAllAlarms() {
        List<AlarmModel> alarms = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ALARMS, null, null, null, null, null, COLUMN_HOUR + " ASC, " + COLUMN_MINUTE + " ASC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                int hour = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HOUR));
                int minute = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MINUTE));
                boolean isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_ENABLED)) == 1;
                String days = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DAYS));
                String label = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABEL));
                String dismissTask = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISMISS_TASK));
                boolean vibrate = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VIBRATE)) == 1;
                String soundUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOUND_URI));
                String soundName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOUND_NAME));
                int snoozeDuration = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SNOOZE_DURATION));

                AlarmModel alarm = new AlarmModel(id, hour, minute, isEnabled, days, label,
                        dismissTask, vibrate, soundUri, soundName, snoozeDuration);
                alarms.add(alarm);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return alarms;
    }

    public AlarmModel getAlarmById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ALARMS, null, COLUMN_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null);
        AlarmModel alarm = null;

        if (cursor != null && cursor.moveToFirst()) {
            int hour = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HOUR));
            int minute = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MINUTE));
            boolean isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_ENABLED)) == 1;
            String days = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DAYS));
            String label = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABEL));
            String dismissTask = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISMISS_TASK));
            boolean vibrate = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VIBRATE)) == 1;
            String soundUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOUND_URI));
            String soundName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOUND_NAME));
            int snoozeDuration = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SNOOZE_DURATION));

            alarm = new AlarmModel(id, hour, minute, isEnabled, days, label,
                    dismissTask, vibrate, soundUri, soundName, snoozeDuration);
            cursor.close();
        }
        return alarm;
    }
}

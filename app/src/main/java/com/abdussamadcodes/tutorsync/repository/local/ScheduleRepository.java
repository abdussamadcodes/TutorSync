package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.ScheduleEntry;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ScheduleRepository {

    public boolean insertSchedule(@NonNull RecurringSchedule schedule) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(ScheduleEntry.COLUMN_ID, schedule.getId());
            values.put(ScheduleEntry.COLUMN_ACCOUNT_ID, schedule.getAccountId());
            values.put(ScheduleEntry.COLUMN_STUDENT_ID, schedule.getStudentId());
            values.put(ScheduleEntry.COLUMN_SUBJECT_ID, schedule.getSubjectId());
            values.put(ScheduleEntry.COLUMN_DAY_OF_WEEK, schedule.getDayOfWeek());
            values.put(ScheduleEntry.COLUMN_START_TIME_MINUTES, schedule.getStartTimeMinutes());
            values.put(ScheduleEntry.COLUMN_DURATION_MINUTES, schedule.getDurationMinutes());
            values.put(ScheduleEntry.COLUMN_START_DATE, schedule.getStartDate());
            values.put(ScheduleEntry.COLUMN_END_DATE, schedule.getEndDate());
            values.put(ScheduleEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            return db.insert(ScheduleEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteScheduleSafely(@NonNull String scheduleId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            // Detach past occurrences so billing data isn't lost on cascade delete
            ContentValues occValues = new ContentValues();
            occValues.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, (String) null);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            db.update(OccurrenceEntry.TABLE_NAME, occValues,
                    OccurrenceEntry.COLUMN_SCHEDULE_ID + " = ? AND " + OccurrenceEntry.COLUMN_DATE + " < ?",
                    new String[]{scheduleId, String.valueOf(cal.getTimeInMillis())});

            return db.delete(ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_ID + " = ?", new String[]{scheduleId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<RecurringSchedule> getSchedulesForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<RecurringSchedule> schedules = new ArrayList<>();
        String selection = ScheduleEntry.COLUMN_ACCOUNT_ID + " = ?";
        try (Cursor cursor = db.query(ScheduleEntry.TABLE_NAME, null, selection, new String[]{accountId}, null, null, ScheduleEntry.COLUMN_DAY_OF_WEEK + " ASC")) {
            while (cursor.moveToNext()) {
                schedules.add(new RecurringSchedule(
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_STUDENT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_SUBJECT_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_DAY_OF_WEEK)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_START_TIME_MINUTES)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_DURATION_MINUTES)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_START_DATE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_END_DATE))
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return schedules;
    }
}
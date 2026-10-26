package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class OccurrenceRepository {

    public boolean insertOccurrence(@NonNull ClassOccurrence occurrence) {
        return insertOccurrenceWithGroup(occurrence, null);
    }

    public boolean insertOccurrenceWithGroup(@NonNull ClassOccurrence occurrence, @Nullable String scheduleGroupId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(OccurrenceEntry.COLUMN_ID, occurrence.getId());
            values.put(OccurrenceEntry.COLUMN_ACCOUNT_ID, occurrence.getAccountId());
            values.put(OccurrenceEntry.COLUMN_STUDENT_ID, occurrence.getStudentId());
            values.put(OccurrenceEntry.COLUMN_SUBJECT_ID, occurrence.getSubjectId());
            values.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, occurrence.getScheduleId());
            values.put(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, scheduleGroupId);
            values.put(OccurrenceEntry.COLUMN_DATE, occurrence.getDate());
            values.put(OccurrenceEntry.COLUMN_START_TIME_MINUTES, occurrence.getStartTimeMinutes());
            values.put(OccurrenceEntry.COLUMN_DURATION_MINUTES, occurrence.getDurationMinutes());

            return db.insert(OccurrenceEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteOccurrence(@NonNull String occurrenceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            return db.delete(OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_ID + " = ?", new String[]{occurrenceId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public void deleteFutureOccurrencesByScheduleId(@NonNull String scheduleId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            String where = OccurrenceEntry.COLUMN_SCHEDULE_ID + " = ? AND " +
                    OccurrenceEntry.COLUMN_DATE + " >= ? AND " +
                    OccurrenceEntry.COLUMN_ID + " NOT IN (SELECT " +
                    TutorSyncContract.AttendanceEntry.COLUMN_OCCURRENCE_ID + " FROM " +
                    TutorSyncContract.AttendanceEntry.TABLE_NAME + ")";

            db.delete(OccurrenceEntry.TABLE_NAME, where, new String[]{scheduleId, String.valueOf(cal.getTimeInMillis())});
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<ClassOccurrence> getOccurrencesForToday(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<ClassOccurrence> occurrences = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayMillis = cal.getTimeInMillis();

        String selection = OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                OccurrenceEntry.COLUMN_DATE + " = ? AND " +
                OccurrenceEntry.COLUMN_ID + " NOT IN (SELECT " +
                TutorSyncContract.AttendanceEntry.COLUMN_OCCURRENCE_ID + " FROM " +
                TutorSyncContract.AttendanceEntry.TABLE_NAME + ")";
        String[] selectionArgs = {accountId, String.valueOf(todayMillis)};

        try (Cursor cursor = db.query(OccurrenceEntry.TABLE_NAME, null, selection, selectionArgs, null, null, OccurrenceEntry.COLUMN_START_TIME_MINUTES + " ASC")) {
            while (cursor.moveToNext()) {
                occurrences.add(new ClassOccurrence(
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_STUDENT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SUBJECT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SCHEDULE_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DATE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_START_TIME_MINUTES)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DURATION_MINUTES))
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return occurrences;
    }
}
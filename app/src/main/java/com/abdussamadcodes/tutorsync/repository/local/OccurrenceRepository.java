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
            long now = System.currentTimeMillis();
            long modTime = occurrence.getUpdatedAt() > 0 ? occurrence.getUpdatedAt() : now;

            ContentValues values = new ContentValues();
            values.put(OccurrenceEntry.COLUMN_ID, TutorSyncContract.ensureValidUuid(occurrence.getId()));
            values.put(OccurrenceEntry.COLUMN_ACCOUNT_ID, occurrence.getAccountId());
            values.put(OccurrenceEntry.COLUMN_STUDENT_ID, occurrence.getStudentId());
            values.put(OccurrenceEntry.COLUMN_SUBJECT_ID, occurrence.getSubjectId());
            values.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, occurrence.getScheduleId());
            values.put(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, scheduleGroupId);
            values.put(OccurrenceEntry.COLUMN_DATE, occurrence.getDate());
            values.put(OccurrenceEntry.COLUMN_START_TIME_MINUTES, occurrence.getStartTimeMinutes());
            values.put(OccurrenceEntry.COLUMN_DURATION_MINUTES, occurrence.getDurationMinutes());
            values.put(OccurrenceEntry.COLUMN_CREATED_AT, modTime);
            values.put(OccurrenceEntry.COLUMN_UPDATED_AT, modTime);
            values.put(OccurrenceEntry.COLUMN_LAST_MODIFIED, modTime);

            return db.insert(OccurrenceEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteOccurrence(@NonNull String accountId, @NonNull String occurrenceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            return db.delete(OccurrenceEntry.TABLE_NAME,
                    OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + OccurrenceEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, occurrenceId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteOccurrence(@NonNull String occurrenceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            return db.delete(OccurrenceEntry.TABLE_NAME,
                    OccurrenceEntry.COLUMN_ID + " = ?", new String[]{occurrenceId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public void deleteFutureOccurrencesByScheduleId(@NonNull String accountId, @NonNull String scheduleId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            String where = OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                    OccurrenceEntry.COLUMN_SCHEDULE_ID + " = ? AND " +
                    OccurrenceEntry.COLUMN_DATE + " >= ? AND " +
                    OccurrenceEntry.COLUMN_ID + " NOT IN (SELECT " +
                    TutorSyncContract.AttendanceEntry.COLUMN_OCCURRENCE_ID + " FROM " +
                    TutorSyncContract.AttendanceEntry.TABLE_NAME + " WHERE " +
                    TutorSyncContract.AttendanceEntry.COLUMN_ACCOUNT_ID + " = ?)";

            db.delete(OccurrenceEntry.TABLE_NAME, where,
                    new String[]{accountId, scheduleId, String.valueOf(cal.getTimeInMillis()), accountId});
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
                    OccurrenceEntry.COLUMN_ID + " NOT IN (SELECT a." +
                    TutorSyncContract.AttendanceEntry.COLUMN_OCCURRENCE_ID + " FROM " +
                    TutorSyncContract.AttendanceEntry.TABLE_NAME + " a WHERE a." +
                    TutorSyncContract.AttendanceEntry.COLUMN_ACCOUNT_ID + " = " +
                    OccurrenceEntry.TABLE_NAME + "." + OccurrenceEntry.COLUMN_ACCOUNT_ID + ")";

            db.delete(OccurrenceEntry.TABLE_NAME, where,
                    new String[]{scheduleId, String.valueOf(cal.getTimeInMillis())});
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
                TutorSyncContract.AttendanceEntry.TABLE_NAME + " WHERE " +
                TutorSyncContract.AttendanceEntry.COLUMN_ACCOUNT_ID + " = ?)";
        String[] selectionArgs = {accountId, String.valueOf(todayMillis), accountId};

        try (Cursor cursor = db.query(OccurrenceEntry.TABLE_NAME, null, selection, selectionArgs,
                null, null, OccurrenceEntry.COLUMN_START_TIME_MINUTES + " ASC")) {
            while (cursor.moveToNext()) {
                long lastMod = 0L;
                int lastModIdx = cursor.getColumnIndex(OccurrenceEntry.COLUMN_LAST_MODIFIED);
                if (lastModIdx != -1 && !cursor.isNull(lastModIdx)) {
                    lastMod = cursor.getLong(lastModIdx);
                }
                occurrences.add(new ClassOccurrence(
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_STUDENT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SUBJECT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SCHEDULE_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DATE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_START_TIME_MINUTES)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DURATION_MINUTES)),
                        lastMod
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return occurrences;
    }
}
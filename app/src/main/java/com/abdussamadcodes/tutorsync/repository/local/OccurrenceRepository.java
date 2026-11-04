package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.RemovedOccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.SubjectEntry;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

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

    /**
     * Deletes one class occurrence (account-scoped).
     *
     * - An occurrence that already has attendance recorded is never deleted (returns false).
     * - When a regular (non-makeup) occurrence of a recurring schedule is removed on purpose, the
     *   removal is remembered so the automatic top-up never recreates it.
     */
    public boolean deleteOccurrence(@NonNull String accountId, @NonNull String occurrenceId) {
        if (accountId.trim().isEmpty() || occurrenceId.trim().isEmpty()) {
            return false;
        }
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            String scheduleId = null;
            long dateMillis = 0L;
            boolean isMakeup = false;
            boolean found = false;
            try (Cursor c = db.query(OccurrenceEntry.TABLE_NAME,
                    new String[]{OccurrenceEntry.COLUMN_SCHEDULE_ID, OccurrenceEntry.COLUMN_DATE,
                            OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID},
                    OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + OccurrenceEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, occurrenceId}, null, null, null, "1")) {
                if (c.moveToFirst()) {
                    found = true;
                    scheduleId = c.isNull(0) ? null : c.getString(0);
                    dateMillis = c.getLong(1);
                    isMakeup = !c.isNull(2) && !c.getString(2).trim().isEmpty();
                }
            }
            if (!found) {
                return false;
            }

            try (Cursor a = db.query(TutorSyncContract.AttendanceEntry.TABLE_NAME,
                    new String[]{TutorSyncContract.AttendanceEntry.COLUMN_ID},
                    TutorSyncContract.AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            TutorSyncContract.AttendanceEntry.COLUMN_OCCURRENCE_ID + " = ?",
                    new String[]{accountId, occurrenceId}, null, null, null, "1")) {
                if (a.moveToFirst()) {
                    return false;
                }
            }

            if (scheduleId != null && !scheduleId.trim().isEmpty() && !isMakeup) {
                long now = System.currentTimeMillis();
                ContentValues tomb = new ContentValues();
                tomb.put(RemovedOccurrenceEntry.COLUMN_ID, UUID.nameUUIDFromBytes(
                        ("RMOCC:" + scheduleId + ":" + dateMillis).getBytes(StandardCharsets.UTF_8)).toString());
                tomb.put(RemovedOccurrenceEntry.COLUMN_ACCOUNT_ID, accountId);
                tomb.put(RemovedOccurrenceEntry.COLUMN_SCHEDULE_ID, scheduleId);
                tomb.put(RemovedOccurrenceEntry.COLUMN_OCCURRENCE_DATE, dateMillis);
                tomb.put(RemovedOccurrenceEntry.COLUMN_CREATED_AT, now);
                tomb.put(RemovedOccurrenceEntry.COLUMN_UPDATED_AT, now);
                tomb.put(RemovedOccurrenceEntry.COLUMN_LAST_MODIFIED, now);
                db.insertWithOnConflict(RemovedOccurrenceEntry.TABLE_NAME, null, tomb,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }

            boolean deleted = db.delete(OccurrenceEntry.TABLE_NAME,
                    OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + OccurrenceEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, occurrenceId}) > 0;
            db.setTransactionSuccessful();
            return deleted;
        } finally {
            db.endTransaction();
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
                    TutorSyncContract.AttendanceEntry.COLUMN_ACCOUNT_ID + " = ?) AND " +
                    // M4: makeup classes are never deleted by a bulk schedule clean-up
                    OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID + " IS NULL";

            db.delete(OccurrenceEntry.TABLE_NAME, where,
                    new String[]{accountId, scheduleId, String.valueOf(cal.getTimeInMillis()), accountId});
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
                TutorSyncContract.AttendanceEntry.COLUMN_ACCOUNT_ID + " = ?) AND " +
                OccurrenceEntry.COLUMN_STUDENT_ID + " IN (SELECT " +
                StudentEntry.COLUMN_ID + " FROM " + StudentEntry.TABLE_NAME + " WHERE " +
                StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                StudentEntry.COLUMN_IS_DELETED + " = 0) AND " +
                OccurrenceEntry.COLUMN_SUBJECT_ID + " IN (SELECT " +
                SubjectEntry.COLUMN_ID + " FROM " + SubjectEntry.TABLE_NAME + " WHERE " +
                SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                SubjectEntry.COLUMN_IS_DELETED + " = 0)";
        String[] selectionArgs = {accountId, String.valueOf(todayMillis), accountId, accountId, accountId};

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
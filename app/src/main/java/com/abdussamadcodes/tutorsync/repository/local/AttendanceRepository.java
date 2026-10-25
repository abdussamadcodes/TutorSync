package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AttendanceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import java.util.Calendar;

public class AttendanceRepository {

    public int recordAttendance(@NonNull AttendanceRecord record) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(AttendanceEntry.COLUMN_ID, record.getId());
            values.put(AttendanceEntry.COLUMN_ACCOUNT_ID, record.getAccountId());
            values.put(AttendanceEntry.COLUMN_OCCURRENCE_ID, record.getOccurrenceId());
            values.put(AttendanceEntry.COLUMN_STATUS, record.getStatus());
            values.put(AttendanceEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            long result = db.insertOrThrow(AttendanceEntry.TABLE_NAME, null, values);
            return result != -1 ? 1 : 0;
        } catch (SQLiteConstraintException e) {
            return -1;
        } catch (Exception e) {
            return 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public int getPresentCountForStudentThisMonth(@NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        int presentCount = 0;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startOfMonth = cal.getTimeInMillis();

        cal.add(Calendar.MONTH, 1);
        long endOfMonth = cal.getTimeInMillis();

        String query = "SELECT COUNT(*) FROM " + AttendanceEntry.TABLE_NAME + " a " +
                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                "WHERE o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = ? " +
                "AND a." + AttendanceEntry.COLUMN_STATUS + " = 'PRESENT' " +
                "AND o." + OccurrenceEntry.COLUMN_DATE + " >= ? " +
                "AND o." + OccurrenceEntry.COLUMN_DATE + " < ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{studentId, String.valueOf(startOfMonth), String.valueOf(endOfMonth)})) {
            if (cursor.moveToFirst()) {
                presentCount = cursor.getInt(0);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }

        return presentCount;
    }
}
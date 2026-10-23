package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AttendanceEntry;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;

public class AttendanceRepository {

    // Returns: 1 for Success, -1 for Duplicate (already recorded), 0 for general error
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
            // Triggers due to our UNIQUE(occurrence_id) constraint preventing double-attendance
            return -1;
        } catch (Exception e) {
            return 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }
}
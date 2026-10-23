package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import java.util.ArrayList;
import java.util.List;

public class OccurrenceRepository {

    public boolean insertOccurrence(@NonNull ClassOccurrence occurrence) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(OccurrenceEntry.COLUMN_ID, occurrence.getId());
            values.put(OccurrenceEntry.COLUMN_ACCOUNT_ID, occurrence.getAccountId());
            values.put(OccurrenceEntry.COLUMN_STUDENT_ID, occurrence.getStudentId());
            values.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, occurrence.getScheduleId());
            values.put(OccurrenceEntry.COLUMN_DATE, occurrence.getDate());
            values.put(OccurrenceEntry.COLUMN_START_TIME_MINUTES, occurrence.getStartTimeMinutes());
            values.put(OccurrenceEntry.COLUMN_DURATION_MINUTES, occurrence.getDurationMinutes());

            return db.insert(OccurrenceEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<ClassOccurrence> getOccurrencesForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<ClassOccurrence> occurrences = new ArrayList<>();

        String selection = OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ?";
        String[] selectionArgs = {accountId};

        try (Cursor cursor = db.query(OccurrenceEntry.TABLE_NAME, null, selection, selectionArgs, null, null, OccurrenceEntry.COLUMN_DATE + " ASC")) {
            while (cursor.moveToNext()) {
                occurrences.add(new ClassOccurrence(
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_STUDENT_ID)),
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
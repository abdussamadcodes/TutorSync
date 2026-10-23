package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.SubjectEntry;
import com.abdussamadcodes.tutorsync.model.Subject;
import java.util.ArrayList;
import java.util.List;

public class SubjectRepository {

    public boolean insertSubject(@NonNull Subject subject) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(SubjectEntry.COLUMN_ID, subject.getId());
            values.put(SubjectEntry.COLUMN_ACCOUNT_ID, subject.getAccountId());
            values.put(SubjectEntry.COLUMN_TITLE, subject.getTitle());
            values.put(SubjectEntry.COLUMN_LEVEL, subject.getLevel());
            values.put(SubjectEntry.COLUMN_COLOR_TAG, subject.getColorTag());
            values.put(SubjectEntry.COLUMN_IS_ACTIVE, subject.isActive() ? 1 : 0);
            values.put(SubjectEntry.COLUMN_IS_DELETED, 0);
            values.put(SubjectEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            return db.insert(SubjectEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<Subject> getActiveSubjectsForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Subject> subjects = new ArrayList<>();

        // Data Isolation: Strict account_id filtering
        String selection = SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                SubjectEntry.COLUMN_IS_DELETED + " = 0 AND " +
                SubjectEntry.COLUMN_IS_ACTIVE + " = 1";
        String[] selectionArgs = {accountId};

        try (Cursor cursor = db.query(SubjectEntry.TABLE_NAME, null, selection, selectionArgs, null, null, SubjectEntry.COLUMN_TITLE + " ASC")) {
            while (cursor.moveToNext()) {
                subjects.add(new Subject(
                        cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_TITLE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_LEVEL)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_COLOR_TAG)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_IS_ACTIVE)) == 1
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return subjects;
    }
}
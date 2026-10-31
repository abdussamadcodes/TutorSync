package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.SubjectEntry;
import com.abdussamadcodes.tutorsync.model.Subject;
import java.util.ArrayList;
import java.util.List;

public class SubjectRepository {

    public boolean insertSubject(@NonNull Subject subject) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long now = System.currentTimeMillis();
            long modTime = subject.getUpdatedAt() > 0 ? subject.getUpdatedAt() : now;

            ContentValues values = new ContentValues();
            values.put(SubjectEntry.COLUMN_ID, TutorSyncContract.ensureValidUuid(subject.getId()));
            values.put(SubjectEntry.COLUMN_ACCOUNT_ID, subject.getAccountId());
            values.put(SubjectEntry.COLUMN_TITLE, subject.getTitle());
            values.put(SubjectEntry.COLUMN_LEVEL, subject.getLevel());
            values.put(SubjectEntry.COLUMN_IS_ACTIVE, subject.isActive() ? 1 : 0);
            values.put(SubjectEntry.COLUMN_IS_DELETED, 0);
            values.put(SubjectEntry.COLUMN_CREATED_AT, modTime);
            values.put(SubjectEntry.COLUMN_UPDATED_AT, modTime);
            values.put(SubjectEntry.COLUMN_LAST_MODIFIED, modTime);

            return db.insert(SubjectEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean updateSubject(@NonNull Subject subject) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put(SubjectEntry.COLUMN_TITLE, subject.getTitle());
            values.put(SubjectEntry.COLUMN_LEVEL, subject.getLevel());
            values.put(SubjectEntry.COLUMN_IS_ACTIVE, subject.isActive() ? 1 : 0);
            values.put(SubjectEntry.COLUMN_UPDATED_AT, now);
            values.put(SubjectEntry.COLUMN_LAST_MODIFIED, now);

            return db.update(SubjectEntry.TABLE_NAME, values,
                    SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + SubjectEntry.COLUMN_ID + " = ?",
                    new String[]{subject.getAccountId(), subject.getId()}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @Nullable
    public Subject getSubjectById(@NonNull String accountId, @NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor cursor = db.query(SubjectEntry.TABLE_NAME, null,
                SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + SubjectEntry.COLUMN_ID + " = ?",
                new String[]{accountId, subjectId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return mapCursorToSubject(cursor);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    public boolean deleteSubject(@NonNull String accountId, @NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            return db.delete(SubjectEntry.TABLE_NAME,
                    SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + SubjectEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, subjectId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteSubject(@NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            return db.delete(SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_ID + " = ?", new String[]{subjectId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<Subject> getActiveSubjectsForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Subject> subjects = new ArrayList<>();
        String selection = SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                SubjectEntry.COLUMN_IS_DELETED + " = 0 AND " +
                SubjectEntry.COLUMN_IS_ACTIVE + " = 1";
        try (Cursor cursor = db.query(SubjectEntry.TABLE_NAME, null, selection,
                new String[]{accountId}, null, null, SubjectEntry.COLUMN_TITLE + " ASC")) {
            while (cursor.moveToNext()) {
                subjects.add(mapCursorToSubject(cursor));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return subjects;
    }

    @NonNull
    private Subject mapCursorToSubject(@NonNull Cursor cursor) {
        long lastMod = 0L;
        int lastModIdx = cursor.getColumnIndex(SubjectEntry.COLUMN_LAST_MODIFIED);
        if (lastModIdx != -1 && !cursor.isNull(lastModIdx)) {
            lastMod = cursor.getLong(lastModIdx);
        }
        if (lastMod <= 0L) {
            int upIdx = cursor.getColumnIndex(SubjectEntry.COLUMN_UPDATED_AT);
            if (upIdx != -1 && !cursor.isNull(upIdx)) {
                lastMod = cursor.getLong(upIdx);
            }
        }
        return new Subject(
                cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_ACCOUNT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_LEVEL)),
                cursor.getInt(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_IS_ACTIVE)) == 1,
                lastMod
        );
    }
}
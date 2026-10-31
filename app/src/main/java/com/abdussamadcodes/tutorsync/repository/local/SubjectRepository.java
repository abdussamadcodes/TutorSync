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
import java.util.Locale;

public class SubjectRepository {

    private static final String COLUMN_SUBJECT_UNIQUE_ID = "subject_unique_id";

    private void ensureSubjectUniqueIdSupport(@NonNull SQLiteDatabase db, @Nullable String accountId) {
        boolean hasColumn = false;
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + SubjectEntry.TABLE_NAME + ")", null)) {
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (nameIdx != -1 && COLUMN_SUBJECT_UNIQUE_ID.equalsIgnoreCase(c.getString(nameIdx))) {
                    hasColumn = true;
                    break;
                }
            }
        }
        if (!hasColumn) {
            db.execSQL("ALTER TABLE " + SubjectEntry.TABLE_NAME +
                    " ADD COLUMN " + COLUMN_SUBJECT_UNIQUE_ID + " TEXT");
        }

        if (accountId != null && !accountId.isEmpty()) {
            migrateLegacySubjectIds(db, accountId);
        }
    }

    private void migrateLegacySubjectIds(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        int maxSeq = getMaxSubjectSequenceForAccount(db, accountId);
        List<String> missingIds = new ArrayList<>();

        try (Cursor c = db.query(
                SubjectEntry.TABLE_NAME,
                new String[]{SubjectEntry.COLUMN_ID, COLUMN_SUBJECT_UNIQUE_ID, SubjectEntry.COLUMN_TITLE},
                SubjectEntry.COLUMN_ACCOUNT_ID + " = ?",
                new String[]{accountId},
                null, null,
                SubjectEntry.COLUMN_CREATED_AT + " ASC, " + SubjectEntry.COLUMN_ID + " ASC")) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                String code = c.isNull(1) ? null : c.getString(1);
                String title = c.isNull(2) ? "" : c.getString(2);
                String cleanTitle = Subject.stripSubjectIdSuffix(title);

                if (!cleanTitle.equals(title)) {
                    ContentValues cleanTitleValues = new ContentValues();
                    cleanTitleValues.put(SubjectEntry.COLUMN_TITLE, cleanTitle);
                    db.update(SubjectEntry.TABLE_NAME, cleanTitleValues,
                            SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + SubjectEntry.COLUMN_ID + " = ?",
                            new String[]{accountId, id});
                }

                if (code == null || code.trim().isEmpty()) {
                    missingIds.add(id);
                }
            }
        }

        for (String subjectId : missingIds) {
            maxSeq++;
            String newCode = String.format(Locale.US, "SUB-%04d", maxSeq);
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_SUBJECT_UNIQUE_ID, newCode);
            db.update(SubjectEntry.TABLE_NAME, cv,
                    SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + SubjectEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, subjectId});
        }
    }

    private int getMaxSubjectSequenceForAccount(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        int max = 0;
        try (Cursor c = db.query(
                SubjectEntry.TABLE_NAME,
                new String[]{COLUMN_SUBJECT_UNIQUE_ID},
                SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + COLUMN_SUBJECT_UNIQUE_ID + " IS NOT NULL",
                new String[]{accountId},
                null, null, null)) {
            while (c.moveToNext()) {
                String code = c.getString(0);
                if (code != null && code.startsWith("SUB-")) {
                    try {
                        int num = Integer.parseInt(code.substring(4).trim());
                        if (num > max) max = num;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return max;
    }

    @NonNull
    private String resolveOrGenerateSubjectUniqueId(@NonNull SQLiteDatabase db, @NonNull Subject subject) {
        String accountId = subject.getAccountId();
        String existingId = TutorSyncContract.ensureValidUuid(subject.getId());

        try (Cursor c = db.query(
                SubjectEntry.TABLE_NAME,
                new String[]{COLUMN_SUBJECT_UNIQUE_ID},
                SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + SubjectEntry.COLUMN_ID + " = ?",
                new String[]{accountId, existingId},
                null, null, null)) {
            if (c.moveToFirst() && !c.isNull(0)) {
                String dbCode = c.getString(0);
                if (dbCode != null && !dbCode.trim().isEmpty()) {
                    return dbCode.trim();
                }
            }
        }

        if (subject.getSubjectUniqueId() != null && !subject.getSubjectUniqueId().trim().isEmpty()) {
            return subject.getSubjectUniqueId().trim();
        }

        int nextSeq = getMaxSubjectSequenceForAccount(db, accountId) + 1;
        return String.format(Locale.US, "SUB-%04d", nextSeq);
    }

    public boolean insertSubject(@NonNull Subject subject) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ensureSubjectUniqueIdSupport(db, subject.getAccountId());

            long now = System.currentTimeMillis();
            long modTime = subject.getUpdatedAt() > 0 ? subject.getUpdatedAt() : now;
            String uniqueCode = resolveOrGenerateSubjectUniqueId(db, subject);

            ContentValues values = new ContentValues();
            values.put(SubjectEntry.COLUMN_ID, TutorSyncContract.ensureValidUuid(subject.getId()));
            values.put(SubjectEntry.COLUMN_ACCOUNT_ID, subject.getAccountId());
            values.put(COLUMN_SUBJECT_UNIQUE_ID, uniqueCode);
            values.put(SubjectEntry.COLUMN_TITLE, subject.getRawTitle());
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
            ensureSubjectUniqueIdSupport(db, subject.getAccountId());

            long now = System.currentTimeMillis();
            String uniqueCode = resolveOrGenerateSubjectUniqueId(db, subject);

            ContentValues values = new ContentValues();
            values.put(COLUMN_SUBJECT_UNIQUE_ID, uniqueCode);
            values.put(SubjectEntry.COLUMN_TITLE, subject.getRawTitle());
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
        try {
            ensureSubjectUniqueIdSupport(db, accountId);
            try (Cursor cursor = db.query(SubjectEntry.TABLE_NAME, null,
                    SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + SubjectEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, subjectId}, null, null, null)) {
                if (cursor.moveToFirst()) {
                    return mapCursorToSubject(cursor);
                }
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
        try {
            ensureSubjectUniqueIdSupport(db, accountId);
            String selection = SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                    SubjectEntry.COLUMN_IS_DELETED + " = 0 AND " +
                    SubjectEntry.COLUMN_IS_ACTIVE + " = 1";
            try (Cursor cursor = db.query(SubjectEntry.TABLE_NAME, null, selection,
                    new String[]{accountId}, null, null,
                    SubjectEntry.COLUMN_TITLE + " ASC, " + COLUMN_SUBJECT_UNIQUE_ID + " ASC")) {
                while (cursor.moveToNext()) {
                    subjects.add(mapCursorToSubject(cursor));
                }
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

        String uniqueCode = null;
        int codeIdx = cursor.getColumnIndex(COLUMN_SUBJECT_UNIQUE_ID);
        if (codeIdx != -1 && !cursor.isNull(codeIdx)) {
            uniqueCode = cursor.getString(codeIdx);
        }

        return new Subject(
                cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_ACCOUNT_ID)),
                uniqueCode,
                cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_LEVEL)),
                cursor.getInt(cursor.getColumnIndexOrThrow(SubjectEntry.COLUMN_IS_ACTIVE)) == 1,
                lastMod
        );
    }
}
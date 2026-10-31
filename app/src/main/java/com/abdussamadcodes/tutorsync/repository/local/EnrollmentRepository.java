package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentSubjectEntry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EnrollmentRepository {

    @NonNull
    private static String buildEnrollmentUuid(@NonNull String studentId, @NonNull String subjectId) {
        return UUID.nameUUIDFromBytes(("ENR:" + studentId + ":" + subjectId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Nullable
    private String resolveAccountIdForStudent(@NonNull SQLiteDatabase db, @NonNull String studentId) {
        try (Cursor c = db.query(StudentEntry.TABLE_NAME,
                new String[]{StudentEntry.COLUMN_ACCOUNT_ID},
                StudentEntry.COLUMN_ID + " = ?",
                new String[]{studentId}, null, null, StudentEntry.COLUMN_LAST_MODIFIED + " DESC", "1")) {
            if (c.moveToFirst()) {
                return c.getString(0);
            }
        }
        return null;
    }

    public boolean enrollStudentInSubject(@NonNull String accountId, @NonNull String studentId, @NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put(StudentSubjectEntry.COLUMN_ID, buildEnrollmentUuid(studentId, subjectId));
            values.put(StudentSubjectEntry.COLUMN_ACCOUNT_ID, accountId);
            values.put(StudentSubjectEntry.COLUMN_STUDENT_ID, studentId);
            values.put(StudentSubjectEntry.COLUMN_SUBJECT_ID, subjectId);
            values.put(StudentSubjectEntry.COLUMN_CREATED_AT, now);
            values.put(StudentSubjectEntry.COLUMN_UPDATED_AT, now);
            values.put(StudentSubjectEntry.COLUMN_LAST_MODIFIED, now);

            return db.insertWithOnConflict(StudentSubjectEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
        } catch (Exception e) {
            return false;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean enrollStudentInSubject(@NonNull String studentId, @NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String accountId;
        try {
            accountId = resolveAccountIdForStudent(db, studentId);
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        if (accountId == null || accountId.isEmpty()) return false;
        return enrollStudentInSubject(accountId, studentId, subjectId);
    }

    public boolean unenrollStudentFromSubject(@NonNull String accountId, @NonNull String studentId, @NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            String whereClause = StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                    StudentSubjectEntry.COLUMN_STUDENT_ID + " = ? AND " +
                    StudentSubjectEntry.COLUMN_SUBJECT_ID + " = ?";
            String[] whereArgs = {accountId, studentId, subjectId};
            return db.delete(StudentSubjectEntry.TABLE_NAME, whereClause, whereArgs) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean unenrollStudentFromSubject(@NonNull String studentId, @NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String accountId;
        try {
            accountId = resolveAccountIdForStudent(db, studentId);
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        if (accountId == null || accountId.isEmpty()) return false;
        return unenrollStudentFromSubject(accountId, studentId, subjectId);
    }

    @NonNull
    public List<String> getEnrolledSubjectIdsForStudent(@NonNull String accountId, @NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<String> subjectIds = new ArrayList<>();

        String selection = StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                StudentSubjectEntry.COLUMN_STUDENT_ID + " = ?";
        String[] selectionArgs = {accountId, studentId};

        try (Cursor cursor = db.query(StudentSubjectEntry.TABLE_NAME,
                new String[]{StudentSubjectEntry.COLUMN_SUBJECT_ID},
                selection, selectionArgs, null, null, null)) {
            while (cursor.moveToNext()) {
                subjectIds.add(cursor.getString(0));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return subjectIds;
    }

    @NonNull
    public List<String> getEnrolledSubjectIdsForStudent(@NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String accountId;
        try {
            accountId = resolveAccountIdForStudent(db, studentId);
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        if (accountId == null || accountId.isEmpty()) return new ArrayList<>();
        return getEnrolledSubjectIdsForStudent(accountId, studentId);
    }

    public boolean syncStudentEnrollments(@NonNull String accountId, @NonNull String studentId, @NonNull Set<String> targetSubjectIds) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            List<String> currentEnrollments = new ArrayList<>();
            try (Cursor cursor = db.query(StudentSubjectEntry.TABLE_NAME,
                    new String[]{StudentSubjectEntry.COLUMN_SUBJECT_ID},
                    StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentSubjectEntry.COLUMN_STUDENT_ID + " = ?",
                    new String[]{accountId, studentId}, null, null, null)) {
                while (cursor.moveToNext()) {
                    currentEnrollments.add(cursor.getString(0));
                }
            }

            for (String currentId : currentEnrollments) {
                if (!targetSubjectIds.contains(currentId)) {
                    db.delete(StudentSubjectEntry.TABLE_NAME,
                            StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                    StudentSubjectEntry.COLUMN_STUDENT_ID + " = ? AND " +
                                    StudentSubjectEntry.COLUMN_SUBJECT_ID + " = ?",
                            new String[]{accountId, studentId, currentId});
                }
            }

            long now = System.currentTimeMillis();
            for (String targetId : targetSubjectIds) {
                if (!currentEnrollments.contains(targetId)) {
                    ContentValues values = new ContentValues();
                    values.put(StudentSubjectEntry.COLUMN_ID, buildEnrollmentUuid(studentId, targetId));
                    values.put(StudentSubjectEntry.COLUMN_ACCOUNT_ID, accountId);
                    values.put(StudentSubjectEntry.COLUMN_STUDENT_ID, studentId);
                    values.put(StudentSubjectEntry.COLUMN_SUBJECT_ID, targetId);
                    values.put(StudentSubjectEntry.COLUMN_CREATED_AT, now);
                    values.put(StudentSubjectEntry.COLUMN_UPDATED_AT, now);
                    values.put(StudentSubjectEntry.COLUMN_LAST_MODIFIED, now);
                    db.insertWithOnConflict(StudentSubjectEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean syncStudentEnrollments(@NonNull String studentId, @NonNull Set<String> targetSubjectIds) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String accountId;
        try {
            accountId = resolveAccountIdForStudent(db, studentId);
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        if (accountId == null || accountId.isEmpty()) return false;
        return syncStudentEnrollments(accountId, studentId, targetSubjectIds);
    }
}
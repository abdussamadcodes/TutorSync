package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentSubjectEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EnrollmentRepository {

    public boolean enrollStudentInSubject(@NonNull String studentId, @NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(StudentSubjectEntry.COLUMN_ID, UUID.randomUUID().toString());
            values.put(StudentSubjectEntry.COLUMN_STUDENT_ID, studentId);
            values.put(StudentSubjectEntry.COLUMN_SUBJECT_ID, subjectId);

            return db.insert(StudentSubjectEntry.TABLE_NAME, null, values) != -1;
        } catch (Exception e) {
            return false; // Unique constraint might fail if already enrolled
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean unenrollStudentFromSubject(@NonNull String studentId, @NonNull String subjectId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            String whereClause = StudentSubjectEntry.COLUMN_STUDENT_ID + " = ? AND " + StudentSubjectEntry.COLUMN_SUBJECT_ID + " = ?";
            String[] whereArgs = {studentId, subjectId};
            return db.delete(StudentSubjectEntry.TABLE_NAME, whereClause, whereArgs) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<String> getEnrolledSubjectIdsForStudent(@NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<String> subjectIds = new ArrayList<>();

        String selection = StudentSubjectEntry.COLUMN_STUDENT_ID + " = ?";
        String[] selectionArgs = {studentId};

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
    public boolean syncStudentEnrollments(@NonNull String studentId, @NonNull java.util.Set<String> targetSubjectIds) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            // 1. Get current enrollments
            List<String> currentEnrollments = new ArrayList<>();
            try (Cursor cursor = db.query(StudentSubjectEntry.TABLE_NAME, new String[]{StudentSubjectEntry.COLUMN_SUBJECT_ID},
                    StudentSubjectEntry.COLUMN_STUDENT_ID + " = ?", new String[]{studentId}, null, null, null)) {
                while (cursor.moveToNext()) {
                    currentEnrollments.add(cursor.getString(0));
                }
            }

            // 2. Delete missing ones
            for (String currentId : currentEnrollments) {
                if (!targetSubjectIds.contains(currentId)) {
                    db.delete(StudentSubjectEntry.TABLE_NAME,
                            StudentSubjectEntry.COLUMN_STUDENT_ID + " = ? AND " + StudentSubjectEntry.COLUMN_SUBJECT_ID + " = ?",
                            new String[]{studentId, currentId});
                }
            }

            // 3. Add new ones
            for (String targetId : targetSubjectIds) {
                if (!currentEnrollments.contains(targetId)) {
                    ContentValues values = new ContentValues();
                    values.put(StudentSubjectEntry.COLUMN_ID, UUID.randomUUID().toString());
                    values.put(StudentSubjectEntry.COLUMN_STUDENT_ID, studentId);
                    values.put(StudentSubjectEntry.COLUMN_SUBJECT_ID, targetId);
                    db.insert(StudentSubjectEntry.TABLE_NAME, null, values);
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
}
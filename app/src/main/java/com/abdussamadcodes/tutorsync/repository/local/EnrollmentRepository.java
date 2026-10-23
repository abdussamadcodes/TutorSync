package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentSubjectEntry;
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
}
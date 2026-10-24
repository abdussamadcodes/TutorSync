package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.model.Student;
import java.util.ArrayList;
import java.util.List;

public class StudentRepository {

    public int insertStudent(@NonNull Student student) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(StudentEntry.COLUMN_ID, student.getId());
            values.put(StudentEntry.COLUMN_ACCOUNT_ID, student.getAccountId());
            values.put(StudentEntry.COLUMN_STUDENT_ID_DISPLAY, student.getStudentIdDisplay());
            values.put(StudentEntry.COLUMN_NAME, student.getName());
            values.put(StudentEntry.COLUMN_GRADE, student.getGrade());
            values.put(StudentEntry.COLUMN_SCHOOL, student.getSchool());
            values.put(StudentEntry.COLUMN_GUARDIAN_NAME, student.getGuardianName());
            values.put(StudentEntry.COLUMN_GUARDIAN_PHONE, student.getGuardianPhone());
            values.put(StudentEntry.COLUMN_ADDRESS, student.getAddress());
            values.put(StudentEntry.COLUMN_IS_ACTIVE, student.isActive() ? 1 : 0);
            values.put(StudentEntry.COLUMN_IS_DELETED, 0);
            values.put(StudentEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            long result = db.insertOrThrow(StudentEntry.TABLE_NAME, null, values);
            return result != -1 ? 1 : 0;
        } catch (SQLiteConstraintException e) {
            return -1;
        } catch (Exception e) {
            return 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteStudent(@NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            return db.delete(StudentEntry.TABLE_NAME, StudentEntry.COLUMN_ID + " = ?", new String[]{studentId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<Student> getActiveStudentsForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Student> students = new ArrayList<>();
        String selection = StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_IS_DELETED + " = 0 AND " + StudentEntry.COLUMN_IS_ACTIVE + " = 1";
        try (Cursor cursor = db.query(StudentEntry.TABLE_NAME, null, selection, new String[]{accountId}, null, null, StudentEntry.COLUMN_NAME + " ASC")) {
            while (cursor.moveToNext()) {
                students.add(new Student(
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_STUDENT_ID_DISPLAY)),
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_GRADE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_SCHOOL)),
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_GUARDIAN_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_GUARDIAN_PHONE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_ADDRESS)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_IS_ACTIVE)) == 1
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return students;
    }
}
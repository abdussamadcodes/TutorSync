package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AttendanceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.ScheduleEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.TransactionEntry;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.abdussamadcodes.tutorsync.model.Student;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StudentRepository {

    public void migrateLegacyStudentIds(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            String selection = StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                    StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " IS NULL OR " +
                    StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " = '')";

            List<String> needsMigration = new ArrayList<>();
            try (Cursor cursor = db.query(StudentEntry.TABLE_NAME, new String[]{StudentEntry.COLUMN_ID},
                    selection, new String[]{accountId}, null, null, StudentEntry.COLUMN_CREATED_AT + " ASC")) {
                while (cursor.moveToNext()) {
                    needsMigration.add(cursor.getString(0));
                }
            }

            long now = System.currentTimeMillis();
            for (String internalId : needsMigration) {
                String newDisplayId = generateNextStudentId(accountId, db);
                ContentValues values = new ContentValues();
                values.put(StudentEntry.COLUMN_STUDENT_ID_DISPLAY, newDisplayId);
                values.put(StudentEntry.COLUMN_UPDATED_AT, now);
                values.put(StudentEntry.COLUMN_LAST_MODIFIED, now);
                db.update(StudentEntry.TABLE_NAME, values,
                        StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, internalId});
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private String generateNextStudentId(String accountId, SQLiteDatabase db) {
        String prefix = "STU-";
        int nextNumber = 1;

        try (Cursor cursor = db.rawQuery(
                "SELECT " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY +
                        " FROM " + StudentEntry.TABLE_NAME +
                        " WHERE " + StudentEntry.COLUMN_ACCOUNT_ID + " = ? " +
                        " AND " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " LIKE 'STU-%'" +
                        " ORDER BY CAST(SUBSTR(" + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + ", 5) AS INTEGER) DESC LIMIT 1",
                new String[]{accountId})) {

            if (cursor.moveToFirst()) {
                String lastId = cursor.getString(0);
                try {
                    int lastNumber = Integer.parseInt(lastId.substring(4));
                    nextNumber = lastNumber + 1;
                } catch (NumberFormatException ignored) {}
            }
        }
        return prefix + String.format(Locale.US, "%04d", nextNumber);
    }

    public int insertStudent(@NonNull Student student) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            String displayId = student.getStudentIdDisplay();
            if (displayId == null || displayId.trim().isEmpty()) {
                displayId = generateNextStudentId(student.getAccountId(), db);
            }

            long now = System.currentTimeMillis();
            long modTime = student.getUpdatedAt() > 0 ? student.getUpdatedAt() : now;

            ContentValues values = new ContentValues();
            values.put(StudentEntry.COLUMN_ID, TutorSyncContract.ensureValidUuid(student.getId()));
            values.put(StudentEntry.COLUMN_ACCOUNT_ID, student.getAccountId());
            values.put(StudentEntry.COLUMN_STUDENT_ID_DISPLAY, displayId);
            values.put(StudentEntry.COLUMN_NAME, student.getName());
            values.put(StudentEntry.COLUMN_GRADE, student.getGrade());
            values.put(StudentEntry.COLUMN_SCHOOL, student.getSchool());
            values.put(StudentEntry.COLUMN_GUARDIAN_NAME, student.getGuardianName());
            values.put(StudentEntry.COLUMN_GUARDIAN_PHONE, student.getGuardianPhone());
            values.put(StudentEntry.COLUMN_ADDRESS, student.getAddress());
            values.put(StudentEntry.COLUMN_IS_ACTIVE, student.isActive() ? 1 : 0);
            values.put(StudentEntry.COLUMN_IS_DELETED, 0);
            values.put(StudentEntry.COLUMN_CREATED_AT, modTime);
            values.put(StudentEntry.COLUMN_UPDATED_AT, modTime);
            values.put(StudentEntry.COLUMN_LAST_MODIFIED, modTime);

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

    public boolean updateStudent(@NonNull Student student) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put(StudentEntry.COLUMN_NAME, student.getName());
            values.put(StudentEntry.COLUMN_GRADE, student.getGrade());
            values.put(StudentEntry.COLUMN_SCHOOL, student.getSchool());
            values.put(StudentEntry.COLUMN_GUARDIAN_NAME, student.getGuardianName());
            values.put(StudentEntry.COLUMN_GUARDIAN_PHONE, student.getGuardianPhone());
            values.put(StudentEntry.COLUMN_ADDRESS, student.getAddress());
            values.put(StudentEntry.COLUMN_IS_ACTIVE, student.isActive() ? 1 : 0);
            values.put(StudentEntry.COLUMN_UPDATED_AT, now);
            values.put(StudentEntry.COLUMN_LAST_MODIFIED, now);

            return db.update(StudentEntry.TABLE_NAME, values,
                    StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            StudentEntry.COLUMN_ID + " = ? AND " +
                            StudentEntry.COLUMN_IS_DELETED + " = 0",
                    new String[]{student.getAccountId(), student.getId()}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @Nullable
    public Student getStudentById(@NonNull String accountId, @NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor cursor = db.query(StudentEntry.TABLE_NAME, null,
                StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_ID + " = ?",
                new String[]{accountId, studentId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return mapCursorToStudent(cursor);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    public boolean hasFinancialRecords(@NonNull String accountId, @NonNull String studentId) {
        if (accountId.trim().isEmpty() || studentId.trim().isEmpty()) {
            return false;
        }
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM " + InvoiceEntry.TABLE_NAME +
                            " WHERE " + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            InvoiceEntry.COLUMN_STUDENT_ID + " = ? LIMIT 1",
                    new String[]{accountId, studentId})) {
                if (c.moveToFirst()) return true;
            }

            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM " + TransactionEntry.TABLE_NAME +
                            " WHERE " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            TransactionEntry.COLUMN_STUDENT_ID + " = ? LIMIT 1",
                    new String[]{accountId, studentId})) {
                if (c.moveToFirst()) return true;
            }

            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM " + PaymentEntry.TABLE_NAME + " p " +
                            "INNER JOIN " + InvoiceEntry.TABLE_NAME + " i " +
                            "ON p." + PaymentEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + " " +
                            "AND p." + PaymentEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " " +
                            "WHERE i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            "i." + InvoiceEntry.COLUMN_STUDENT_ID + " = ? LIMIT 1",
                    new String[]{accountId, studentId})) {
                if (c.moveToFirst()) return true;
            }

            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM " + AttendanceEntry.TABLE_NAME + " a " +
                            "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o " +
                            "ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                            "AND a." + AttendanceEntry.COLUMN_ACCOUNT_ID + " = o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " " +
                            "WHERE o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            "o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = ? AND " +
                            "a." + AttendanceEntry.COLUMN_CHARGE_DECISION + " = ? LIMIT 1",
                    new String[]{accountId, studentId, AttendanceRecord.DECISION_CHARGE})) {
                if (c.moveToFirst()) return true;
            }

            return false;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    /**
     * True when the student has chargeable classes that are not on any invoice yet.
     */
    public boolean hasOutstandingPerClassCharges(@NonNull String accountId, @NonNull String studentId) {
        if (accountId.trim().isEmpty() || studentId.trim().isEmpty()) {
            return false;
        }
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT 1 FROM " + AttendanceEntry.TABLE_NAME + " a " +
                        "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o " +
                        "ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                        "AND a." + AttendanceEntry.COLUMN_ACCOUNT_ID + " = o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " " +
                        "WHERE o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        "o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = ? AND " +
                        "a." + AttendanceEntry.COLUMN_CHARGE_DECISION + " = ? AND " +
                        "a." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " IS NULL LIMIT 1",
                new String[]{accountId, studentId, AttendanceRecord.DECISION_CHARGE})) {
            return c.moveToFirst();
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteStudent(@NonNull String accountId, @NonNull String studentId) {
        if (accountId.trim().isEmpty() || studentId.trim().isEmpty()) {
            return false;
        }

        // H4: move any unbilled per-class charges onto an invoice BEFORE the student disappears
        // from the active lists (billing only runs for active students). If that fails, the
        // billing is rolled back and the student is NOT deleted, so no charge can be lost.
        if (!new InvoiceRepository().billOutstandingPerClassChargesForStudent(accountId, studentId)) {
            return false;
        }

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();

            ContentValues studentValues = new ContentValues();
            studentValues.put(StudentEntry.COLUMN_IS_DELETED, 1);
            studentValues.put(StudentEntry.COLUMN_IS_ACTIVE, 0);
            studentValues.put(StudentEntry.COLUMN_UPDATED_AT, now);
            studentValues.put(StudentEntry.COLUMN_LAST_MODIFIED, now);

            int updated = db.update(StudentEntry.TABLE_NAME, studentValues,
                    StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            StudentEntry.COLUMN_ID + " = ? AND " +
                            StudentEntry.COLUMN_IS_DELETED + " = 0",
                    new String[]{accountId, studentId});

            if (updated > 0) {
                ContentValues scheduleValues = new ContentValues();
                scheduleValues.put(ScheduleEntry.COLUMN_IS_ACTIVE, 0);
                scheduleValues.put(ScheduleEntry.COLUMN_UPDATED_AT, now);
                scheduleValues.put(ScheduleEntry.COLUMN_LAST_MODIFIED, now);

                db.update(ScheduleEntry.TABLE_NAME, scheduleValues,
                        ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " +
                                ScheduleEntry.COLUMN_IS_ACTIVE + " = 1",
                        new String[]{accountId, studentId});
            }

            db.setTransactionSuccessful();
            return updated > 0;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<Student> getActiveStudentsForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Student> students = new ArrayList<>();
        String selection = StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                StudentEntry.COLUMN_IS_DELETED + " = 0 AND " +
                StudentEntry.COLUMN_IS_ACTIVE + " = 1";
        try (Cursor cursor = db.query(StudentEntry.TABLE_NAME, null, selection,
                new String[]{accountId}, null, null, StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " ASC")) {
            while (cursor.moveToNext()) {
                students.add(mapCursorToStudent(cursor));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return students;
    }

    @NonNull
    private Student mapCursorToStudent(@NonNull Cursor cursor) {
        long lastMod = 0L;
        int lastModIdx = cursor.getColumnIndex(StudentEntry.COLUMN_LAST_MODIFIED);
        if (lastModIdx != -1 && !cursor.isNull(lastModIdx)) {
            lastMod = cursor.getLong(lastModIdx);
        }
        if (lastMod <= 0L) {
            int upIdx = cursor.getColumnIndex(StudentEntry.COLUMN_UPDATED_AT);
            if (upIdx != -1 && !cursor.isNull(upIdx)) {
                lastMod = cursor.getLong(upIdx);
            }
        }
        return new Student(
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_ACCOUNT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_STUDENT_ID_DISPLAY)),
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_GRADE)),
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_SCHOOL)),
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_GUARDIAN_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_GUARDIAN_PHONE)),
                cursor.getString(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_ADDRESS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(StudentEntry.COLUMN_IS_ACTIVE)) == 1,
                lastMod
        );
    }
}
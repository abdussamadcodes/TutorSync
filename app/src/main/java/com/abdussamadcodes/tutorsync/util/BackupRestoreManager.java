package com.abdussamadcodes.tutorsync.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AttendanceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.FeeAgreementEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.LessonEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.ScheduleEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentSubjectEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.SubjectEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class BackupRestoreManager {

    private static final String BACKUP_MAGIC = "TUTORSYNC_LOCAL_BACKUP";
    private static final int BACKUP_SCHEMA_VERSION = 1;

    private BackupRestoreManager() {}

    public static boolean exportAccountData(@NonNull Context context,
                                            @NonNull String accountId,
                                            @NonNull Uri destinationUri) {
        if (accountId.isEmpty()) return false;

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            JSONObject root = new JSONObject();
            root.put("backup_type", BACKUP_MAGIC);
            root.put("backup_version", BACKUP_SCHEMA_VERSION);
            root.put("exported_at", System.currentTimeMillis());
            root.put("source_account_id", accountId);

            // 1. Account & Tutor Profile
            JSONObject profileObj = exportAccountProfile(db, accountId);
            root.put("account_profile", profileObj);

            // 2. Account-scoped tables
            root.put(StudentEntry.TABLE_NAME, exportTableByAccount(db, StudentEntry.TABLE_NAME, accountId));
            root.put(SubjectEntry.TABLE_NAME, exportTableByAccount(db, SubjectEntry.TABLE_NAME, accountId));
            root.put(StudentSubjectEntry.TABLE_NAME, exportStudentSubjects(db, accountId));
            root.put(ScheduleEntry.TABLE_NAME, exportTableByAccount(db, ScheduleEntry.TABLE_NAME, accountId));
            root.put(OccurrenceEntry.TABLE_NAME, exportTableByAccount(db, OccurrenceEntry.TABLE_NAME, accountId));
            root.put(AttendanceEntry.TABLE_NAME, exportTableByAccount(db, AttendanceEntry.TABLE_NAME, accountId));
            root.put(LessonEntry.TABLE_NAME, exportTableByAccount(db, LessonEntry.TABLE_NAME, accountId));
            root.put(FeeAgreementEntry.TABLE_NAME, exportTableByAccount(db, FeeAgreementEntry.TABLE_NAME, accountId));
            root.put(InvoiceEntry.TABLE_NAME, exportTableByAccount(db, InvoiceEntry.TABLE_NAME, accountId));
            root.put(PaymentEntry.TABLE_NAME, exportTableByAccount(db, PaymentEntry.TABLE_NAME, accountId));

            byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = context.getContentResolver().openOutputStream(destinationUri)) {
                if (os == null) return false;
                os.write(bytes);
                os.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public static boolean importAccountData(@NonNull Context context,
                                            @NonNull String activeAccountId,
                                            @NonNull Uri sourceUri) {
        if (activeAccountId.isEmpty()) return false;

        String jsonContent;
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri)) {
            if (is == null) return false;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            jsonContent = sb.toString();
        } catch (Exception e) {
            return false;
        }

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            JSONObject root = new JSONObject(jsonContent);
            if (!BACKUP_MAGIC.equals(root.optString("backup_type"))) {
                return false;
            }

            // 1. Restore Tutor Profile
            JSONObject profileObj = root.optJSONObject("account_profile");
            if (profileObj != null) {
                restoreAccountProfile(context, db, activeAccountId, profileObj);
            }

            // Maps to remap foreign keys when a logical duplicate already exists in the active account
            Map<String, String> studentIdMap = new HashMap<>();
            Map<String, String> subjectIdMap = new HashMap<>();
            Map<String, String> scheduleIdMap = new HashMap<>();
            Map<String, String> occurrenceIdMap = new HashMap<>();
            Map<String, String> invoiceIdMap = new HashMap<>();

            // 2. Restore Students (prevent duplicate STU-xxxx or ID collisions)
            restoreStudents(db, activeAccountId, root.optJSONArray(StudentEntry.TABLE_NAME), studentIdMap);

            // 3. Restore Subjects (prevent duplicate subject title+level)
            restoreSubjects(db, activeAccountId, root.optJSONArray(SubjectEntry.TABLE_NAME), subjectIdMap);

            // 4. Restore Student-Subject Enrollments
            restoreStudentSubjects(db, root.optJSONArray(StudentSubjectEntry.TABLE_NAME), studentIdMap, subjectIdMap);

            // 5. Restore Recurring Schedules
            restoreSchedules(db, activeAccountId, root.optJSONArray(ScheduleEntry.TABLE_NAME),
                    studentIdMap, subjectIdMap, scheduleIdMap);

            // 6. Restore Class Occurrences
            restoreOccurrences(db, activeAccountId, root.optJSONArray(OccurrenceEntry.TABLE_NAME),
                    studentIdMap, subjectIdMap, scheduleIdMap, occurrenceIdMap);

            // 7. Restore Attendance Records
            restoreAttendance(db, activeAccountId, root.optJSONArray(AttendanceEntry.TABLE_NAME), occurrenceIdMap);

            // 8. Restore Lessons
            restoreLessons(db, activeAccountId, root.optJSONArray(LessonEntry.TABLE_NAME),
                    studentIdMap, occurrenceIdMap);

            // 9. Restore Fee Agreements
            restoreFeeAgreements(db, activeAccountId, root.optJSONArray(FeeAgreementEntry.TABLE_NAME), studentIdMap);

            // 10. Restore Invoices
            restoreInvoices(db, activeAccountId, root.optJSONArray(InvoiceEntry.TABLE_NAME),
                    studentIdMap, invoiceIdMap);

            // 11. Restore Payments
            restorePayments(db, activeAccountId, root.optJSONArray(PaymentEntry.TABLE_NAME), invoiceIdMap);

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private static JSONObject exportAccountProfile(SQLiteDatabase db, String accountId) throws Exception {
        JSONObject obj = new JSONObject();
        try (Cursor cursor = db.query(AccountEntry.TABLE_NAME, null,
                AccountEntry.COLUMN_ID + " = ?", new String[]{accountId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                obj.put(AccountEntry.COLUMN_FULL_NAME, cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_FULL_NAME)));
                putOptionalColumn(cursor, obj, AccountEntry.COLUMN_PHONE);
                putOptionalColumn(cursor, obj, AccountEntry.COLUMN_EMAIL);
                putOptionalColumn(cursor, obj, AccountEntry.COLUMN_ADDRESS);
                putOptionalColumn(cursor, obj, AccountEntry.COLUMN_DOB);

                int picIdx = cursor.getColumnIndex(AccountEntry.COLUMN_PROFILE_PIC_PATH);
                if (picIdx >= 0) {
                    String path = cursor.getString(picIdx);
                    if (path != null && !path.isEmpty()) {
                        File file = new File(path);
                        if (file.exists() && file.isFile()) {
                            obj.put("profile_pic_base64", encodeFileToBase64(file));
                        }
                    }
                }
            }
        }
        return obj;
    }

    private static void putOptionalColumn(Cursor cursor, JSONObject obj, String colName) throws Exception {
        int idx = cursor.getColumnIndex(colName);
        if (idx >= 0 && !cursor.isNull(idx)) {
            obj.put(colName, cursor.getString(idx));
        }
    }

    private static JSONArray exportTableByAccount(SQLiteDatabase db, String tableName, String accountId) throws Exception {
        JSONArray array = new JSONArray();
        try (Cursor cursor = db.query(tableName, null, "account_id = ?", new String[]{accountId}, null, null, null)) {
            while (cursor.moveToNext()) {
                array.put(cursorRowToJson(cursor));
            }
        }
        return array;
    }

    private static JSONArray exportStudentSubjects(SQLiteDatabase db, String accountId) throws Exception {
        JSONArray array = new JSONArray();
        String sql = "SELECT ss.* FROM " + StudentSubjectEntry.TABLE_NAME + " ss " +
                "INNER JOIN " + StudentEntry.TABLE_NAME + " s ON ss." + StudentSubjectEntry.COLUMN_STUDENT_ID + " = s." + StudentEntry.COLUMN_ID +
                " WHERE s." + StudentEntry.COLUMN_ACCOUNT_ID + " = ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{accountId})) {
            while (cursor.moveToNext()) {
                array.put(cursorRowToJson(cursor));
            }
        }
        return array;
    }

    private static JSONObject cursorRowToJson(Cursor cursor) throws Exception {
        JSONObject row = new JSONObject();
        String[] cols = cursor.getColumnNames();
        for (int i = 0; i < cols.length; i++) {
            int type = cursor.getType(i);
            if (type == Cursor.FIELD_TYPE_NULL) {
                row.put(cols[i], JSONObject.NULL);
            } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                row.put(cols[i], cursor.getLong(i));
            } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                row.put(cols[i], cursor.getDouble(i));
            } else {
                row.put(cols[i], cursor.getString(i));
            }
        }
        return row;
    }

    private static void restoreAccountProfile(Context context, SQLiteDatabase db,
                                              String activeAccountId, JSONObject profileObj) {
        try (Cursor cursor = db.query(AccountEntry.TABLE_NAME, null,
                AccountEntry.COLUMN_ID + " = ?", new String[]{activeAccountId}, null, null, null)) {
            if (!cursor.moveToFirst()) return;

            ContentValues values = new ContentValues();
            String backupName = profileObj.optString(AccountEntry.COLUMN_FULL_NAME, "").trim();
            if (!backupName.isEmpty()) {
                values.put(AccountEntry.COLUMN_FULL_NAME, backupName);
            }

            mergeProfileField(cursor, profileObj, values, AccountEntry.COLUMN_PHONE);
            mergeProfileField(cursor, profileObj, values, AccountEntry.COLUMN_EMAIL);
            mergeProfileField(cursor, profileObj, values, AccountEntry.COLUMN_ADDRESS);
            mergeProfileField(cursor, profileObj, values, AccountEntry.COLUMN_DOB);

            String base64Pic = profileObj.optString("profile_pic_base64", "");
            int picIdx = cursor.getColumnIndex(AccountEntry.COLUMN_PROFILE_PIC_PATH);
            String existingPic = picIdx >= 0 ? cursor.getString(picIdx) : null;
            boolean hasExistingPic = existingPic != null && new File(existingPic).exists();

            if (!hasExistingPic && !base64Pic.isEmpty()) {
                File outPic = new File(context.getFilesDir(), "profile_" + activeAccountId + ".jpg");
                if (decodeBase64ToFile(base64Pic, outPic)) {
                    values.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, outPic.getAbsolutePath());
                }
            }

            values.put(AccountEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());
            db.update(AccountEntry.TABLE_NAME, values, AccountEntry.COLUMN_ID + " = ?", new String[]{activeAccountId});
        }
    }

    private static void mergeProfileField(Cursor cursor, JSONObject profileObj, ContentValues values, String col) {
        int idx = cursor.getColumnIndex(col);
        String currentVal = idx >= 0 ? cursor.getString(idx) : null;
        String backupVal = profileObj.optString(col, "");
        if ((currentVal == null || currentVal.trim().isEmpty()) && !backupVal.isEmpty()) {
            values.put(col, backupVal);
        }
    }

    private static void restoreStudents(SQLiteDatabase db, String activeAccountId,
                                        JSONArray array, Map<String, String> studentIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, StudentEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(StudentEntry.COLUMN_ID);
            String displayId = obj.optString(StudentEntry.COLUMN_STUDENT_ID_DISPLAY, "");

            // Check if a student with the same display ID already exists in this account
            String existingId = findExistingId(db, StudentEntry.TABLE_NAME,
                    StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " = ?",
                    new String[]{activeAccountId, displayId});

            if (existingId != null) {
                studentIdMap.put(origId, existingId);
                continue;
            }

            // Check if primary key exists under another account
            String targetId = origId;
            String ownerAccount = findOwnerAccount(db, StudentEntry.TABLE_NAME, origId);
            if (ownerAccount != null) {
                if (activeAccountId.equals(ownerAccount)) {
                    studentIdMap.put(origId, origId);
                    continue;
                } else {
                    targetId = java.util.UUID.randomUUID().toString();
                }
            }

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(StudentEntry.COLUMN_ID, targetId);
            values.put(StudentEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            db.insertWithOnConflict(StudentEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            studentIdMap.put(origId, targetId);
        }
    }

    private static void restoreSubjects(SQLiteDatabase db, String activeAccountId,
                                        JSONArray array, Map<String, String> subjectIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, SubjectEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(SubjectEntry.COLUMN_ID);
            String title = obj.optString(SubjectEntry.COLUMN_TITLE, "");
            String level = obj.isNull(SubjectEntry.COLUMN_LEVEL) ? "" : obj.optString(SubjectEntry.COLUMN_LEVEL, "");

            String existingId = findExistingId(db, SubjectEntry.TABLE_NAME,
                    SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND LOWER(" + SubjectEntry.COLUMN_TITLE + ") = LOWER(?) AND COALESCE(" + SubjectEntry.COLUMN_LEVEL + ", '') = ?",
                    new String[]{activeAccountId, title, level});

            if (existingId != null) {
                subjectIdMap.put(origId, existingId);
                continue;
            }

            String targetId = resolveTargetPrimaryKey(db, SubjectEntry.TABLE_NAME, origId, activeAccountId);
            if (targetId == null) {
                subjectIdMap.put(origId, origId);
                continue;
            }

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(SubjectEntry.COLUMN_ID, targetId);
            values.put(SubjectEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            db.insertWithOnConflict(SubjectEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            subjectIdMap.put(origId, targetId);
        }
    }

    private static void restoreStudentSubjects(SQLiteDatabase db, JSONArray array,
                                               Map<String, String> studentIdMap,
                                               Map<String, String> subjectIdMap) throws Exception {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String studentId = mapId(studentIdMap, obj.optString(StudentSubjectEntry.COLUMN_STUDENT_ID));
            String subjectId = mapId(subjectIdMap, obj.optString(StudentSubjectEntry.COLUMN_SUBJECT_ID));

            String existing = findExistingId(db, StudentSubjectEntry.TABLE_NAME,
                    StudentSubjectEntry.COLUMN_STUDENT_ID + " = ? AND " + StudentSubjectEntry.COLUMN_SUBJECT_ID + " = ?",
                    new String[]{studentId, subjectId});
            if (existing != null) continue;

            ContentValues values = new ContentValues();
            values.put(StudentSubjectEntry.COLUMN_ID, java.util.UUID.randomUUID().toString());
            values.put(StudentSubjectEntry.COLUMN_STUDENT_ID, studentId);
            values.put(StudentSubjectEntry.COLUMN_SUBJECT_ID, subjectId);
            db.insertWithOnConflict(StudentSubjectEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static void restoreSchedules(SQLiteDatabase db, String activeAccountId, JSONArray array,
                                         Map<String, String> studentIdMap, Map<String, String> subjectIdMap,
                                         Map<String, String> scheduleIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, ScheduleEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(ScheduleEntry.COLUMN_ID);
            String studentId = mapId(studentIdMap, obj.optString(ScheduleEntry.COLUMN_STUDENT_ID));
            String subjectId = mapId(subjectIdMap, obj.optString(ScheduleEntry.COLUMN_SUBJECT_ID));
            int dayOfWeek = obj.optInt(ScheduleEntry.COLUMN_DAY_OF_WEEK);
            int startMinutes = obj.optInt(ScheduleEntry.COLUMN_START_TIME_MINUTES);

            String existingId = findExistingId(db, ScheduleEntry.TABLE_NAME,
                    ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " +
                            ScheduleEntry.COLUMN_SUBJECT_ID + " = ? AND " +
                            ScheduleEntry.COLUMN_DAY_OF_WEEK + " = ? AND " +
                            ScheduleEntry.COLUMN_START_TIME_MINUTES + " = ?",
                    new String[]{activeAccountId, studentId, subjectId, String.valueOf(dayOfWeek), String.valueOf(startMinutes)});

            if (existingId != null) {
                scheduleIdMap.put(origId, existingId);
                continue;
            }

            String targetId = resolveTargetPrimaryKey(db, ScheduleEntry.TABLE_NAME, origId, activeAccountId);
            if (targetId == null) {
                scheduleIdMap.put(origId, origId);
                continue;
            }

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(ScheduleEntry.COLUMN_ID, targetId);
            values.put(ScheduleEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            values.put(ScheduleEntry.COLUMN_STUDENT_ID, studentId);
            values.put(ScheduleEntry.COLUMN_SUBJECT_ID, subjectId);
            db.insertWithOnConflict(ScheduleEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            scheduleIdMap.put(origId, targetId);
        }
    }

    private static void restoreOccurrences(SQLiteDatabase db, String activeAccountId, JSONArray array,
                                           Map<String, String> studentIdMap, Map<String, String> subjectIdMap,
                                           Map<String, String> scheduleIdMap, Map<String, String> occurrenceIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, OccurrenceEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(OccurrenceEntry.COLUMN_ID);
            String studentId = mapId(studentIdMap, obj.optString(OccurrenceEntry.COLUMN_STUDENT_ID));
            String subjectId = obj.isNull(OccurrenceEntry.COLUMN_SUBJECT_ID) ? null : mapId(subjectIdMap, obj.optString(OccurrenceEntry.COLUMN_SUBJECT_ID));
            String scheduleId = obj.isNull(OccurrenceEntry.COLUMN_SCHEDULE_ID) ? null : mapId(scheduleIdMap, obj.optString(OccurrenceEntry.COLUMN_SCHEDULE_ID));
            long date = obj.optLong(OccurrenceEntry.COLUMN_DATE);
            int startMinutes = obj.optInt(OccurrenceEntry.COLUMN_START_TIME_MINUTES);

            String existingId = findExistingId(db, OccurrenceEntry.TABLE_NAME,
                    OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            OccurrenceEntry.COLUMN_STUDENT_ID + " = ? AND " +
                            OccurrenceEntry.COLUMN_DATE + " = ? AND " +
                            OccurrenceEntry.COLUMN_START_TIME_MINUTES + " = ?",
                    new String[]{activeAccountId, studentId, String.valueOf(date), String.valueOf(startMinutes)});

            if (existingId != null) {
                occurrenceIdMap.put(origId, existingId);
                continue;
            }

            String targetId = resolveTargetPrimaryKey(db, OccurrenceEntry.TABLE_NAME, origId, activeAccountId);
            if (targetId == null) {
                occurrenceIdMap.put(origId, origId);
                continue;
            }

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(OccurrenceEntry.COLUMN_ID, targetId);
            values.put(OccurrenceEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            values.put(OccurrenceEntry.COLUMN_STUDENT_ID, studentId);
            if (subjectId != null) values.put(OccurrenceEntry.COLUMN_SUBJECT_ID, subjectId);
            if (scheduleId != null) values.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, scheduleId);
            db.insertWithOnConflict(OccurrenceEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            occurrenceIdMap.put(origId, targetId);
        }
    }

    private static void restoreAttendance(SQLiteDatabase db, String activeAccountId, JSONArray array,
                                          Map<String, String> occurrenceIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, AttendanceEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(AttendanceEntry.COLUMN_ID);
            String occurrenceId = mapId(occurrenceIdMap, obj.optString(AttendanceEntry.COLUMN_OCCURRENCE_ID));

            String existingId = findExistingId(db, AttendanceEntry.TABLE_NAME,
                    AttendanceEntry.COLUMN_OCCURRENCE_ID + " = ?", new String[]{occurrenceId});
            if (existingId != null) continue;

            String targetId = resolveTargetPrimaryKey(db, AttendanceEntry.TABLE_NAME, origId, activeAccountId);
            if (targetId == null) continue;

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(AttendanceEntry.COLUMN_ID, targetId);
            values.put(AttendanceEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            values.put(AttendanceEntry.COLUMN_OCCURRENCE_ID, occurrenceId);
            db.insertWithOnConflict(AttendanceEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static void restoreLessons(SQLiteDatabase db, String activeAccountId, JSONArray array,
                                       Map<String, String> studentIdMap, Map<String, String> occurrenceIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, LessonEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(LessonEntry.COLUMN_ID);
            String studentId = mapId(studentIdMap, obj.optString(LessonEntry.COLUMN_STUDENT_ID));
            String occurrenceId = obj.isNull(LessonEntry.COLUMN_OCCURRENCE_ID)
                    ? null : mapId(occurrenceIdMap, obj.optString(LessonEntry.COLUMN_OCCURRENCE_ID));

            if (occurrenceId != null) {
                String existing = findExistingId(db, LessonEntry.TABLE_NAME,
                        LessonEntry.COLUMN_ACCOUNT_ID + " = ? AND " + LessonEntry.COLUMN_OCCURRENCE_ID + " = ?",
                        new String[]{activeAccountId, occurrenceId});
                if (existing != null) continue;
            }

            String targetId = resolveTargetPrimaryKey(db, LessonEntry.TABLE_NAME, origId, activeAccountId);
            if (targetId == null) continue;

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(LessonEntry.COLUMN_ID, targetId);
            values.put(LessonEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            values.put(LessonEntry.COLUMN_STUDENT_ID, studentId);
            if (occurrenceId != null) values.put(LessonEntry.COLUMN_OCCURRENCE_ID, occurrenceId);
            db.insertWithOnConflict(LessonEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static void restoreFeeAgreements(SQLiteDatabase db, String activeAccountId, JSONArray array,
                                             Map<String, String> studentIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, FeeAgreementEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(FeeAgreementEntry.COLUMN_ID);
            String studentId = mapId(studentIdMap, obj.optString(FeeAgreementEntry.COLUMN_STUDENT_ID));

            // Protect existing fee agreement for this student
            String existingId = findExistingId(db, FeeAgreementEntry.TABLE_NAME,
                    FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?", new String[]{studentId});
            if (existingId != null) continue;

            String targetId = resolveTargetPrimaryKey(db, FeeAgreementEntry.TABLE_NAME, origId, activeAccountId);
            if (targetId == null) continue;

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(FeeAgreementEntry.COLUMN_ID, targetId);
            values.put(FeeAgreementEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            values.put(FeeAgreementEntry.COLUMN_STUDENT_ID, studentId);
            db.insertWithOnConflict(FeeAgreementEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static void restoreInvoices(SQLiteDatabase db, String activeAccountId, JSONArray array,
                                        Map<String, String> studentIdMap, Map<String, String> invoiceIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, InvoiceEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(InvoiceEntry.COLUMN_ID);
            String studentId = mapId(studentIdMap, obj.optString(InvoiceEntry.COLUMN_STUDENT_ID));
            String billingPeriod = obj.optString(InvoiceEntry.COLUMN_BILLING_PERIOD, "");

            // Prevent duplicate invoices for the same student and billing period
            String existingId = findExistingId(db, InvoiceEntry.TABLE_NAME,
                    InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            InvoiceEntry.COLUMN_STUDENT_ID + " = ? AND " +
                            InvoiceEntry.COLUMN_BILLING_PERIOD + " = ?",
                    new String[]{activeAccountId, studentId, billingPeriod});

            if (existingId != null) {
                invoiceIdMap.put(origId, existingId);
                continue;
            }

            String targetId = resolveTargetPrimaryKey(db, InvoiceEntry.TABLE_NAME, origId, activeAccountId);
            if (targetId == null) {
                invoiceIdMap.put(origId, origId);
                continue;
            }

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(InvoiceEntry.COLUMN_ID, targetId);
            values.put(InvoiceEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            values.put(InvoiceEntry.COLUMN_STUDENT_ID, studentId);
            db.insertWithOnConflict(InvoiceEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            invoiceIdMap.put(origId, targetId);
        }
    }

    private static void restorePayments(SQLiteDatabase db, String activeAccountId, JSONArray array,
                                        Map<String, String> invoiceIdMap) throws Exception {
        if (array == null) return;
        Set<String> validCols = getTableColumns(db, PaymentEntry.TABLE_NAME);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String origId = obj.optString(PaymentEntry.COLUMN_ID);
            String invoiceId = mapId(invoiceIdMap, obj.optString(PaymentEntry.COLUMN_INVOICE_ID));
            long paymentDate = obj.optLong(PaymentEntry.COLUMN_PAYMENT_DATE);
            int amount = obj.optInt(PaymentEntry.COLUMN_AMOUNT);

            // Prevent duplicate payment transaction records
            String existingId = findExistingId(db, PaymentEntry.TABLE_NAME,
                    PaymentEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            PaymentEntry.COLUMN_INVOICE_ID + " = ? AND " +
                            PaymentEntry.COLUMN_PAYMENT_DATE + " = ? AND " +
                            PaymentEntry.COLUMN_AMOUNT + " = ?",
                    new String[]{activeAccountId, invoiceId, String.valueOf(paymentDate), String.valueOf(amount)});
            if (existingId != null) continue;

            String targetId = resolveTargetPrimaryKey(db, PaymentEntry.TABLE_NAME, origId, activeAccountId);
            if (targetId == null) continue;

            ContentValues values = jsonToContentValues(obj, validCols);
            values.put(PaymentEntry.COLUMN_ID, targetId);
            values.put(PaymentEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            values.put(PaymentEntry.COLUMN_INVOICE_ID, invoiceId);
            db.insertWithOnConflict(PaymentEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static String mapId(Map<String, String> map, String origId) {
        if (origId == null) return "";
        String mapped = map.get(origId);
        return mapped != null ? mapped : origId;
    }

    private static String findExistingId(SQLiteDatabase db, String table, String selection, String[] args) {
        try (Cursor cursor = db.query(table, new String[]{"id"}, selection, args, null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    private static String findOwnerAccount(SQLiteDatabase db, String table, String id) {
        try (Cursor cursor = db.query(table, new String[]{"account_id"}, "id = ?", new String[]{id}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    /**
     * Returns:
     * - null if the record already exists in the active account (skip insert)
     * - origId if the ID does not exist in the DB at all
     * - a new UUID if the ID exists in another account (maintaining strict account isolation)
     */
    private static String resolveTargetPrimaryKey(SQLiteDatabase db, String table, String origId, String activeAccountId) {
        String owner = findOwnerAccount(db, table, origId);
        if (owner == null) return origId;
        if (activeAccountId.equals(owner)) return null;
        return java.util.UUID.randomUUID().toString();
    }

    private static Set<String> getTableColumns(SQLiteDatabase db, String tableName) {
        Set<String> cols = new HashSet<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            int nameIdx = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIdx >= 0) cols.add(cursor.getString(nameIdx));
            }
        }
        return cols;
    }

    private static ContentValues jsonToContentValues(JSONObject obj, Set<String> validCols) throws Exception {
        ContentValues values = new ContentValues();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!validCols.contains(key)) continue;
            if (obj.isNull(key)) {
                values.putNull(key);
            } else {
                Object val = obj.get(key);
                if (val instanceof Integer) {
                    values.put(key, (Integer) val);
                } else if (val instanceof Long) {
                    values.put(key, (Long) val);
                } else if (val instanceof Double) {
                    values.put(key, (Double) val);
                } else {
                    values.put(key, String.valueOf(val));
                }
            }
        }
        return values;
    }

    private static String encodeFileToBase64(File file) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
        }
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private static boolean decodeBase64ToFile(String base64, File outFile) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes);
                fos.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
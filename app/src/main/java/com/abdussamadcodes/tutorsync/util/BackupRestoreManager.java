package com.abdussamadcodes.tutorsync.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AttendanceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AuditLogEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.FeeAgreementEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceItemEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.LessonEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentAllocationEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.ScheduleEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.ScheduleFeeHistoryEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentSubjectEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.SubjectEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.TransactionEntry;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;

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
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BackupRestoreManager {

    private static final String BACKUP_MAGIC = "TUTORSYNC_LOCAL_BACKUP";
    private static final int BACKUP_SCHEMA_VERSION = 2;

    private BackupRestoreManager() {}

    public static boolean exportAccountData(@NonNull Context context,
                                            @NonNull String accountId,
                                            @NonNull Uri destinationUri) {
        if (accountId.isEmpty()) return false;

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            // Ensure mirror columns and allocations are in sync before exporting
            reconcileAccountFinancialData(db, accountId);

            JSONObject root = new JSONObject();
            root.put("backup_type", BACKUP_MAGIC);
            root.put("backup_version", BACKUP_SCHEMA_VERSION);
            root.put("exported_at", System.currentTimeMillis());
            root.put("source_account_id", accountId);

            // 1. Account & Tutor Profile
            JSONObject profileObj = exportAccountProfile(db, accountId);
            root.put("account_profile", profileObj);

            // 2. Account-scoped tables (full relational + financial ledger export)
            root.put(StudentEntry.TABLE_NAME, exportTableByAccount(db, StudentEntry.TABLE_NAME, accountId));
            root.put(SubjectEntry.TABLE_NAME, exportTableByAccount(db, SubjectEntry.TABLE_NAME, accountId));
            root.put(StudentSubjectEntry.TABLE_NAME, exportStudentSubjects(db, accountId));
            root.put(ScheduleFeeHistoryEntry.TABLE_NAME, exportTableByAccount(db, ScheduleFeeHistoryEntry.TABLE_NAME, accountId));
            root.put(ScheduleEntry.TABLE_NAME, exportTableByAccount(db, ScheduleEntry.TABLE_NAME, accountId));
            root.put(OccurrenceEntry.TABLE_NAME, exportTableByAccount(db, OccurrenceEntry.TABLE_NAME, accountId));
            root.put(AttendanceEntry.TABLE_NAME, exportTableByAccount(db, AttendanceEntry.TABLE_NAME, accountId));
            root.put(LessonEntry.TABLE_NAME, exportTableByAccount(db, LessonEntry.TABLE_NAME, accountId));
            root.put(FeeAgreementEntry.TABLE_NAME, exportTableByAccount(db, FeeAgreementEntry.TABLE_NAME, accountId));
            root.put(InvoiceEntry.TABLE_NAME, exportTableByAccount(db, InvoiceEntry.TABLE_NAME, accountId));
            root.put(InvoiceItemEntry.TABLE_NAME, exportTableByAccount(db, InvoiceItemEntry.TABLE_NAME, accountId));
            root.put(PaymentEntry.TABLE_NAME, exportTableByAccount(db, PaymentEntry.TABLE_NAME, accountId));
            root.put(TransactionEntry.TABLE_NAME, exportTableByAccount(db, TransactionEntry.TABLE_NAME, accountId));
            root.put(PaymentAllocationEntry.TABLE_NAME, exportTableByAccount(db, PaymentAllocationEntry.TABLE_NAME, accountId));
            root.put(AuditLogEntry.TABLE_NAME, exportTableByAccount(db, AuditLogEntry.TABLE_NAME, accountId));

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

            // Maps to remap foreign keys onto the active account
            Map<String, String> studentIdMap = new HashMap<>();
            Map<String, String> subjectIdMap = new HashMap<>();
            Map<String, String> scheduleGroupIdMap = new HashMap<>();
            Map<String, String> scheduleIdMap = new HashMap<>();
            Map<String, String> occurrenceIdMap = new HashMap<>();
            Map<String, String> invoiceIdMap = new HashMap<>();
            Map<String, String> invoiceItemIdMap = new HashMap<>();
            Map<String, String> transactionIdMap = new HashMap<>();

            // 2. Restore Students
            restoreStudents(db, activeAccountId, root.optJSONArray(StudentEntry.TABLE_NAME), studentIdMap);

            // 3. Restore Subjects
            restoreSubjects(db, activeAccountId, root.optJSONArray(SubjectEntry.TABLE_NAME), subjectIdMap);

            // 4. Restore Student-Subject Enrollments
            restoreStudentSubjects(db, root.optJSONArray(StudentSubjectEntry.TABLE_NAME), studentIdMap, subjectIdMap);

            // 5. Restore Schedule Fee History
            restoreScheduleFeeHistory(db, activeAccountId, root.optJSONArray(ScheduleFeeHistoryEntry.TABLE_NAME),
                    studentIdMap, scheduleGroupIdMap);

            // 6. Restore Recurring Schedules
            restoreSchedules(db, activeAccountId, root.optJSONArray(ScheduleEntry.TABLE_NAME),
                    studentIdMap, subjectIdMap, scheduleGroupIdMap, scheduleIdMap);

            // 7. Restore Class Occurrences
            restoreOccurrences(db, activeAccountId, root.optJSONArray(OccurrenceEntry.TABLE_NAME),
                    studentIdMap, subjectIdMap, scheduleIdMap, scheduleGroupIdMap, occurrenceIdMap);

            // 8. Restore Invoices (must precede InvoiceItems and PaymentAllocations)
            restoreInvoices(db, activeAccountId, root.optJSONArray(InvoiceEntry.TABLE_NAME),
                    studentIdMap, invoiceIdMap);

            // 9. Restore Invoice Items (must precede Attendance billed_invoice_item_id mapping)
            restoreInvoiceItems(db, activeAccountId, root.optJSONArray(InvoiceItemEntry.TABLE_NAME),
                    invoiceIdMap, scheduleGroupIdMap, occurrenceIdMap, invoiceItemIdMap);

            // 10. Restore Attendance Records
            restoreAttendance(db, activeAccountId, root.optJSONArray(AttendanceEntry.TABLE_NAME),
                    occurrenceIdMap, invoiceItemIdMap);

            // 11. Restore Lessons
            restoreLessons(db, activeAccountId, root.optJSONArray(LessonEntry.TABLE_NAME),
                    studentIdMap, occurrenceIdMap);

            // 12. Restore Fee Agreements
            restoreFeeAgreements(db, activeAccountId, root.optJSONArray(FeeAgreementEntry.TABLE_NAME), studentIdMap);

            // 13. Restore Financial Transactions
            restoreTransactions(db, activeAccountId, root.optJSONArray(TransactionEntry.TABLE_NAME),
                    studentIdMap, transactionIdMap);

            // 14. Restore Payment Allocations
            restorePaymentAllocations(db, activeAccountId, root.optJSONArray(PaymentAllocationEntry.TABLE_NAME),
                    transactionIdMap, invoiceIdMap);

            // 15. Restore Legacy Payments
            restorePayments(db, activeAccountId, root.optJSONArray(PaymentEntry.TABLE_NAME), invoiceIdMap);

            // 16. Restore Audit Logs
            restoreAuditLogs(db, activeAccountId, root.optJSONArray(AuditLogEntry.TABLE_NAME), studentIdMap);

            // 17. Reconcile paid/partially-paid invoices (handles backups created before financial_transactions were exported)
            reconcileAccountFinancialData(db, activeAccountId);

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    /**
     * Ensures every invoice's paid_amount, status, invoice_items, financial_transactions,
     * and payment_allocations are completely synchronized.
     * If a backup was created before financial_transactions/payment_allocations were exported,
     * this reconstructs the missing transaction and allocation rows from invoices.paid_amount / status.
     */
    public static void reconcileAccountFinancialData(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        if (accountId.isEmpty()) return;

        try (Cursor invCursor = db.query(InvoiceEntry.TABLE_NAME, null,
                InvoiceEntry.COLUMN_ACCOUNT_ID + " = ?", new String[]{accountId},
                null, null, InvoiceEntry.COLUMN_CREATED_AT + " ASC")) {

            while (invCursor.moveToNext()) {
                String invoiceId = invCursor.getString(invCursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ID));
                String studentId = invCursor.getString(invCursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_STUDENT_ID));
                String invoiceDisplayId = invCursor.getString(invCursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY));
                String billingPeriod = invCursor.getString(invCursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_BILLING_PERIOD));
                int totalAmount = invCursor.getInt(invCursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_TOTAL_AMOUNT));
                int savedPaidAmount = invCursor.getInt(invCursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_PAID_AMOUNT));
                String savedStatus = invCursor.getString(invCursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_STATUS));
                long createdAt = invCursor.getLong(invCursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_CREATED_AT));
                int updatedIdx = invCursor.getColumnIndex(InvoiceEntry.COLUMN_UPDATED_AT);
                long updatedAt = (updatedIdx >= 0 && !invCursor.isNull(updatedIdx)) ? invCursor.getLong(updatedIdx) : createdAt;
                if (updatedAt <= 0) updatedAt = createdAt > 0 ? createdAt : System.currentTimeMillis();

                // 1. Ensure at least one InvoiceItem exists so InvoiceDetailActivity & PDF export show line items
                if (totalAmount > 0 && countRows(db, InvoiceItemEntry.TABLE_NAME,
                        InvoiceItemEntry.COLUMN_INVOICE_ID + " = ?", new String[]{invoiceId}) == 0) {
                    ContentValues iiv = new ContentValues();
                    iiv.put(InvoiceItemEntry.COLUMN_ID, UUID.randomUUID().toString());
                    iiv.put(InvoiceItemEntry.COLUMN_ACCOUNT_ID, accountId);
                    iiv.put(InvoiceItemEntry.COLUMN_INVOICE_ID, invoiceId);
                    iiv.put(InvoiceItemEntry.COLUMN_DESCRIPTION, "Tuition Fee (" + (billingPeriod != null ? billingPeriod : "Billing Period") + ")");
                    iiv.put(InvoiceItemEntry.COLUMN_BILLING_MODEL, "MONTHLY");
                    iiv.put(InvoiceItemEntry.COLUMN_QUANTITY, 1);
                    iiv.put(InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR, totalAmount);
                    iiv.put(InvoiceItemEntry.COLUMN_AMOUNT_MINOR, totalAmount);
                    iiv.put(InvoiceItemEntry.COLUMN_CREATED_AT, createdAt > 0 ? createdAt : updatedAt);
                    db.insert(InvoiceItemEntry.TABLE_NAME, null, iiv);
                }

                // 2. Check current active allocations sum for this invoice
                int currentAllocatedMinor = getActiveAllocatedSumForInvoice(db, invoiceId);

                // 3. If no allocations exist yet, migrate any legacy rows from the 'payments' table for this invoice
                if (currentAllocatedMinor == 0) {
                    try (Cursor payCursor = db.query(PaymentEntry.TABLE_NAME, null,
                            PaymentEntry.COLUMN_INVOICE_ID + " = ?", new String[]{invoiceId},
                            null, null, PaymentEntry.COLUMN_PAYMENT_DATE + " ASC")) {
                        while (payCursor.moveToNext()) {
                            int payAmt = payCursor.getInt(payCursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_AMOUNT));
                            long payDate = payCursor.getLong(payCursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_PAYMENT_DATE));
                            String payMethod = payCursor.getString(payCursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_METHOD));
                            if (payAmt > 0) {
                                insertReconciledPaymentTransaction(
                                        db, accountId, studentId, invoiceId, invoiceDisplayId,
                                        payAmt, payDate > 0 ? payDate : updatedAt,
                                        payMethod != null ? payMethod : "Cash",
                                        "Restored payment"
                                );
                                currentAllocatedMinor += payAmt;
                            }
                        }
                    }
                }

                // 4. Determine target paid amount from saved invoice state
                int targetPaidMinor = savedPaidAmount;
                if (targetPaidMinor <= 0 && Invoice.STATUS_PAID.equalsIgnoreCase(savedStatus) && totalAmount > 0) {
                    targetPaidMinor = totalAmount;
                }

                // 5. If the invoice was marked PAID or PARTIAL (savedPaidAmount > currentAllocatedMinor),
                //    reconstruct the missing FinancialTransaction and PaymentAllocation
                if (targetPaidMinor > currentAllocatedMinor) {
                    int missingMinor = targetPaidMinor - currentAllocatedMinor;
                    insertReconciledPaymentTransaction(
                            db, accountId, studentId, invoiceId, invoiceDisplayId,
                            missingMinor, updatedAt, "Cash",
                            "Restored payment for " + (invoiceDisplayId != null ? invoiceDisplayId : "invoice")
                    );
                    currentAllocatedMinor += missingMinor;
                }

                // 6. Sync mirror columns on the invoice row
                int finalPaidMinor = Math.max(0, currentAllocatedMinor);
                String finalStatus = Invoice.STATUS_UNPAID;
                if (finalPaidMinor >= totalAmount && totalAmount > 0) {
                    finalStatus = Invoice.STATUS_PAID;
                } else if (finalPaidMinor > 0) {
                    finalStatus = Invoice.STATUS_PARTIAL;
                }

                if (finalPaidMinor != savedPaidAmount || !finalStatus.equals(savedStatus)) {
                    ContentValues upInv = new ContentValues();
                    upInv.put(InvoiceEntry.COLUMN_PAID_AMOUNT, finalPaidMinor);
                    upInv.put(InvoiceEntry.COLUMN_STATUS, finalStatus);
                    db.update(InvoiceEntry.TABLE_NAME, upInv,
                            InvoiceEntry.COLUMN_ID + " = ?", new String[]{invoiceId});
                }
            }
        }
    }

    private static int getActiveAllocatedSumForInvoice(SQLiteDatabase db, String invoiceId) {
        String sql = "SELECT COALESCE(SUM(pa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + "), 0) " +
                "FROM " + PaymentAllocationEntry.TABLE_NAME + " pa " +
                "INNER JOIN " + TransactionEntry.TABLE_NAME + " t ON pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = t." + TransactionEntry.COLUMN_ID + " " +
                "WHERE pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ? AND t." + TransactionEntry.COLUMN_IS_REVERSED + " = 0";
        try (Cursor c = db.rawQuery(sql, new String[]{invoiceId})) {
            if (c.moveToFirst()) {
                return Math.max(0, c.getInt(0));
            }
        }
        return 0;
    }

    private static void insertReconciledPaymentTransaction(SQLiteDatabase db,
                                                           String accountId,
                                                           String studentId,
                                                           String invoiceId,
                                                           @Nullable String invoiceDisplayId,
                                                           int amountMinor,
                                                           long timestamp,
                                                           String method,
                                                           String notes) {
        String txnId = UUID.randomUUID().toString();
        String txnDisplayId = generateNextTransactionId(accountId, db);

        ContentValues tv = new ContentValues();
        tv.put(TransactionEntry.COLUMN_ID, txnId);
        tv.put(TransactionEntry.COLUMN_ACCOUNT_ID, accountId);
        tv.put(TransactionEntry.COLUMN_STUDENT_ID, studentId);
        tv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, txnDisplayId);
        tv.put(TransactionEntry.COLUMN_TYPE, FinancialTransaction.TYPE_PAYMENT);
        tv.put(TransactionEntry.COLUMN_AMOUNT_MINOR, amountMinor);
        tv.put(TransactionEntry.COLUMN_TIMESTAMP, timestamp);
        tv.put(TransactionEntry.COLUMN_METHOD, method);
        tv.put(TransactionEntry.COLUMN_NOTES, notes);
        tv.put(TransactionEntry.COLUMN_IS_REVERSED, 0);
        tv.put(TransactionEntry.COLUMN_CREATED_AT, timestamp);
        db.insert(TransactionEntry.TABLE_NAME, null, tv);

        ContentValues av = new ContentValues();
        av.put(PaymentAllocationEntry.COLUMN_ID, UUID.randomUUID().toString());
        av.put(PaymentAllocationEntry.COLUMN_ACCOUNT_ID, accountId);
        av.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, txnId);
        av.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invoiceId);
        av.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, amountMinor);
        av.put(PaymentAllocationEntry.COLUMN_CREATED_AT, timestamp);
        db.insert(PaymentAllocationEntry.TABLE_NAME, null, av);
    }

    private static String generateNextTransactionId(String accountId, SQLiteDatabase db) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = String.format(Locale.US, "TXN-%04d-", year);
        int nextNumber = 1;

        try (Cursor c = db.rawQuery(
                "SELECT " + TransactionEntry.COLUMN_TXN_ID_DISPLAY +
                        " FROM " + TransactionEntry.TABLE_NAME +
                        " WHERE " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        TransactionEntry.COLUMN_TXN_ID_DISPLAY + " LIKE ? " +
                        " ORDER BY CAST(SUBSTR(" + TransactionEntry.COLUMN_TXN_ID_DISPLAY + ", 10) AS INTEGER) DESC LIMIT 1",
                new String[]{accountId, prefix + "%"})) {
            if (c.moveToFirst()) {
                try {
                    nextNumber = Integer.parseInt(c.getString(0).substring(9)) + 1;
                } catch (Exception ignored) {}
            }
        }
        return prefix + String.format(Locale.US, "%06d", nextNumber);
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

    private static void restoreAccountProfile(Context context, SQLiteDatabase db, String activeAccountId, JSONObject profileObj) {
        ContentValues values = new ContentValues();
        String fullName = optNullableString(profileObj, AccountEntry.COLUMN_FULL_NAME);
        if (fullName != null && !fullName.trim().isEmpty()) {
            values.put(AccountEntry.COLUMN_FULL_NAME, fullName.trim());
        }
        if (profileObj.has(AccountEntry.COLUMN_PHONE)) {
            values.put(AccountEntry.COLUMN_PHONE, optNullableString(profileObj, AccountEntry.COLUMN_PHONE));
        }
        if (profileObj.has(AccountEntry.COLUMN_EMAIL)) {
            values.put(AccountEntry.COLUMN_EMAIL, optNullableString(profileObj, AccountEntry.COLUMN_EMAIL));
        }
        if (profileObj.has(AccountEntry.COLUMN_ADDRESS)) {
            values.put(AccountEntry.COLUMN_ADDRESS, optNullableString(profileObj, AccountEntry.COLUMN_ADDRESS));
        }
        if (profileObj.has(AccountEntry.COLUMN_DOB)) {
            values.put(AccountEntry.COLUMN_DOB, optNullableString(profileObj, AccountEntry.COLUMN_DOB));
        }

        String picBase64 = optNullableString(profileObj, "profile_pic_base64");
        if (picBase64 != null && !picBase64.isEmpty()) {
            File destFile = new File(context.getFilesDir(), "profile_" + activeAccountId + ".jpg");
            if (decodeBase64ToFile(picBase64, destFile)) {
                values.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, destFile.getAbsolutePath());
            }
        }

        values.put(AccountEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());
        db.update(AccountEntry.TABLE_NAME, values, AccountEntry.COLUMN_ID + " = ?", new String[]{activeAccountId});
    }

    private static void restoreStudents(SQLiteDatabase db, String activeAccountId,
                                        @Nullable JSONArray arr, Map<String, String> studentIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(StudentEntry.COLUMN_ID);
            String displayId = optNullableString(obj, StudentEntry.COLUMN_STUDENT_ID_DISPLAY);
            String name = obj.optString(StudentEntry.COLUMN_NAME, "Student");

            String existingId = null;
            if (displayId != null && !displayId.isEmpty()) {
                existingId = findIdByQuery(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_ID,
                        StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " = ?",
                        new String[]{activeAccountId, displayId});
            }
            if (existingId == null) {
                existingId = findIdByQuery(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_ID,
                        StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_ID + " = ?",
                        new String[]{activeAccountId, oldId});
            }

            ContentValues cv = new ContentValues();
            cv.put(StudentEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(StudentEntry.COLUMN_STUDENT_ID_DISPLAY, displayId);
            cv.put(StudentEntry.COLUMN_NAME, name);
            cv.put(StudentEntry.COLUMN_GRADE, optNullableString(obj, StudentEntry.COLUMN_GRADE));
            cv.put(StudentEntry.COLUMN_SCHOOL, optNullableString(obj, StudentEntry.COLUMN_SCHOOL));
            cv.put(StudentEntry.COLUMN_GUARDIAN_NAME, optNullableString(obj, StudentEntry.COLUMN_GUARDIAN_NAME));
            cv.put(StudentEntry.COLUMN_GUARDIAN_PHONE, optNullableString(obj, StudentEntry.COLUMN_GUARDIAN_PHONE));
            cv.put(StudentEntry.COLUMN_ADDRESS, optNullableString(obj, StudentEntry.COLUMN_ADDRESS));
            cv.put(StudentEntry.COLUMN_IS_ACTIVE, obj.optInt(StudentEntry.COLUMN_IS_ACTIVE, 1));
            cv.put(StudentEntry.COLUMN_IS_DELETED, obj.optInt(StudentEntry.COLUMN_IS_DELETED, 0));
            cv.put(StudentEntry.COLUMN_UPDATED_AT, obj.optLong(StudentEntry.COLUMN_UPDATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                studentIdMap.put(oldId, existingId);
                db.update(StudentEntry.TABLE_NAME, cv, StudentEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, StudentEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(StudentEntry.COLUMN_ID, targetId);
                db.insert(StudentEntry.TABLE_NAME, null, cv);
                studentIdMap.put(oldId, targetId);
            }
        }
    }

    private static void restoreSubjects(SQLiteDatabase db, String activeAccountId,
                                        @Nullable JSONArray arr, Map<String, String> subjectIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(SubjectEntry.COLUMN_ID);
            String title = obj.optString(SubjectEntry.COLUMN_TITLE, "Subject");
            String level = optNullableString(obj, SubjectEntry.COLUMN_LEVEL);

            String existingId = findIdByQuery(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_ID,
                    SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND " + SubjectEntry.COLUMN_ID + " = ?",
                    new String[]{activeAccountId, oldId});

            if (existingId == null) {
                if (level != null) {
                    existingId = findIdByQuery(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_ID,
                            SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND LOWER(" + SubjectEntry.COLUMN_TITLE + ") = LOWER(?) AND LOWER(COALESCE(" + SubjectEntry.COLUMN_LEVEL + ", '')) = LOWER(?)",
                            new String[]{activeAccountId, title, level});
                } else {
                    existingId = findIdByQuery(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_ID,
                            SubjectEntry.COLUMN_ACCOUNT_ID + " = ? AND LOWER(" + SubjectEntry.COLUMN_TITLE + ") = LOWER(?) AND (" + SubjectEntry.COLUMN_LEVEL + " IS NULL OR " + SubjectEntry.COLUMN_LEVEL + " = '')",
                            new String[]{activeAccountId, title});
                }
            }

            ContentValues cv = new ContentValues();
            cv.put(SubjectEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(SubjectEntry.COLUMN_TITLE, title);
            cv.put(SubjectEntry.COLUMN_LEVEL, level);
            cv.put(SubjectEntry.COLUMN_IS_ACTIVE, obj.optInt(SubjectEntry.COLUMN_IS_ACTIVE, 1));
            cv.put(SubjectEntry.COLUMN_IS_DELETED, obj.optInt(SubjectEntry.COLUMN_IS_DELETED, 0));
            cv.put(SubjectEntry.COLUMN_UPDATED_AT, obj.optLong(SubjectEntry.COLUMN_UPDATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                subjectIdMap.put(oldId, existingId);
                db.update(SubjectEntry.TABLE_NAME, cv, SubjectEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, SubjectEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(SubjectEntry.COLUMN_ID, targetId);
                db.insert(SubjectEntry.TABLE_NAME, null, cv);
                subjectIdMap.put(oldId, targetId);
            }
        }
    }

    private static void restoreStudentSubjects(SQLiteDatabase db, @Nullable JSONArray arr,
                                               Map<String, String> studentIdMap,
                                               Map<String, String> subjectIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldStudentId = obj.getString(StudentSubjectEntry.COLUMN_STUDENT_ID);
            String oldSubjectId = obj.getString(StudentSubjectEntry.COLUMN_SUBJECT_ID);

            String studentId = studentIdMap.get(oldStudentId);
            String subjectId = subjectIdMap.get(oldSubjectId);
            if (studentId == null || subjectId == null) continue;

            ContentValues cv = new ContentValues();
            cv.put(StudentSubjectEntry.COLUMN_ID, UUID.randomUUID().toString());
            cv.put(StudentSubjectEntry.COLUMN_STUDENT_ID, studentId);
            cv.put(StudentSubjectEntry.COLUMN_SUBJECT_ID, subjectId);
            db.insertWithOnConflict(StudentSubjectEntry.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static void restoreScheduleFeeHistory(SQLiteDatabase db, String activeAccountId,
                                                  @Nullable JSONArray arr,
                                                  Map<String, String> studentIdMap,
                                                  Map<String, String> scheduleGroupIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(ScheduleFeeHistoryEntry.COLUMN_ID);
            String oldGroupId = optNullableString(obj, ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID);
            String targetGroupId = mapGroupId(oldGroupId, scheduleGroupIdMap);

            String oldStuId = optNullableString(obj, ScheduleFeeHistoryEntry.COLUMN_STUDENT_ID);
            String targetStuId = oldStuId != null ? studentIdMap.get(oldStuId) : null;

            String existingId = findIdByQuery(db, ScheduleFeeHistoryEntry.TABLE_NAME, ScheduleFeeHistoryEntry.COLUMN_ID,
                    ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleFeeHistoryEntry.COLUMN_ID + " = ?",
                    new String[]{activeAccountId, oldId});

            ContentValues cv = new ContentValues();
            cv.put(ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(ScheduleFeeHistoryEntry.COLUMN_STUDENT_ID, targetStuId);
            cv.put(ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID, targetGroupId);
            cv.put(ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, obj.optString(ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, "MONTHLY"));
            cv.put(ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR, obj.optInt(ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR, 0));
            if (!obj.isNull(ScheduleFeeHistoryEntry.COLUMN_ANCHOR_DAY)) {
                cv.put(ScheduleFeeHistoryEntry.COLUMN_ANCHOR_DAY, obj.optInt(ScheduleFeeHistoryEntry.COLUMN_ANCHOR_DAY, 1));
            }
            cv.put(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM, obj.optLong(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM, System.currentTimeMillis()));
            if (!obj.isNull(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO)) {
                cv.put(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO, obj.optLong(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO));
            } else {
                cv.putNull(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO);
            }
            cv.put(ScheduleFeeHistoryEntry.COLUMN_CREATED_AT, obj.optLong(ScheduleFeeHistoryEntry.COLUMN_CREATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                db.update(ScheduleFeeHistoryEntry.TABLE_NAME, cv, ScheduleFeeHistoryEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, ScheduleFeeHistoryEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(ScheduleFeeHistoryEntry.COLUMN_ID, targetId);
                db.insert(ScheduleFeeHistoryEntry.TABLE_NAME, null, cv);
            }
        }
    }

    private static void restoreSchedules(SQLiteDatabase db, String activeAccountId,
                                         @Nullable JSONArray arr,
                                         Map<String, String> studentIdMap,
                                         Map<String, String> subjectIdMap,
                                         Map<String, String> scheduleGroupIdMap,
                                         Map<String, String> scheduleIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(ScheduleEntry.COLUMN_ID);
            String studentId = studentIdMap.get(obj.getString(ScheduleEntry.COLUMN_STUDENT_ID));
            String subjectId = subjectIdMap.get(obj.getString(ScheduleEntry.COLUMN_SUBJECT_ID));
            if (studentId == null || subjectId == null) continue;

            String oldGroupId = optNullableString(obj, ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID);
            String targetGroupId = mapGroupId(oldGroupId, scheduleGroupIdMap);
            int dayOfWeek = obj.optInt(ScheduleEntry.COLUMN_DAY_OF_WEEK, 1);
            int startMinutes = obj.optInt(ScheduleEntry.COLUMN_START_TIME_MINUTES, 960);

            String existingId = findIdByQuery(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_ID,
                    ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " +
                            ScheduleEntry.COLUMN_SUBJECT_ID + " = ? AND " +
                            ScheduleEntry.COLUMN_DAY_OF_WEEK + " = ? AND " +
                            ScheduleEntry.COLUMN_START_TIME_MINUTES + " = ?",
                    new String[]{activeAccountId, studentId, subjectId, String.valueOf(dayOfWeek), String.valueOf(startMinutes)});

            ContentValues cv = new ContentValues();
            cv.put(ScheduleEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(ScheduleEntry.COLUMN_STUDENT_ID, studentId);
            cv.put(ScheduleEntry.COLUMN_SUBJECT_ID, subjectId);
            cv.put(ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID, targetGroupId);
            cv.put(ScheduleEntry.COLUMN_DAY_OF_WEEK, dayOfWeek);
            cv.put(ScheduleEntry.COLUMN_START_TIME_MINUTES, startMinutes);
            cv.put(ScheduleEntry.COLUMN_DURATION_MINUTES, obj.optInt(ScheduleEntry.COLUMN_DURATION_MINUTES, 60));
            cv.put(ScheduleEntry.COLUMN_START_DATE, obj.optLong(ScheduleEntry.COLUMN_START_DATE, System.currentTimeMillis()));
            cv.put(ScheduleEntry.COLUMN_END_DATE, obj.optLong(ScheduleEntry.COLUMN_END_DATE, System.currentTimeMillis() + 31536000000L));
            cv.put(ScheduleEntry.COLUMN_BILLING_MODEL, obj.optString(ScheduleEntry.COLUMN_BILLING_MODEL, "MONTHLY"));
            cv.put(ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, obj.optInt(ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, 0));
            cv.put(ScheduleEntry.COLUMN_ANCHOR_DAY, obj.optInt(ScheduleEntry.COLUMN_ANCHOR_DAY, 1));
            cv.put(ScheduleEntry.COLUMN_IS_ACTIVE, obj.optInt(ScheduleEntry.COLUMN_IS_ACTIVE, 1));
            cv.put(ScheduleEntry.COLUMN_UPDATED_AT, obj.optLong(ScheduleEntry.COLUMN_UPDATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                scheduleIdMap.put(oldId, existingId);
                db.update(ScheduleEntry.TABLE_NAME, cv, ScheduleEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, ScheduleEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(ScheduleEntry.COLUMN_ID, targetId);
                db.insert(ScheduleEntry.TABLE_NAME, null, cv);
                scheduleIdMap.put(oldId, targetId);
            }
        }
    }

    private static void restoreOccurrences(SQLiteDatabase db, String activeAccountId,
                                           @Nullable JSONArray arr,
                                           Map<String, String> studentIdMap,
                                           Map<String, String> subjectIdMap,
                                           Map<String, String> scheduleIdMap,
                                           Map<String, String> scheduleGroupIdMap,
                                           Map<String, String> occurrenceIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(OccurrenceEntry.COLUMN_ID);
            String studentId = studentIdMap.get(obj.getString(OccurrenceEntry.COLUMN_STUDENT_ID));
            if (studentId == null) continue;

            String oldSubjectId = optNullableString(obj, OccurrenceEntry.COLUMN_SUBJECT_ID);
            String subjectId = oldSubjectId != null ? subjectIdMap.get(oldSubjectId) : null;

            String oldScheduleId = optNullableString(obj, OccurrenceEntry.COLUMN_SCHEDULE_ID);
            String scheduleId = oldScheduleId != null ? scheduleIdMap.get(oldScheduleId) : null;

            String oldGroupId = optNullableString(obj, OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID);
            String groupId = mapGroupId(oldGroupId, scheduleGroupIdMap);

            long date = obj.optLong(OccurrenceEntry.COLUMN_DATE, 0L);
            int startMinutes = obj.optInt(OccurrenceEntry.COLUMN_START_TIME_MINUTES, 960);

            String existingId = findIdByQuery(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_ID,
                    OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + OccurrenceEntry.COLUMN_ID + " = ?",
                    new String[]{activeAccountId, oldId});

            if (existingId == null && subjectId != null) {
                existingId = findIdByQuery(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_ID,
                        OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                OccurrenceEntry.COLUMN_STUDENT_ID + " = ? AND " +
                                OccurrenceEntry.COLUMN_SUBJECT_ID + " = ? AND " +
                                OccurrenceEntry.COLUMN_DATE + " = ? AND " +
                                OccurrenceEntry.COLUMN_START_TIME_MINUTES + " = ?",
                        new String[]{activeAccountId, studentId, subjectId, String.valueOf(date), String.valueOf(startMinutes)});
            }

            ContentValues cv = new ContentValues();
            cv.put(OccurrenceEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(OccurrenceEntry.COLUMN_STUDENT_ID, studentId);
            cv.put(OccurrenceEntry.COLUMN_SUBJECT_ID, subjectId);
            cv.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, scheduleId);
            cv.put(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
            cv.put(OccurrenceEntry.COLUMN_DATE, date);
            cv.put(OccurrenceEntry.COLUMN_START_TIME_MINUTES, startMinutes);
            cv.put(OccurrenceEntry.COLUMN_DURATION_MINUTES, obj.optInt(OccurrenceEntry.COLUMN_DURATION_MINUTES, 60));

            if (existingId != null) {
                occurrenceIdMap.put(oldId, existingId);
                db.update(OccurrenceEntry.TABLE_NAME, cv, OccurrenceEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, OccurrenceEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(OccurrenceEntry.COLUMN_ID, targetId);
                db.insert(OccurrenceEntry.TABLE_NAME, null, cv);
                occurrenceIdMap.put(oldId, targetId);
            }
        }
    }

    private static void restoreInvoices(SQLiteDatabase db, String activeAccountId,
                                        @Nullable JSONArray arr,
                                        Map<String, String> studentIdMap,
                                        Map<String, String> invoiceIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(InvoiceEntry.COLUMN_ID);
            String studentId = studentIdMap.get(obj.getString(InvoiceEntry.COLUMN_STUDENT_ID));
            if (studentId == null) continue;

            String displayId = optNullableString(obj, InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY);
            String billingPeriod = obj.optString(InvoiceEntry.COLUMN_BILLING_PERIOD, "");
            int totalAmount = obj.optInt(InvoiceEntry.COLUMN_TOTAL_AMOUNT, 0);
            int paidAmount = obj.optInt(InvoiceEntry.COLUMN_PAID_AMOUNT, 0);
            String status = obj.optString(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_UNPAID);
            long dueDate = obj.optLong(InvoiceEntry.COLUMN_DUE_DATE, System.currentTimeMillis());
            long createdAt = obj.optLong(InvoiceEntry.COLUMN_CREATED_AT, System.currentTimeMillis());
            long periodStart = obj.optLong(InvoiceEntry.COLUMN_PERIOD_START, createdAt);
            long periodEnd = obj.optLong(InvoiceEntry.COLUMN_PERIOD_END, createdAt);
            long issueDate = obj.optLong(InvoiceEntry.COLUMN_ISSUE_DATE, createdAt);
            int subtotalMinor = obj.optInt(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, totalAmount);
            int discountMinor = obj.optInt(InvoiceEntry.COLUMN_DISCOUNT_MINOR, 0);
            String notes = optNullableString(obj, InvoiceEntry.COLUMN_NOTES);
            int isVoid = obj.optInt(InvoiceEntry.COLUMN_IS_VOID, 0);
            long updatedAt = obj.optLong(InvoiceEntry.COLUMN_UPDATED_AT, createdAt);

            String existingId = findIdByQuery(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_ID,
                    InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceEntry.COLUMN_ID + " = ?",
                    new String[]{activeAccountId, oldId});

            if (existingId == null && displayId != null && !displayId.isEmpty()) {
                existingId = findIdByQuery(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_ID,
                        InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " = ?",
                        new String[]{activeAccountId, displayId});
            }

            ContentValues cv = new ContentValues();
            cv.put(InvoiceEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(InvoiceEntry.COLUMN_STUDENT_ID, studentId);
            cv.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, displayId);
            cv.put(InvoiceEntry.COLUMN_BILLING_PERIOD, billingPeriod);
            cv.put(InvoiceEntry.COLUMN_PERIOD_START, periodStart);
            cv.put(InvoiceEntry.COLUMN_PERIOD_END, periodEnd);
            cv.put(InvoiceEntry.COLUMN_ISSUE_DATE, issueDate);
            cv.put(InvoiceEntry.COLUMN_DUE_DATE, dueDate);
            cv.put(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, subtotalMinor);
            cv.put(InvoiceEntry.COLUMN_DISCOUNT_MINOR, discountMinor);
            cv.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, totalAmount);
            cv.put(InvoiceEntry.COLUMN_PAID_AMOUNT, paidAmount);
            cv.put(InvoiceEntry.COLUMN_STATUS, status);
            cv.put(InvoiceEntry.COLUMN_NOTES, notes);
            cv.put(InvoiceEntry.COLUMN_IS_VOID, isVoid);
            cv.put(InvoiceEntry.COLUMN_CREATED_AT, createdAt);
            cv.put(InvoiceEntry.COLUMN_UPDATED_AT, updatedAt);

            if (existingId != null) {
                invoiceIdMap.put(oldId, existingId);
                db.update(InvoiceEntry.TABLE_NAME, cv, InvoiceEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, InvoiceEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(InvoiceEntry.COLUMN_ID, targetId);
                db.insert(InvoiceEntry.TABLE_NAME, null, cv);
                invoiceIdMap.put(oldId, targetId);
            }
        }
    }

    private static void restoreInvoiceItems(SQLiteDatabase db, String activeAccountId,
                                            @Nullable JSONArray arr,
                                            Map<String, String> invoiceIdMap,
                                            Map<String, String> scheduleGroupIdMap,
                                            Map<String, String> occurrenceIdMap,
                                            Map<String, String> invoiceItemIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(InvoiceItemEntry.COLUMN_ID);
            String invoiceId = invoiceIdMap.get(obj.getString(InvoiceItemEntry.COLUMN_INVOICE_ID));
            if (invoiceId == null) continue;

            String oldGroupId = optNullableString(obj, InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID);
            String groupId = mapGroupId(oldGroupId, scheduleGroupIdMap);

            String oldOccId = optNullableString(obj, InvoiceItemEntry.COLUMN_OCCURRENCE_ID);
            String occId = oldOccId != null ? occurrenceIdMap.get(oldOccId) : null;

            String description = obj.optString(InvoiceItemEntry.COLUMN_DESCRIPTION, "Tuition Charge");
            int amountMinor = obj.optInt(InvoiceItemEntry.COLUMN_AMOUNT_MINOR, 0);

            String existingId = findIdByQuery(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_ID,
                    InvoiceItemEntry.COLUMN_INVOICE_ID + " = ? AND " + InvoiceItemEntry.COLUMN_DESCRIPTION + " = ? AND " +
                            InvoiceItemEntry.COLUMN_AMOUNT_MINOR + " = ?",
                    new String[]{invoiceId, description, String.valueOf(amountMinor)});

            ContentValues cv = new ContentValues();
            cv.put(InvoiceItemEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(InvoiceItemEntry.COLUMN_INVOICE_ID, invoiceId);
            cv.put(InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
            cv.put(InvoiceItemEntry.COLUMN_OCCURRENCE_ID, occId);
            cv.put(InvoiceItemEntry.COLUMN_DESCRIPTION, description);
            cv.put(InvoiceItemEntry.COLUMN_BILLING_MODEL, obj.optString(InvoiceItemEntry.COLUMN_BILLING_MODEL, "MONTHLY"));
            cv.put(InvoiceItemEntry.COLUMN_QUANTITY, obj.optInt(InvoiceItemEntry.COLUMN_QUANTITY, 1));
            cv.put(InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR, obj.optInt(InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR, amountMinor));
            cv.put(InvoiceItemEntry.COLUMN_AMOUNT_MINOR, amountMinor);
            cv.put(InvoiceItemEntry.COLUMN_CREATED_AT, obj.optLong(InvoiceItemEntry.COLUMN_CREATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                invoiceItemIdMap.put(oldId, existingId);
                db.update(InvoiceItemEntry.TABLE_NAME, cv, InvoiceItemEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, InvoiceItemEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(InvoiceItemEntry.COLUMN_ID, targetId);
                db.insert(InvoiceItemEntry.TABLE_NAME, null, cv);
                invoiceItemIdMap.put(oldId, targetId);
            }
        }
    }

    private static void restoreAttendance(SQLiteDatabase db, String activeAccountId,
                                          @Nullable JSONArray arr,
                                          Map<String, String> occurrenceIdMap,
                                          Map<String, String> invoiceItemIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(AttendanceEntry.COLUMN_ID);
            String occId = occurrenceIdMap.get(obj.getString(AttendanceEntry.COLUMN_OCCURRENCE_ID));
            if (occId == null) continue;

            String oldMakeupId = optNullableString(obj, AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID);
            String makeupOccId = oldMakeupId != null ? occurrenceIdMap.get(oldMakeupId) : null;

            String oldBilledItemId = optNullableString(obj, AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID);
            String billedItemId = oldBilledItemId != null ? invoiceItemIdMap.get(oldBilledItemId) : null;

            String existingId = findIdByQuery(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_ID,
                    AttendanceEntry.COLUMN_OCCURRENCE_ID + " = ?", new String[]{occId});

            ContentValues cv = new ContentValues();
            cv.put(AttendanceEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(AttendanceEntry.COLUMN_OCCURRENCE_ID, occId);
            cv.put(AttendanceEntry.COLUMN_STATUS, obj.optString(AttendanceEntry.COLUMN_STATUS, AttendanceRecord.STATUS_PRESENT));
            cv.put(AttendanceEntry.COLUMN_CHARGE_DECISION, obj.optString(AttendanceEntry.COLUMN_CHARGE_DECISION, AttendanceRecord.DECISION_CHARGE));
            cv.put(AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, obj.optInt(AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, 0));
            cv.put(AttendanceEntry.COLUMN_NEEDS_RESCHEDULE, obj.optInt(AttendanceEntry.COLUMN_NEEDS_RESCHEDULE, 0));
            cv.put(AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID, makeupOccId);
            cv.put(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID, billedItemId);
            cv.put(AttendanceEntry.COLUMN_UPDATED_AT, obj.optLong(AttendanceEntry.COLUMN_UPDATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                db.update(AttendanceEntry.TABLE_NAME, cv, AttendanceEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, AttendanceEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(AttendanceEntry.COLUMN_ID, targetId);
                db.insert(AttendanceEntry.TABLE_NAME, null, cv);
            }
        }
    }

    private static void restoreLessons(SQLiteDatabase db, String activeAccountId,
                                       @Nullable JSONArray arr,
                                       Map<String, String> studentIdMap,
                                       Map<String, String> occurrenceIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(LessonEntry.COLUMN_ID);
            String studentId = studentIdMap.get(obj.getString(LessonEntry.COLUMN_STUDENT_ID));
            if (studentId == null) continue;

            String oldOccId = optNullableString(obj, LessonEntry.COLUMN_OCCURRENCE_ID);
            String occId = oldOccId != null ? occurrenceIdMap.get(oldOccId) : null;

            String existingId = findIdByQuery(db, LessonEntry.TABLE_NAME, LessonEntry.COLUMN_ID,
                    LessonEntry.COLUMN_ACCOUNT_ID + " = ? AND " + LessonEntry.COLUMN_ID + " = ?",
                    new String[]{activeAccountId, oldId});

            ContentValues cv = new ContentValues();
            cv.put(LessonEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(LessonEntry.COLUMN_STUDENT_ID, studentId);
            cv.put(LessonEntry.COLUMN_OCCURRENCE_ID, occId);
            cv.put(LessonEntry.COLUMN_TOPIC_COVERED, optNullableString(obj, LessonEntry.COLUMN_TOPIC_COVERED));
            cv.put(LessonEntry.COLUMN_HOMEWORK, optNullableString(obj, LessonEntry.COLUMN_HOMEWORK));
            cv.put(LessonEntry.COLUMN_TUTOR_NOTES, optNullableString(obj, LessonEntry.COLUMN_TUTOR_NOTES));
            cv.put(LessonEntry.COLUMN_UPDATED_AT, obj.optLong(LessonEntry.COLUMN_UPDATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                db.update(LessonEntry.TABLE_NAME, cv, LessonEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, LessonEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(LessonEntry.COLUMN_ID, targetId);
                db.insert(LessonEntry.TABLE_NAME, null, cv);
            }
        }
    }

    private static void restoreFeeAgreements(SQLiteDatabase db, String activeAccountId,
                                             @Nullable JSONArray arr,
                                             Map<String, String> studentIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(FeeAgreementEntry.COLUMN_ID);
            String studentId = studentIdMap.get(obj.getString(FeeAgreementEntry.COLUMN_STUDENT_ID));
            if (studentId == null) continue;

            String existingId = findIdByQuery(db, FeeAgreementEntry.TABLE_NAME, FeeAgreementEntry.COLUMN_ID,
                    FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?", new String[]{studentId});

            ContentValues cv = new ContentValues();
            cv.put(FeeAgreementEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(FeeAgreementEntry.COLUMN_STUDENT_ID, studentId);
            cv.put(FeeAgreementEntry.COLUMN_FEE_TYPE, obj.optString(FeeAgreementEntry.COLUMN_FEE_TYPE, "MONTHLY"));
            cv.put(FeeAgreementEntry.COLUMN_AMOUNT_MINOR, obj.optInt(FeeAgreementEntry.COLUMN_AMOUNT_MINOR, 0));
            cv.put(FeeAgreementEntry.COLUMN_UPDATED_AT, obj.optLong(FeeAgreementEntry.COLUMN_UPDATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                db.update(FeeAgreementEntry.TABLE_NAME, cv, FeeAgreementEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, FeeAgreementEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(FeeAgreementEntry.COLUMN_ID, targetId);
                db.insert(FeeAgreementEntry.TABLE_NAME, null, cv);
            }
        }
    }

    private static void restoreTransactions(SQLiteDatabase db, String activeAccountId,
                                            @Nullable JSONArray arr,
                                            Map<String, String> studentIdMap,
                                            Map<String, String> transactionIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(TransactionEntry.COLUMN_ID);
            String studentId = studentIdMap.get(obj.getString(TransactionEntry.COLUMN_STUDENT_ID));
            if (studentId == null) continue;

            String displayId = optNullableString(obj, TransactionEntry.COLUMN_TXN_ID_DISPLAY);
            long timestamp = obj.optLong(TransactionEntry.COLUMN_TIMESTAMP, System.currentTimeMillis());
            int amountMinor = obj.optInt(TransactionEntry.COLUMN_AMOUNT_MINOR, 0);

            String existingId = findIdByQuery(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_ID,
                    TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_ID + " = ?",
                    new String[]{activeAccountId, oldId});

            if (existingId == null && displayId != null && !displayId.isEmpty()) {
                existingId = findIdByQuery(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_ID,
                        TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                TransactionEntry.COLUMN_TXN_ID_DISPLAY + " = ? AND " +
                                TransactionEntry.COLUMN_TIMESTAMP + " = ? AND " +
                                TransactionEntry.COLUMN_AMOUNT_MINOR + " = ?",
                        new String[]{activeAccountId, displayId, String.valueOf(timestamp), String.valueOf(amountMinor)});
            }

            String oldParentId = optNullableString(obj, TransactionEntry.COLUMN_PARENT_TXN_ID);
            String parentTxnId = oldParentId != null ? transactionIdMap.get(oldParentId) : null;

            ContentValues cv = new ContentValues();
            cv.put(TransactionEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(TransactionEntry.COLUMN_STUDENT_ID, studentId);
            cv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, displayId);
            cv.put(TransactionEntry.COLUMN_TYPE, obj.optString(TransactionEntry.COLUMN_TYPE, FinancialTransaction.TYPE_PAYMENT));
            cv.put(TransactionEntry.COLUMN_AMOUNT_MINOR, amountMinor);
            cv.put(TransactionEntry.COLUMN_TIMESTAMP, timestamp);
            cv.put(TransactionEntry.COLUMN_METHOD, obj.optString(TransactionEntry.COLUMN_METHOD, "Cash"));
            cv.put(TransactionEntry.COLUMN_REFERENCE_NO, optNullableString(obj, TransactionEntry.COLUMN_REFERENCE_NO));
            cv.put(TransactionEntry.COLUMN_NOTES, optNullableString(obj, TransactionEntry.COLUMN_NOTES));
            cv.put(TransactionEntry.COLUMN_PARENT_TXN_ID, parentTxnId);
            cv.put(TransactionEntry.COLUMN_IS_REVERSED, obj.optInt(TransactionEntry.COLUMN_IS_REVERSED, 0));
            cv.put(TransactionEntry.COLUMN_CREATED_AT, obj.optLong(TransactionEntry.COLUMN_CREATED_AT, timestamp));

            if (existingId != null) {
                transactionIdMap.put(oldId, existingId);
                db.update(TransactionEntry.TABLE_NAME, cv, TransactionEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, TransactionEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(TransactionEntry.COLUMN_ID, targetId);
                db.insert(TransactionEntry.TABLE_NAME, null, cv);
                transactionIdMap.put(oldId, targetId);
            }
        }
    }

    private static void restorePaymentAllocations(SQLiteDatabase db, String activeAccountId,
                                                  @Nullable JSONArray arr,
                                                  Map<String, String> transactionIdMap,
                                                  Map<String, String> invoiceIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(PaymentAllocationEntry.COLUMN_ID);
            String txnId = transactionIdMap.get(obj.getString(PaymentAllocationEntry.COLUMN_TRANSACTION_ID));
            String invoiceId = invoiceIdMap.get(obj.getString(PaymentAllocationEntry.COLUMN_INVOICE_ID));
            if (txnId == null || invoiceId == null) continue;

            int allocatedMinor = obj.optInt(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, 0);

            String existingId = findIdByQuery(db, PaymentAllocationEntry.TABLE_NAME, PaymentAllocationEntry.COLUMN_ID,
                    PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ? AND " +
                            PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ? AND " +
                            PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + " = ?",
                    new String[]{txnId, invoiceId, String.valueOf(allocatedMinor)});

            ContentValues cv = new ContentValues();
            cv.put(PaymentAllocationEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, txnId);
            cv.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invoiceId);
            cv.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, allocatedMinor);
            cv.put(PaymentAllocationEntry.COLUMN_CREATED_AT, obj.optLong(PaymentAllocationEntry.COLUMN_CREATED_AT, System.currentTimeMillis()));

            if (existingId != null) {
                db.update(PaymentAllocationEntry.TABLE_NAME, cv, PaymentAllocationEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, PaymentAllocationEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(PaymentAllocationEntry.COLUMN_ID, targetId);
                db.insert(PaymentAllocationEntry.TABLE_NAME, null, cv);
            }
        }
    }

    private static void restorePayments(SQLiteDatabase db, String activeAccountId,
                                        @Nullable JSONArray arr,
                                        Map<String, String> invoiceIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(PaymentEntry.COLUMN_ID);
            String invoiceId = invoiceIdMap.get(obj.getString(PaymentEntry.COLUMN_INVOICE_ID));
            if (invoiceId == null) continue;

            int amount = obj.optInt(PaymentEntry.COLUMN_AMOUNT, 0);
            long paymentDate = obj.optLong(PaymentEntry.COLUMN_PAYMENT_DATE, System.currentTimeMillis());

            String existingId = findIdByQuery(db, PaymentEntry.TABLE_NAME, PaymentEntry.COLUMN_ID,
                    PaymentEntry.COLUMN_INVOICE_ID + " = ? AND " +
                            PaymentEntry.COLUMN_AMOUNT + " = ? AND " +
                            PaymentEntry.COLUMN_PAYMENT_DATE + " = ?",
                    new String[]{invoiceId, String.valueOf(amount), String.valueOf(paymentDate)});

            ContentValues cv = new ContentValues();
            cv.put(PaymentEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(PaymentEntry.COLUMN_INVOICE_ID, invoiceId);
            cv.put(PaymentEntry.COLUMN_AMOUNT, amount);
            cv.put(PaymentEntry.COLUMN_PAYMENT_DATE, paymentDate);
            cv.put(PaymentEntry.COLUMN_METHOD, obj.optString(PaymentEntry.COLUMN_METHOD, "Cash"));

            if (existingId != null) {
                db.update(PaymentEntry.TABLE_NAME, cv, PaymentEntry.COLUMN_ID + " = ?", new String[]{existingId});
            } else {
                String targetId = isIdInTable(db, PaymentEntry.TABLE_NAME, oldId) ? UUID.randomUUID().toString() : oldId;
                cv.put(PaymentEntry.COLUMN_ID, targetId);
                db.insert(PaymentEntry.TABLE_NAME, null, cv);
            }
        }
    }

    private static void restoreAuditLogs(SQLiteDatabase db, String activeAccountId,
                                         @Nullable JSONArray arr,
                                         Map<String, String> studentIdMap) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String oldId = obj.getString(AuditLogEntry.COLUMN_ID);
            String oldStudentId = optNullableString(obj, AuditLogEntry.COLUMN_STUDENT_ID);
            String studentId = oldStudentId != null ? studentIdMap.get(oldStudentId) : null;

            if (isIdInTable(db, AuditLogEntry.TABLE_NAME, oldId)) continue;

            ContentValues cv = new ContentValues();
            cv.put(AuditLogEntry.COLUMN_ID, oldId);
            cv.put(AuditLogEntry.COLUMN_ACCOUNT_ID, activeAccountId);
            cv.put(AuditLogEntry.COLUMN_STUDENT_ID, studentId);
            cv.put(AuditLogEntry.COLUMN_ENTITY_TYPE, obj.optString(AuditLogEntry.COLUMN_ENTITY_TYPE, "SYSTEM"));
            cv.put(AuditLogEntry.COLUMN_ENTITY_ID, obj.optString(AuditLogEntry.COLUMN_ENTITY_ID, ""));
            cv.put(AuditLogEntry.COLUMN_ACTION, obj.optString(AuditLogEntry.COLUMN_ACTION, "RESTORED"));
            cv.put(AuditLogEntry.COLUMN_DETAILS, optNullableString(obj, AuditLogEntry.COLUMN_DETAILS));
            cv.put(AuditLogEntry.COLUMN_TIMESTAMP, obj.optLong(AuditLogEntry.COLUMN_TIMESTAMP, System.currentTimeMillis()));
            db.insert(AuditLogEntry.TABLE_NAME, null, cv);
        }
    }

    @Nullable
    private static String mapGroupId(@Nullable String oldGroupId, Map<String, String> groupMap) {
        if (oldGroupId == null || oldGroupId.isEmpty()) return null;
        String mapped = groupMap.get(oldGroupId);
        if (mapped == null) {
            mapped = oldGroupId;
            groupMap.put(oldGroupId, mapped);
        }
        return mapped;
    }

    @Nullable
    private static String optNullableString(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return null;
        String val = obj.optString(key, null);
        return (val == null || "null".equals(val)) ? null : val;
    }

    @Nullable
    private static String findIdByQuery(SQLiteDatabase db, String table, String idCol, String selection, String[] args) {
        try (Cursor c = db.query(table, new String[]{idCol}, selection, args, null, null, null, "1")) {
            if (c.moveToFirst()) {
                return c.getString(0);
            }
        }
        return null;
    }

    private static boolean isIdInTable(SQLiteDatabase db, String table, String id) {
        return findIdByQuery(db, table, "id", "id = ?", new String[]{id}) != null;
    }

    private static int countRows(SQLiteDatabase db, String table, String selection, String[] args) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table + " WHERE " + selection, args)) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return 0;
    }

    private static String encodeFileToBase64(File file) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
        }
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private static boolean decodeBase64ToFile(String base64, File destFile) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                fos.write(bytes);
                fos.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
package com.abdussamadcodes.tutorsync.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BackupRestoreManager {

    public static final int CURRENT_BACKUP_VERSION = 2;

    private static final String[] SYNC_TABLE_ORDER = new String[]{
            StudentEntry.TABLE_NAME,
            SubjectEntry.TABLE_NAME,
            StudentSubjectEntry.TABLE_NAME,
            ScheduleFeeHistoryEntry.TABLE_NAME,
            ScheduleEntry.TABLE_NAME,
            OccurrenceEntry.TABLE_NAME,
            AttendanceEntry.TABLE_NAME,
            LessonEntry.TABLE_NAME,
            FeeAgreementEntry.TABLE_NAME,
            InvoiceEntry.TABLE_NAME,
            InvoiceItemEntry.TABLE_NAME,
            PaymentEntry.TABLE_NAME,
            TransactionEntry.TABLE_NAME,
            PaymentAllocationEntry.TABLE_NAME,
            AuditLogEntry.TABLE_NAME
    };

    private BackupRestoreManager() {}

    public static final class ImportResult {
        public final int insertedCount;
        public final int updatedCount;
        public final int skippedCount;

        public ImportResult(int insertedCount, int updatedCount, int skippedCount) {
            this.insertedCount = insertedCount;
            this.updatedCount = updatedCount;
            this.skippedCount = skippedCount;
        }
    }

    // =========================================================================
    // EXPORT
    // =========================================================================

    @NonNull
    public static JSONObject exportAccountBackup(
            @Nullable Context context,
            @NonNull SQLiteDatabase db,
            @NonNull String accountId) throws Exception {

        if (accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be empty.");
        }

        reconcileAccountFinancialData(db, accountId);

        JSONObject root = new JSONObject();
        root.put("backup_version", CURRENT_BACKUP_VERSION);
        root.put("exported_at", System.currentTimeMillis());
        root.put("source_account_id", accountId);

        // Export non-security profile metadata (never export password_hash or salt)
        try (Cursor c = db.query(AccountEntry.TABLE_NAME, null,
                AccountEntry.COLUMN_ID + " = ?", new String[]{accountId}, null, null, null)) {
            if (c.moveToFirst()) {
                JSONObject profile = cursorRowToJson(c);
                profile.remove(AccountEntry.COLUMN_PASSWORD_HASH);
                profile.remove(AccountEntry.COLUMN_SALT);

                String picPath = getColumnStringOrEmpty(c, AccountEntry.COLUMN_PROFILE_PIC_PATH);
                if (!picPath.isEmpty()) {
                    File picFile = new File(picPath);
                    if (picFile.exists()) {
                        byte[] bytes = readFileBytes(picFile);
                        if (bytes != null) {
                            profile.put("profile_pic_base64", Base64.encodeToString(bytes, Base64.NO_WRAP));
                        }
                    }
                }
                root.put("profile", profile);
            }
        }

        JSONObject tablesJson = new JSONObject();
        for (String table : SYNC_TABLE_ORDER) {
            JSONArray arr = new JSONArray();
            if (StudentSubjectEntry.TABLE_NAME.equals(table)) {
                try (Cursor c = db.rawQuery(
                        "SELECT ss.* FROM " + StudentSubjectEntry.TABLE_NAME + " ss " +
                                "INNER JOIN " + StudentEntry.TABLE_NAME + " s " +
                                "ON ss." + StudentSubjectEntry.COLUMN_STUDENT_ID + " = s." + StudentEntry.COLUMN_ID +
                                " AND ss." + StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = s." + StudentEntry.COLUMN_ACCOUNT_ID +
                                " WHERE s." + StudentEntry.COLUMN_ACCOUNT_ID + " = ?",
                        new String[]{accountId})) {
                    while (c.moveToNext()) {
                        arr.put(cursorRowToJson(c));
                    }
                }
            } else {
                try (Cursor c = db.query(table, null, "account_id = ?", new String[]{accountId}, null, null, null)) {
                    while (c.moveToNext()) {
                        arr.put(cursorRowToJson(c));
                    }
                }
            }
            tablesJson.put(table, arr);
        }

        root.put("tables", tablesJson);
        return root;
    }

    public static void exportAccountBackupToStream(
            @Nullable Context context,
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull OutputStream out) throws Exception {
        JSONObject root = exportAccountBackup(context, db, accountId);
        out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    // =========================================================================
    // IMPORT WITH PRE-COMMIT VALIDATION & CONFLICT RESOLUTION
    // =========================================================================

    @NonNull
    public static ImportResult importAccountBackupFromStream(
            @Nullable Context context,
            @NonNull SQLiteDatabase db,
            @NonNull String targetAccountId,
            @NonNull InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        String jsonStr = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        return importAccountBackupFromJson(context, db, targetAccountId, jsonStr);
    }

    @NonNull
    public static ImportResult importAccountBackupFromJson(
            @Nullable Context context,
            @NonNull SQLiteDatabase db,
            @NonNull String targetAccountId,
            @NonNull String jsonString) throws Exception {

        if (targetAccountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Target account ID cannot be empty.");
        }
        if (jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("Backup file is empty.");
        }

        JSONObject root;
        try {
            root = new JSONObject(jsonString);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON backup format: " + e.getMessage(), e);
        }

        JSONObject tablesJson = root.optJSONObject("tables");
        if (tablesJson == null) {
            // Support older flat backup structures if tables were placed at root
            tablesJson = new JSONObject();
            boolean foundAnyTable = false;
            for (String table : SYNC_TABLE_ORDER) {
                if (root.has(table) && root.optJSONArray(table) != null) {
                    tablesJson.put(table, root.optJSONArray(table));
                    foundAnyTable = true;
                }
            }
            if (!foundAnyTable) {
                throw new IllegalArgumentException("Backup JSON does not contain a valid 'tables' section.");
            }
        }

        boolean ownTransaction = !db.inTransaction();
        if (ownTransaction) {
            db.beginTransaction();
        }

        try {
            // Verify target account exists
            try (Cursor ac = db.query(AccountEntry.TABLE_NAME, new String[]{AccountEntry.COLUMN_ID},
                    AccountEntry.COLUMN_ID + " = ?", new String[]{targetAccountId}, null, null, null)) {
                if (!ac.moveToFirst()) {
                    throw new IllegalArgumentException("Target account does not exist.");
                }
            }

            // 1. Pre-validate all incoming records and foreign-key relationships before any write
            Map<String, List<ContentValues>> validatedRowsByTable =
                    validateAndPrepareImportData(db, targetAccountId, tablesJson);

            // 2. Conditionally update non-security profile info ONLY if same account & newer last_modified
            // Never touch password_hash, salt, username_normalized, PIN, biometric, or app preferences.
            JSONObject profile = root.optJSONObject("profile");
            String backupSourceAccountId = root.optString("source_account_id", "");
            if (profile != null) {
                if (backupSourceAccountId.isEmpty()) {
                    backupSourceAccountId = profile.optString(AccountEntry.COLUMN_ID, "");
                }
                if (targetAccountId.equals(backupSourceAccountId)) {
                    syncSameAccountProfileIfNewer(context, db, targetAccountId, profile);
                }
            }

            // 3. Upsert all validated rows in dependency order using last_modified conflict resolution
            int inserted = 0;
            int updated = 0;
            int skipped = 0;

            for (String table : SYNC_TABLE_ORDER) {
                List<ContentValues> rows = validatedRowsByTable.get(table);
                if (rows == null || rows.isEmpty()) continue;

                Set<String> validColumns = getTableColumns(db, table);
                for (ContentValues rawCv : rows) {
                    ContentValues cv = filterToValidColumns(rawCv, validColumns);
                    int outcome = upsertAccountScopedRowWithConflictResolution(db, table, cv, targetAccountId);
                    if (outcome == 1) inserted++;
                    else if (outcome == 2) updated++;
                    else skipped++;
                }
            }

            // 4. Reconcile financial records, legacy payments, invoice balances, and display IDs
            reconcileAccountFinancialData(db, targetAccountId);
            ensureUniqueStudentDisplayIds(db, targetAccountId);
            ensureUniqueInvoiceDisplayIds(db, targetAccountId);

            if (ownTransaction) {
                db.setTransactionSuccessful();
            }
            return new ImportResult(inserted, updated, skipped);
        } finally {
            if (ownTransaction) {
                db.endTransaction();
            }
        }
    }

    // =========================================================================
    // PRE-COMMIT VALIDATION
    // =========================================================================

    @NonNull
    private static Map<String, List<ContentValues>> validateAndPrepareImportData(
            @NonNull SQLiteDatabase db,
            @NonNull String targetAccountId,
            @NonNull JSONObject tablesJson) throws Exception {

        Map<String, List<ContentValues>> prepared = new HashMap<>();

        // Track known entity IDs (existing in DB for targetAccountId + incoming in backup)
        Set<String> knownStudents = queryExistingIdsForAccount(db, StudentEntry.TABLE_NAME, targetAccountId);
        Set<String> knownSubjects = queryExistingIdsForAccount(db, SubjectEntry.TABLE_NAME, targetAccountId);
        Set<String> knownSchedules = queryExistingIdsForAccount(db, ScheduleEntry.TABLE_NAME, targetAccountId);
        Set<String> knownOccurrences = queryExistingIdsForAccount(db, OccurrenceEntry.TABLE_NAME, targetAccountId);
        Set<String> knownInvoices = queryExistingIdsForAccount(db, InvoiceEntry.TABLE_NAME, targetAccountId);
        Set<String> knownInvoiceItems = queryExistingIdsForAccount(db, InvoiceItemEntry.TABLE_NAME, targetAccountId);
        Set<String> knownTransactions = queryExistingIdsForAccount(db, TransactionEntry.TABLE_NAME, targetAccountId);

        for (String table : SYNC_TABLE_ORDER) {
            JSONArray arr = tablesJson.optJSONArray(table);
            List<ContentValues> tableList = new ArrayList<>();
            prepared.put(table, tableList);
            if (arr == null) continue;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject rowObj = arr.optJSONObject(i);
                if (rowObj == null) {
                    throw new IllegalArgumentException("Malformed record at index " + i + " in table " + table);
                }

                ContentValues cv = jsonToContentValues(rowObj);
                cv.put("account_id", targetAccountId);

                String id = cv.getAsString("id");
                if (id == null || id.trim().isEmpty()) {
                    if (StudentSubjectEntry.TABLE_NAME.equals(table)) {
                        String stuId = cv.getAsString(StudentSubjectEntry.COLUMN_STUDENT_ID);
                        String subId = cv.getAsString(StudentSubjectEntry.COLUMN_SUBJECT_ID);
                        if (stuId != null && subId != null && !stuId.isEmpty() && !subId.isEmpty()) {
                            id = UUID.nameUUIDFromBytes(("ENR:" + stuId + ":" + subId).getBytes(StandardCharsets.UTF_8)).toString();
                            cv.put("id", id);
                        } else {
                            throw new IllegalArgumentException("Missing student_id or subject_id in student_subjects.");
                        }
                    } else {
                        throw new IllegalArgumentException("Missing required UUID 'id' in table " + table);
                    }
                } else {
                    id = id.trim();
                    cv.put("id", id);
                }

                long resolvedTimestamp = extractRecordLastModified(table, cv);
                long createdAt = getLongOrDefault(cv, "created_at", resolvedTimestamp);
                if (createdAt <= 0) createdAt = resolvedTimestamp;

                cv.put("created_at", createdAt);
                cv.put("updated_at", resolvedTimestamp);
                cv.put("last_modified", resolvedTimestamp);

                // Table-specific field & relationship validation
                validateRowAndRegisterId(
                        table, cv, id,
                        knownStudents, knownSubjects, knownSchedules,
                        knownOccurrences, knownInvoices, knownInvoiceItems, knownTransactions
                );

                tableList.add(cv);
            }
        }

        // Second pass for self-referential or cross-ordered optional FKs (e.g. parent_txn_id, makeup_occurrence_id, billed_invoice_item_id)
        List<ContentValues> txns = prepared.get(TransactionEntry.TABLE_NAME);
        if (txns != null) {
            for (ContentValues cv : txns) {
                String parentId = cv.getAsString(TransactionEntry.COLUMN_PARENT_TXN_ID);
                if (parentId != null && !parentId.trim().isEmpty() && !knownTransactions.contains(parentId.trim())) {
                    throw new IllegalArgumentException("Transaction " + cv.getAsString("id")
                            + " references missing parent_txn_id " + parentId);
                }
            }
        }

        List<ContentValues> attendances = prepared.get(AttendanceEntry.TABLE_NAME);
        if (attendances != null) {
            for (ContentValues cv : attendances) {
                String makeupId = cv.getAsString(AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID);
                if (makeupId != null && !makeupId.trim().isEmpty() && !knownOccurrences.contains(makeupId.trim())) {
                    throw new IllegalArgumentException("Attendance " + cv.getAsString("id")
                            + " references missing makeup_occurrence_id " + makeupId);
                }
                String billedItemId = cv.getAsString(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID);
                if (billedItemId != null && !billedItemId.trim().isEmpty() && !knownInvoiceItems.contains(billedItemId.trim())) {
                    cv.putNull(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID);
                }
            }
        }

        return prepared;
    }

    private static void validateRowAndRegisterId(
            @NonNull String table,
            @NonNull ContentValues cv,
            @NonNull String id,
            @NonNull Set<String> knownStudents,
            @NonNull Set<String> knownSubjects,
            @NonNull Set<String> knownSchedules,
            @NonNull Set<String> knownOccurrences,
            @NonNull Set<String> knownInvoices,
            @NonNull Set<String> knownInvoiceItems,
            @NonNull Set<String> knownTransactions) {

        switch (table) {
            case StudentEntry.TABLE_NAME: {
                requireNonEmpty(cv, StudentEntry.COLUMN_NAME, table, id);
                knownStudents.add(id);
                break;
            }
            case SubjectEntry.TABLE_NAME: {
                requireNonEmpty(cv, SubjectEntry.COLUMN_TITLE, table, id);
                knownSubjects.add(id);
                break;
            }
            case StudentSubjectEntry.TABLE_NAME: {
                String stuId = requireNonEmpty(cv, StudentSubjectEntry.COLUMN_STUDENT_ID, table, id);
                String subId = requireNonEmpty(cv, StudentSubjectEntry.COLUMN_SUBJECT_ID, table, id);
                requireParentExists(knownStudents, stuId, table, id, "student_id");
                requireParentExists(knownSubjects, subId, table, id, "subject_id");
                break;
            }
            case ScheduleFeeHistoryEntry.TABLE_NAME: {
                requireNonEmpty(cv, ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, table, id);
                String stuId = cv.getAsString(ScheduleFeeHistoryEntry.COLUMN_STUDENT_ID);
                if (stuId != null && !stuId.trim().isEmpty()) {
                    requireParentExists(knownStudents, stuId, table, id, "student_id");
                }
                break;
            }
            case ScheduleEntry.TABLE_NAME: {
                String stuId = requireNonEmpty(cv, ScheduleEntry.COLUMN_STUDENT_ID, table, id);
                String subId = requireNonEmpty(cv, ScheduleEntry.COLUMN_SUBJECT_ID, table, id);
                requireParentExists(knownStudents, stuId, table, id, "student_id");
                requireParentExists(knownSubjects, subId, table, id, "subject_id");
                knownSchedules.add(id);
                break;
            }
            case OccurrenceEntry.TABLE_NAME: {
                String stuId = requireNonEmpty(cv, OccurrenceEntry.COLUMN_STUDENT_ID, table, id);
                requireParentExists(knownStudents, stuId, table, id, "student_id");
                String subId = cv.getAsString(OccurrenceEntry.COLUMN_SUBJECT_ID);
                if (subId != null && !subId.trim().isEmpty()) {
                    requireParentExists(knownSubjects, subId, table, id, "subject_id");
                }
                String schedId = cv.getAsString(OccurrenceEntry.COLUMN_SCHEDULE_ID);
                if (schedId != null && !schedId.trim().isEmpty()) {
                    requireParentExists(knownSchedules, schedId, table, id, "schedule_id");
                }
                knownOccurrences.add(id);
                break;
            }
            case AttendanceEntry.TABLE_NAME: {
                String occId = requireNonEmpty(cv, AttendanceEntry.COLUMN_OCCURRENCE_ID, table, id);
                requireNonEmpty(cv, AttendanceEntry.COLUMN_STATUS, table, id);
                requireParentExists(knownOccurrences, occId, table, id, "occurrence_id");
                break;
            }
            case LessonEntry.TABLE_NAME: {
                String stuId = requireNonEmpty(cv, LessonEntry.COLUMN_STUDENT_ID, table, id);
                requireParentExists(knownStudents, stuId, table, id, "student_id");
                String occId = cv.getAsString(LessonEntry.COLUMN_OCCURRENCE_ID);
                if (occId != null && !occId.trim().isEmpty()) {
                    requireParentExists(knownOccurrences, occId, table, id, "occurrence_id");
                }
                break;
            }
            case FeeAgreementEntry.TABLE_NAME: {
                String stuId = requireNonEmpty(cv, FeeAgreementEntry.COLUMN_STUDENT_ID, table, id);
                requireNonEmpty(cv, FeeAgreementEntry.COLUMN_FEE_TYPE, table, id);
                requireParentExists(knownStudents, stuId, table, id, "student_id");
                break;
            }
            case InvoiceEntry.TABLE_NAME: {
                String stuId = requireNonEmpty(cv, InvoiceEntry.COLUMN_STUDENT_ID, table, id);
                requireParentExists(knownStudents, stuId, table, id, "student_id");
                int total = getIntOrDefault(cv, InvoiceEntry.COLUMN_TOTAL_AMOUNT, -1);
                int paid = getIntOrDefault(cv, InvoiceEntry.COLUMN_PAID_AMOUNT, 0);
                if (total < 0 || paid < 0) {
                    throw new IllegalArgumentException("Invalid negative invoice amount on invoice " + id);
                }
                knownInvoices.add(id);
                break;
            }
            case InvoiceItemEntry.TABLE_NAME: {
                String invId = requireNonEmpty(cv, InvoiceItemEntry.COLUMN_INVOICE_ID, table, id);
                requireParentExists(knownInvoices, invId, table, id, "invoice_id");
                String occId = cv.getAsString(InvoiceItemEntry.COLUMN_OCCURRENCE_ID);
                if (occId != null && !occId.trim().isEmpty()) {
                    requireParentExists(knownOccurrences, occId, table, id, "occurrence_id");
                }
                knownInvoiceItems.add(id);
                break;
            }
            case PaymentEntry.TABLE_NAME: {
                String invId = requireNonEmpty(cv, PaymentEntry.COLUMN_INVOICE_ID, table, id);
                requireParentExists(knownInvoices, invId, table, id, "invoice_id");
                int amount = getIntOrDefault(cv, PaymentEntry.COLUMN_AMOUNT, 0);
                if (amount <= 0) {
                    throw new IllegalArgumentException("Invalid payment amount on payment " + id);
                }
                break;
            }
            case TransactionEntry.TABLE_NAME: {
                String stuId = requireNonEmpty(cv, TransactionEntry.COLUMN_STUDENT_ID, table, id);
                String type = requireNonEmpty(cv, TransactionEntry.COLUMN_TYPE, table, id);
                requireParentExists(knownStudents, stuId, table, id, "student_id");
                int amountMinor = getIntOrDefault(cv, TransactionEntry.COLUMN_AMOUNT_MINOR, 0);
                if (amountMinor <= 0) {
                    throw new IllegalArgumentException("Invalid transaction amount_minor on transaction " + id);
                }
                if (!FinancialTransaction.TYPE_PAYMENT.equals(type) && !FinancialTransaction.TYPE_REFUND.equals(type)) {
                    throw new IllegalArgumentException("Unsupported transaction type '" + type + "' on transaction " + id);
                }
                knownTransactions.add(id);
                break;
            }
            case PaymentAllocationEntry.TABLE_NAME: {
                String txnId = requireNonEmpty(cv, PaymentAllocationEntry.COLUMN_TRANSACTION_ID, table, id);
                String invId = requireNonEmpty(cv, PaymentAllocationEntry.COLUMN_INVOICE_ID, table, id);
                requireParentExists(knownTransactions, txnId, table, id, "transaction_id");
                requireParentExists(knownInvoices, invId, table, id, "invoice_id");
                int alloc = getIntOrDefault(cv, PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, 0);
                if (alloc == 0) {
                    throw new IllegalArgumentException("Zero payment allocation on allocation " + id);
                }
                break;
            }
            case AuditLogEntry.TABLE_NAME: {
                requireNonEmpty(cv, AuditLogEntry.COLUMN_ENTITY_TYPE, table, id);
                requireNonEmpty(cv, AuditLogEntry.COLUMN_ENTITY_ID, table, id);
                requireNonEmpty(cv, AuditLogEntry.COLUMN_ACTION, table, id);
                break;
            }
        }
    }

    @NonNull
    private static String requireNonEmpty(
            @NonNull ContentValues cv,
            @NonNull String column,
            @NonNull String table,
            @NonNull String rowId) {
        String val = cv.getAsString(column);
        if (val == null || val.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required column '" + column + "' in table '" + table + "' for id " + rowId);
        }
        return val.trim();
    }

    private static void requireParentExists(
            @NonNull Set<String> knownParentIds,
            @NonNull String parentId,
            @NonNull String childTable,
            @NonNull String childId,
            @NonNull String fkColumn) {
        if (!knownParentIds.contains(parentId.trim())) {
            throw new IllegalArgumentException("Broken foreign key in '" + childTable + "' (id=" + childId
                    + "): " + fkColumn + "='" + parentId + "' does not exist.");
        }
    }

    // =========================================================================
    // TIMESTAMP CONFLICT RESOLUTION & UPSERT
    // =========================================================================

    /**
     * Returns:
     * 1 = inserted new record
     * 2 = updated existing record (imported last_modified > existing last_modified)
     * 0 = kept existing record (imported last_modified <= existing last_modified)
     */
    private static int upsertAccountScopedRowWithConflictResolution(
            @NonNull SQLiteDatabase db,
            @NonNull String table,
            @NonNull ContentValues cv,
            @NonNull String accountId) {

        String id = cv.getAsString("id");
        long importedLastModified = getLongOrDefault(cv, "last_modified", 0L);

        // 1. Handle tables with secondary natural UNIQUE constraints per account
        if (StudentSubjectEntry.TABLE_NAME.equals(table)) {
            String stuId = cv.getAsString(StudentSubjectEntry.COLUMN_STUDENT_ID);
            String subId = cv.getAsString(StudentSubjectEntry.COLUMN_SUBJECT_ID);
            try (Cursor c = db.query(table,
                    new String[]{"id", "last_modified", "updated_at"},
                    "account_id = ? AND (id = ? OR (" + StudentSubjectEntry.COLUMN_STUDENT_ID + " = ? AND " +
                            StudentSubjectEntry.COLUMN_SUBJECT_ID + " = ?))",
                    new String[]{accountId, id, stuId, subId}, null, null, null)) {
                if (c.moveToFirst()) {
                    String existingId = c.getString(0);
                    long existingLastMod = resolveExistingTimestamp(c, 1, 2);
                    if (importedLastModified > existingLastMod) {
                        cv.put("id", existingId);
                        db.update(table, cv, "account_id = ? AND id = ?", new String[]{accountId, existingId});
                        return 2;
                    }
                    return 0;
                }
            }
            db.insertOrThrow(table, null, cv);
            return 1;
        }

        if (AttendanceEntry.TABLE_NAME.equals(table)) {
            String occId = cv.getAsString(AttendanceEntry.COLUMN_OCCURRENCE_ID);
            try (Cursor c = db.query(table,
                    new String[]{"id", "last_modified", "updated_at", AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID},
                    "account_id = ? AND (id = ? OR " + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = ?)",
                    new String[]{accountId, id, occId}, null, null, null)) {
                if (c.moveToFirst()) {
                    String existingId = c.getString(0);
                    long existingLastMod = resolveExistingTimestamp(c, 1, 2);
                    String existingBilledItem = c.getString(3);
                    if (importedLastModified > existingLastMod) {
                        cv.put("id", existingId);
                        if ((cv.getAsString(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID) == null
                                || cv.getAsString(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID).isEmpty())
                                && existingBilledItem != null && !existingBilledItem.isEmpty()) {
                            cv.put(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID, existingBilledItem);
                        }
                        db.update(table, cv, "account_id = ? AND id = ?", new String[]{accountId, existingId});
                        return 2;
                    }
                    return 0;
                }
            }
            db.insertOrThrow(table, null, cv);
            return 1;
        }

        if (FeeAgreementEntry.TABLE_NAME.equals(table)) {
            String stuId = cv.getAsString(FeeAgreementEntry.COLUMN_STUDENT_ID);
            try (Cursor c = db.query(table,
                    new String[]{"id", "last_modified", "updated_at"},
                    "account_id = ? AND (id = ? OR " + FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?)",
                    new String[]{accountId, id, stuId}, null, null, null)) {
                if (c.moveToFirst()) {
                    String existingId = c.getString(0);
                    long existingLastMod = resolveExistingTimestamp(c, 1, 2);
                    if (importedLastModified > existingLastMod) {
                        cv.put("id", existingId);
                        db.update(table, cv, "account_id = ? AND id = ?", new String[]{accountId, existingId});
                        return 2;
                    }
                    return 0;
                }
            }
            db.insertOrThrow(table, null, cv);
            return 1;
        }

        if (PaymentAllocationEntry.TABLE_NAME.equals(table)) {
            String txnId = cv.getAsString(PaymentAllocationEntry.COLUMN_TRANSACTION_ID);
            String invId = cv.getAsString(PaymentAllocationEntry.COLUMN_INVOICE_ID);
            try (Cursor c = db.query(table,
                    new String[]{"id", "last_modified", "updated_at"},
                    "account_id = ? AND (id = ? OR (" + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ? AND " +
                            PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ?))",
                    new String[]{accountId, id, txnId, invId}, null, null, null)) {
                if (c.moveToFirst()) {
                    String existingId = c.getString(0);
                    long existingLastMod = resolveExistingTimestamp(c, 1, 2);
                    if (importedLastModified > existingLastMod) {
                        cv.put("id", existingId);
                        db.update(table, cv, "account_id = ? AND id = ?", new String[]{accountId, existingId});
                        return 2;
                    }
                    return 0;
                }
            }
            db.insertOrThrow(table, null, cv);
            return 1;
        }

        // Prevent student_id_display collision across different student UUIDs within the same account
        if (StudentEntry.TABLE_NAME.equals(table)) {
            String dispId = cv.getAsString(StudentEntry.COLUMN_STUDENT_ID_DISPLAY);
            if (dispId != null && !dispId.trim().isEmpty()) {
                try (Cursor c = db.query(table, new String[]{"id"},
                        "account_id = ? AND " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " = ? AND id != ?",
                        new String[]{accountId, dispId.trim(), id}, null, null, null)) {
                    if (c.moveToFirst()) {
                        cv.putNull(StudentEntry.COLUMN_STUDENT_ID_DISPLAY);
                    }
                }
            }
        }

        // 2. Standard lookup by composite primary key (account_id, id)
        try (Cursor c = db.query(table,
                new String[]{"last_modified", "updated_at"},
                "account_id = ? AND id = ?",
                new String[]{accountId, id}, null, null, null)) {
            if (c.moveToFirst()) {
                long existingLastMod = resolveExistingTimestamp(c, 0, 1);
                if (importedLastModified > existingLastMod) {
                    if (StudentEntry.TABLE_NAME.equals(table) && cv.getAsString(StudentEntry.COLUMN_STUDENT_ID_DISPLAY) == null) {
                        // Preserve existing student_id_display if the incoming one collided
                        cv.remove(StudentEntry.COLUMN_STUDENT_ID_DISPLAY);
                    }
                    db.update(table, cv, "account_id = ? AND id = ?", new String[]{accountId, id});
                    return 2;
                }
                return 0;
            }
        }

        db.insertOrThrow(table, null, cv);
        return 1;
    }

    private static void syncSameAccountProfileIfNewer(
            @Nullable Context context,
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull JSONObject profile) {

        long importedLastMod = profile.optLong(AccountEntry.COLUMN_LAST_MODIFIED, 0L);
        if (importedLastMod <= 0L) {
            importedLastMod = profile.optLong(AccountEntry.COLUMN_UPDATED_AT, 0L);
        }
        if (importedLastMod <= 0L) {
            importedLastMod = profile.optLong(AccountEntry.COLUMN_CREATED_AT, 0L);
        }

        long existingLastMod = 0L;
        try (Cursor c = db.query(AccountEntry.TABLE_NAME,
                new String[]{AccountEntry.COLUMN_LAST_MODIFIED, AccountEntry.COLUMN_UPDATED_AT},
                AccountEntry.COLUMN_ID + " = ?", new String[]{accountId}, null, null, null)) {
            if (c.moveToFirst()) {
                existingLastMod = resolveExistingTimestamp(c, 0, 1);
            }
        }

        if (importedLastMod <= existingLastMod) {
            return;
        }

        ContentValues pv = new ContentValues();
        if (profile.has(AccountEntry.COLUMN_FULL_NAME) && !profile.isNull(AccountEntry.COLUMN_FULL_NAME)) {
            String fullName = profile.optString(AccountEntry.COLUMN_FULL_NAME, "").trim();
            if (!fullName.isEmpty()) {
                pv.put(AccountEntry.COLUMN_FULL_NAME, fullName);
            }
        }
        if (profile.has(AccountEntry.COLUMN_PHONE)) {
            if (profile.isNull(AccountEntry.COLUMN_PHONE)) pv.putNull(AccountEntry.COLUMN_PHONE);
            else pv.put(AccountEntry.COLUMN_PHONE, profile.optString(AccountEntry.COLUMN_PHONE));
        }
        if (profile.has(AccountEntry.COLUMN_EMAIL)) {
            if (profile.isNull(AccountEntry.COLUMN_EMAIL)) pv.putNull(AccountEntry.COLUMN_EMAIL);
            else pv.put(AccountEntry.COLUMN_EMAIL, profile.optString(AccountEntry.COLUMN_EMAIL));
        }
        if (profile.has(AccountEntry.COLUMN_DOB)) {
            if (profile.isNull(AccountEntry.COLUMN_DOB)) pv.putNull(AccountEntry.COLUMN_DOB);
            else pv.put(AccountEntry.COLUMN_DOB, profile.optString(AccountEntry.COLUMN_DOB));
        }
        if (profile.has(AccountEntry.COLUMN_ADDRESS)) {
            if (profile.isNull(AccountEntry.COLUMN_ADDRESS)) pv.putNull(AccountEntry.COLUMN_ADDRESS);
            else pv.put(AccountEntry.COLUMN_ADDRESS, profile.optString(AccountEntry.COLUMN_ADDRESS));
        }
        if (context != null) {
            String base64Pic = profile.optString("profile_pic_base64", "");
            if (!base64Pic.isEmpty()) {
                try {
                    byte[] imgBytes = Base64.decode(base64Pic, Base64.NO_WRAP);
                    File destFile = new File(context.getFilesDir(), "profile_" + accountId + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        fos.write(imgBytes);
                        pv.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, destFile.getAbsolutePath());
                    }
                } catch (Exception ignored) {}
            }
        }

        pv.put(AccountEntry.COLUMN_UPDATED_AT, importedLastMod);
        pv.put(AccountEntry.COLUMN_LAST_MODIFIED, importedLastMod);
        db.update(AccountEntry.TABLE_NAME, pv, AccountEntry.COLUMN_ID + " = ?", new String[]{accountId});
    }

    // =========================================================================
    // FINANCIAL RECONCILIATION & DISPLAY ID INTEGRITY
    // =========================================================================

    /**
     * Reconciles legacy payments, unallocated invoice paid amounts, and invoice mirror balances/statuses
     * for a specific account without duplicating payments or modifying other accounts' records.
     */
    public static void reconcileAccountFinancialData(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        if (accountId.trim().isEmpty()) return;

        // 1. Bridge any legacy rows in `payments` table into `financial_transactions` and `payment_allocations`
        String legacyPaymentsSql = "SELECT p." + PaymentEntry.COLUMN_ID + ", p." + PaymentEntry.COLUMN_INVOICE_ID + ", " +
                "p." + PaymentEntry.COLUMN_AMOUNT + ", p." + PaymentEntry.COLUMN_PAYMENT_DATE + ", p." + PaymentEntry.COLUMN_METHOD + ", " +
                "p." + PaymentEntry.COLUMN_LAST_MODIFIED + ", i." + InvoiceEntry.COLUMN_STUDENT_ID + " " +
                "FROM " + PaymentEntry.TABLE_NAME + " p " +
                "INNER JOIN " + InvoiceEntry.TABLE_NAME + " i ON p." + PaymentEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + " " +
                "AND p." + PaymentEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " " +
                "WHERE p." + PaymentEntry.COLUMN_ACCOUNT_ID + " = ?";

        try (Cursor pc = db.rawQuery(legacyPaymentsSql, new String[]{accountId})) {
            while (pc.moveToNext()) {
                String payId = pc.getString(0);
                String invId = pc.getString(1);
                int amountMinor = pc.getInt(2);
                long payDate = pc.getLong(3);
                String method = pc.getString(4) != null ? pc.getString(4) : "Cash";
                long lastMod = pc.getLong(5) > 0 ? pc.getLong(5) : (payDate > 0 ? payDate : System.currentTimeMillis());
                String stuId = pc.getString(6);

                if (amountMinor <= 0) continue;

                boolean txnExists = false;
                try (Cursor tc = db.query(TransactionEntry.TABLE_NAME, new String[]{TransactionEntry.COLUMN_ID},
                        TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, payId}, null, null, null)) {
                    if (tc.moveToFirst()) txnExists = true;
                }

                if (!txnExists) {
                    String dispId = generateNextTxnDisplayId(db, accountId);
                    ContentValues tv = new ContentValues();
                    tv.put(TransactionEntry.COLUMN_ID, payId);
                    tv.put(TransactionEntry.COLUMN_ACCOUNT_ID, accountId);
                    tv.put(TransactionEntry.COLUMN_STUDENT_ID, stuId);
                    tv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, dispId);
                    tv.put(TransactionEntry.COLUMN_TYPE, FinancialTransaction.TYPE_PAYMENT);
                    tv.put(TransactionEntry.COLUMN_AMOUNT_MINOR, amountMinor);
                    tv.put(TransactionEntry.COLUMN_TIMESTAMP, payDate > 0 ? payDate : lastMod);
                    tv.put(TransactionEntry.COLUMN_METHOD, method);
                    tv.put(TransactionEntry.COLUMN_IS_REVERSED, 0);
                    tv.put(TransactionEntry.COLUMN_CREATED_AT, payDate > 0 ? payDate : lastMod);
                    tv.put(TransactionEntry.COLUMN_UPDATED_AT, lastMod);
                    tv.put(TransactionEntry.COLUMN_LAST_MODIFIED, lastMod);
                    db.insertWithOnConflict(TransactionEntry.TABLE_NAME, null, tv, SQLiteDatabase.CONFLICT_IGNORE);
                }

                String allocId = UUID.nameUUIDFromBytes(("ALLOC:" + payId + ":" + invId).getBytes(StandardCharsets.UTF_8)).toString();
                ContentValues av = new ContentValues();
                av.put(PaymentAllocationEntry.COLUMN_ID, allocId);
                av.put(PaymentAllocationEntry.COLUMN_ACCOUNT_ID, accountId);
                av.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, payId);
                av.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invId);
                av.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, amountMinor);
                av.put(PaymentAllocationEntry.COLUMN_CREATED_AT, payDate > 0 ? payDate : lastMod);
                av.put(PaymentAllocationEntry.COLUMN_UPDATED_AT, lastMod);
                av.put(PaymentAllocationEntry.COLUMN_LAST_MODIFIED, lastMod);
                db.insertWithOnConflict(PaymentAllocationEntry.TABLE_NAME, null, av, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }

        // 2. Bridge legacy invoices that have paid_amount > 0 but zero payment_allocations
        try (Cursor ic = db.query(InvoiceEntry.TABLE_NAME,
                new String[]{
                        InvoiceEntry.COLUMN_ID,
                        InvoiceEntry.COLUMN_STUDENT_ID,
                        InvoiceEntry.COLUMN_PAID_AMOUNT,
                        InvoiceEntry.COLUMN_CREATED_AT,
                        InvoiceEntry.COLUMN_LAST_MODIFIED
                },
                InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceEntry.COLUMN_PAID_AMOUNT + " > 0",
                new String[]{accountId}, null, null, null)) {

            while (ic.moveToNext()) {
                String invId = ic.getString(0);
                String stuId = ic.getString(1);
                int storedPaid = ic.getInt(2);
                long created = ic.getLong(3) > 0 ? ic.getLong(3) : System.currentTimeMillis();
                long lastMod = ic.getLong(4) > 0 ? ic.getLong(4) : created;

                boolean hasAllocations = false;
                try (Cursor ac = db.query(PaymentAllocationEntry.TABLE_NAME,
                        new String[]{PaymentAllocationEntry.COLUMN_ID},
                        PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = ? AND " + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ?",
                        new String[]{accountId, invId}, null, null, null, "1")) {
                    if (ac.moveToFirst()) hasAllocations = true;
                }

                if (!hasAllocations && storedPaid > 0) {
                    String synthTxnId = UUID.nameUUIDFromBytes(("LEGACY-INV-TXN:" + invId).getBytes(StandardCharsets.UTF_8)).toString();
                    String synthAllocId = UUID.nameUUIDFromBytes(("LEGACY-INV-ALLOC:" + invId).getBytes(StandardCharsets.UTF_8)).toString();

                    boolean txnExists = false;
                    try (Cursor tc = db.query(TransactionEntry.TABLE_NAME, new String[]{TransactionEntry.COLUMN_ID},
                            TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_ID + " = ?",
                            new String[]{accountId, synthTxnId}, null, null, null)) {
                        if (tc.moveToFirst()) txnExists = true;
                    }

                    if (!txnExists) {
                        String dispId = generateNextTxnDisplayId(db, accountId);
                        ContentValues tv = new ContentValues();
                        tv.put(TransactionEntry.COLUMN_ID, synthTxnId);
                        tv.put(TransactionEntry.COLUMN_ACCOUNT_ID, accountId);
                        tv.put(TransactionEntry.COLUMN_STUDENT_ID, stuId);
                        tv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, dispId);
                        tv.put(TransactionEntry.COLUMN_TYPE, FinancialTransaction.TYPE_PAYMENT);
                        tv.put(TransactionEntry.COLUMN_AMOUNT_MINOR, storedPaid);
                        tv.put(TransactionEntry.COLUMN_TIMESTAMP, created);
                        tv.put(TransactionEntry.COLUMN_METHOD, "Cash");
                        tv.put(TransactionEntry.COLUMN_NOTES, "Restored invoice payment");
                        tv.put(TransactionEntry.COLUMN_IS_REVERSED, 0);
                        tv.put(TransactionEntry.COLUMN_CREATED_AT, created);
                        tv.put(TransactionEntry.COLUMN_UPDATED_AT, lastMod);
                        tv.put(TransactionEntry.COLUMN_LAST_MODIFIED, lastMod);
                        db.insertWithOnConflict(TransactionEntry.TABLE_NAME, null, tv, SQLiteDatabase.CONFLICT_IGNORE);
                    }

                    ContentValues av = new ContentValues();
                    av.put(PaymentAllocationEntry.COLUMN_ID, synthAllocId);
                    av.put(PaymentAllocationEntry.COLUMN_ACCOUNT_ID, accountId);
                    av.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, synthTxnId);
                    av.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invId);
                    av.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, storedPaid);
                    av.put(PaymentAllocationEntry.COLUMN_CREATED_AT, created);
                    av.put(PaymentAllocationEntry.COLUMN_UPDATED_AT, lastMod);
                    av.put(PaymentAllocationEntry.COLUMN_LAST_MODIFIED, lastMod);
                    db.insertWithOnConflict(PaymentAllocationEntry.TABLE_NAME, null, av, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
        }

        // 3. Synchronize paid_amount and status on all invoices for this account from payment_allocations
        String invSyncSql = "SELECT i." + InvoiceEntry.COLUMN_ID + ", i." + InvoiceEntry.COLUMN_TOTAL_AMOUNT + ", " +
                "i." + InvoiceEntry.COLUMN_PAID_AMOUNT + ", i." + InvoiceEntry.COLUMN_STATUS + ", i." + InvoiceEntry.COLUMN_DUE_DATE + ", " +
                "COALESCE((SELECT SUM(pa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + ") " +
                "          FROM " + PaymentAllocationEntry.TABLE_NAME + " pa " +
                "          INNER JOIN " + TransactionEntry.TABLE_NAME + " t " +
                "          ON pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = t." + TransactionEntry.COLUMN_ID + " " +
                "          AND pa." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = t." + TransactionEntry.COLUMN_ACCOUNT_ID + " " +
                "          WHERE pa." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " " +
                "          AND pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + " " +
                "          AND t." + TransactionEntry.COLUMN_IS_REVERSED + " = 0), 0) AS derived_paid, " +
                "EXISTS(SELECT 1 FROM " + PaymentAllocationEntry.TABLE_NAME + " pa2 " +
                "       WHERE pa2." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " " +
                "       AND pa2." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + ") AS has_alloc " +
                "FROM " + InvoiceEntry.TABLE_NAME + " i " +
                "WHERE i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ?";

        long now = System.currentTimeMillis();
        try (Cursor c = db.rawQuery(invSyncSql, new String[]{accountId})) {
            while (c.moveToNext()) {
                String invId = c.getString(0);
                int totalAmount = c.getInt(1);
                int currentPaid = c.getInt(2);
                String currentStatus = c.getString(3);
                long dueDate = c.getLong(4);
                int derivedPaid = Math.max(0, c.getInt(5));
                boolean hasAlloc = c.getInt(6) == 1;

                int effectivePaid = hasAlloc ? derivedPaid : Math.max(0, currentPaid);
                String newStatus = Invoice.STATUS_UNPAID;
                if (effectivePaid >= totalAmount && totalAmount > 0) {
                    newStatus = Invoice.STATUS_PAID;
                } else if (totalAmount - effectivePaid > 0 && dueDate > 0 && now > dueDate) {
                    newStatus = Invoice.STATUS_OVERDUE;
                } else if (effectivePaid > 0) {
                    newStatus = Invoice.STATUS_PARTIAL;
                }

                if (effectivePaid != currentPaid || !newStatus.equals(currentStatus)) {
                    ContentValues up = new ContentValues();
                    up.put(InvoiceEntry.COLUMN_PAID_AMOUNT, effectivePaid);
                    up.put(InvoiceEntry.COLUMN_STATUS, newStatus);
                    db.update(InvoiceEntry.TABLE_NAME, up,
                            InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceEntry.COLUMN_ID + " = ?",
                            new String[]{accountId, invId});
                }
            }
        }

        // 4. Relink billed_invoice_item_id on attendance_records where an invoice_item bills that occurrence
        db.execSQL("UPDATE " + AttendanceEntry.TABLE_NAME +
                " SET " + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " = (" +
                "   SELECT ii." + InvoiceItemEntry.COLUMN_ID +
                "   FROM " + InvoiceItemEntry.TABLE_NAME + " ii " +
                "   INNER JOIN " + InvoiceEntry.TABLE_NAME + " i ON ii." + InvoiceItemEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID +
                "   AND ii." + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID +
                "   WHERE ii." + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = " + AttendanceEntry.TABLE_NAME + "." + AttendanceEntry.COLUMN_ACCOUNT_ID +
                "   AND ii." + InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " = " + AttendanceEntry.TABLE_NAME + "." + AttendanceEntry.COLUMN_OCCURRENCE_ID +
                "   AND i." + InvoiceEntry.COLUMN_IS_VOID + " = 0 LIMIT 1" +
                ") WHERE " + AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " IS NULL " +
                "AND EXISTS (" +
                "   SELECT 1 FROM " + InvoiceItemEntry.TABLE_NAME + " ii2 " +
                "   INNER JOIN " + InvoiceEntry.TABLE_NAME + " i2 ON ii2." + InvoiceItemEntry.COLUMN_INVOICE_ID + " = i2." + InvoiceEntry.COLUMN_ID +
                "   AND ii2." + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = i2." + InvoiceEntry.COLUMN_ACCOUNT_ID +
                "   WHERE ii2." + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = " + AttendanceEntry.TABLE_NAME + "." + AttendanceEntry.COLUMN_ACCOUNT_ID +
                "   AND ii2." + InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " = " + AttendanceEntry.TABLE_NAME + "." + AttendanceEntry.COLUMN_OCCURRENCE_ID +
                "   AND i2." + InvoiceEntry.COLUMN_IS_VOID + " = 0" +
                ")", new Object[]{accountId});
    }

    private static void ensureUniqueStudentDisplayIds(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        List<String> missingIds = new ArrayList<>();
        try (Cursor c = db.query(StudentEntry.TABLE_NAME,
                new String[]{StudentEntry.COLUMN_ID},
                StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                        StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " IS NULL OR " +
                        StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " = '')",
                new String[]{accountId}, null, null, StudentEntry.COLUMN_CREATED_AT + " ASC")) {
            while (c.moveToNext()) {
                missingIds.add(c.getString(0));
            }
        }

        for (String stuId : missingIds) {
            int nextNumber = 1;
            try (Cursor c = db.rawQuery(
                    "SELECT " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY +
                            " FROM " + StudentEntry.TABLE_NAME +
                            " WHERE " + StudentEntry.COLUMN_ACCOUNT_ID + " = ? " +
                            " AND " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " LIKE 'STU-%'" +
                            " ORDER BY CAST(SUBSTR(" + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + ", 5) AS INTEGER) DESC LIMIT 1",
                    new String[]{accountId})) {
                if (c.moveToFirst()) {
                    try {
                        nextNumber = Integer.parseInt(c.getString(0).substring(4)) + 1;
                    } catch (Exception ignored) {}
                }
            }
            ContentValues cv = new ContentValues();
            cv.put(StudentEntry.COLUMN_STUDENT_ID_DISPLAY, String.format(Locale.US, "STU-%04d", nextNumber));
            db.update(StudentEntry.TABLE_NAME, cv,
                    StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, stuId});
        }
    }

    private static void ensureUniqueInvoiceDisplayIds(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        List<String> needsDisplayId = new ArrayList<>();
        try (Cursor c = db.query(InvoiceEntry.TABLE_NAME,
                new String[]{InvoiceEntry.COLUMN_ID},
                InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                        InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " IS NULL OR " +
                        InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " = '' OR " +
                        InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " LIKE '#INV-%')",
                new String[]{accountId}, null, null, InvoiceEntry.COLUMN_CREATED_AT + " ASC")) {
            while (c.moveToNext()) {
                needsDisplayId.add(c.getString(0));
            }
        }

        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = String.format(Locale.US, "TS-%04d-", year);

        for (String invId : needsDisplayId) {
            int nextNumber = 1;
            try (Cursor c = db.rawQuery(
                    "SELECT " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY +
                            " FROM " + InvoiceEntry.TABLE_NAME +
                            " WHERE " + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? " +
                            " AND " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " LIKE ?" +
                            " ORDER BY CAST(SUBSTR(" + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + ", 9) AS INTEGER) DESC LIMIT 1",
                    new String[]{accountId, prefix + "%"})) {
                if (c.moveToFirst()) {
                    try {
                        nextNumber = Integer.parseInt(c.getString(0).substring(8)) + 1;
                    } catch (Exception ignored) {}
                }
            }
            ContentValues cv = new ContentValues();
            cv.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, prefix + String.format(Locale.US, "%06d", nextNumber));
            db.update(InvoiceEntry.TABLE_NAME, cv,
                    InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, invId});
        }
    }

    @NonNull
    private static String generateNextTxnDisplayId(@NonNull SQLiteDatabase db, @NonNull String accountId) {
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

    // =========================================================================
    // LOW-LEVEL CURSOR & JSON UTILITIES
    // =========================================================================

    private static long extractRecordLastModified(@NonNull String table, @NonNull ContentValues cv) {
        long lastMod = getLongOrDefault(cv, TutorSyncContract.COLUMN_SYNC_LAST_MODIFIED, 0L);
        if (lastMod > 0L) return lastMod;

        long updated = getLongOrDefault(cv, TutorSyncContract.COLUMN_SYNC_UPDATED_AT, 0L);
        if (updated > 0L) return updated;

        long created = getLongOrDefault(cv, TutorSyncContract.COLUMN_SYNC_CREATED_AT, 0L);
        if (created > 0L) return created;

        if (TransactionEntry.TABLE_NAME.equals(table) || AuditLogEntry.TABLE_NAME.equals(table)) {
            long ts = getLongOrDefault(cv, "timestamp", 0L);
            if (ts > 0L) return ts;
        }
        if (PaymentEntry.TABLE_NAME.equals(table)) {
            long pd = getLongOrDefault(cv, PaymentEntry.COLUMN_PAYMENT_DATE, 0L);
            if (pd > 0L) return pd;
        }
        if (ScheduleFeeHistoryEntry.TABLE_NAME.equals(table)) {
            long ef = getLongOrDefault(cv, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM, 0L);
            if (ef > 0L) return ef;
        }
        if (OccurrenceEntry.TABLE_NAME.equals(table)) {
            long dt = getLongOrDefault(cv, OccurrenceEntry.COLUMN_DATE, 0L);
            if (dt > 0L) return dt;
        }
        return 1L;
    }

    private static long resolveExistingTimestamp(@NonNull Cursor c, int lastModIdx, int updatedIdx) {
        long lastMod = (!c.isNull(lastModIdx)) ? c.getLong(lastModIdx) : 0L;
        long updated = (!c.isNull(updatedIdx)) ? c.getLong(updatedIdx) : 0L;
        return Math.max(lastMod, updated);
    }

    @NonNull
    private static Set<String> queryExistingIdsForAccount(
            @NonNull SQLiteDatabase db,
            @NonNull String table,
            @NonNull String accountId) {
        Set<String> ids = new HashSet<>();
        try (Cursor c = db.query(table, new String[]{"id"}, "account_id = ?", new String[]{accountId}, null, null, null)) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    @NonNull
    private static Set<String> getTableColumns(@NonNull SQLiteDatabase db, @NonNull String table) {
        Set<String> cols = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (nameIdx >= 0) {
                    cols.add(c.getString(nameIdx));
                }
            }
        }
        return cols;
    }

    @NonNull
    private static ContentValues filterToValidColumns(@NonNull ContentValues source, @NonNull Set<String> validColumns) {
        ContentValues filtered = new ContentValues();
        for (String key : source.keySet()) {
            if (validColumns.contains(key)) {
                Object val = source.get(key);
                if (val == null) {
                    filtered.putNull(key);
                } else if (val instanceof Long) {
                    filtered.put(key, (Long) val);
                } else if (val instanceof Integer) {
                    filtered.put(key, (Integer) val);
                } else if (val instanceof Double) {
                    filtered.put(key, (Double) val);
                } else if (val instanceof Float) {
                    filtered.put(key, (Float) val);
                } else if (val instanceof Boolean) {
                    filtered.put(key, ((Boolean) val) ? 1 : 0);
                } else {
                    filtered.put(key, String.valueOf(val));
                }
            }
        }
        return filtered;
    }

    private static long getLongOrDefault(@NonNull ContentValues cv, @NonNull String key, long def) {
        if (!cv.containsKey(key) || cv.get(key) == null) return def;
        try {
            Long val = cv.getAsLong(key);
            return val != null ? val : def;
        } catch (Exception e) {
            try {
                return Long.parseLong(String.valueOf(cv.get(key)));
            } catch (Exception ex) {
                return def;
            }
        }
    }

    private static int getIntOrDefault(@NonNull ContentValues cv, @NonNull String key, int def) {
        if (!cv.containsKey(key) || cv.get(key) == null) return def;
        try {
            Integer val = cv.getAsInteger(key);
            return val != null ? val : def;
        } catch (Exception e) {
            try {
                return Integer.parseInt(String.valueOf(cv.get(key)));
            } catch (Exception ex) {
                return def;
            }
        }
    }

    @NonNull
    private static String getColumnStringOrEmpty(@NonNull Cursor cursor, @NonNull String columnName) {
        int idx = cursor.getColumnIndex(columnName);
        if (idx != -1 && !cursor.isNull(idx)) {
            String val = cursor.getString(idx);
            return val != null ? val : "";
        }
        return "";
    }

    @NonNull
    public static JSONObject cursorRowToJson(@NonNull Cursor c) throws Exception {
        JSONObject obj = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++) {
            String col = c.getColumnName(i);
            int type = c.getType(i);
            if (type == Cursor.FIELD_TYPE_NULL) {
                obj.put(col, JSONObject.NULL);
            } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                obj.put(col, c.getLong(i));
            } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                obj.put(col, c.getDouble(i));
            } else {
                obj.put(col, c.getString(i));
            }
        }
        return obj;
    }

    @NonNull
    public static ContentValues jsonToContentValues(@NonNull JSONObject obj) throws Exception {
        ContentValues cv = new ContentValues();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (obj.isNull(key)) {
                cv.putNull(key);
            } else {
                Object val = obj.get(key);
                if (val instanceof Long) {
                    cv.put(key, (Long) val);
                } else if (val instanceof Integer) {
                    cv.put(key, (Integer) val);
                } else if (val instanceof Double) {
                    cv.put(key, (Double) val);
                } else if (val instanceof Boolean) {
                    cv.put(key, ((Boolean) val) ? 1 : 0);
                } else {
                    cv.put(key, String.valueOf(val));
                }
            }
        }
        return cv;
    }

    @Nullable
    private static byte[] readFileBytes(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
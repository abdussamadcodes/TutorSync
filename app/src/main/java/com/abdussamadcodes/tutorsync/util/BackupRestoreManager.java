package com.abdussamadcodes.tutorsync.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class BackupRestoreManager {

    public static final int CURRENT_BACKUP_VERSION = 2;

    private static final int OUTCOME_SKIPPED = 0;
    private static final int OUTCOME_INSERTED = 1;
    private static final int OUTCOME_UPDATED = 2;
    private static final int OUTCOME_CONFLICT = 3;

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
        public final int conflictCount;
        @NonNull
        public final List<String> conflicts;

        public ImportResult(int insertedCount, int updatedCount, int skippedCount) {
            this(insertedCount, updatedCount, skippedCount, 0, Collections.emptyList());
        }

        public ImportResult(int insertedCount,
                            int updatedCount,
                            int skippedCount,
                            int conflictCount,
                            @Nullable List<String> conflicts) {
            this.insertedCount = insertedCount;
            this.updatedCount = updatedCount;
            this.skippedCount = skippedCount;
            this.conflictCount = conflictCount;
            this.conflicts = conflicts != null
                    ? Collections.unmodifiableList(new ArrayList<>(conflicts))
                    : Collections.emptyList();
        }

        public boolean hasConflicts() {
            return conflictCount > 0;
        }
    }

    // =========================================================================
    // EXPORT (READ-ONLY)
    // =========================================================================

    @NonNull
    public static JSONObject exportAccountBackup(
            @Nullable Context context,
            @NonNull SQLiteDatabase db,
            @NonNull String accountId) throws Exception {

        if (accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be empty.");
        }

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

            // 3. Upsert non-financial rows with last_modified conflict resolution,
            //    and insert financial/audit records strictly as insert-only with conflict reporting.
            int inserted = 0;
            int updated = 0;
            int skipped = 0;
            List<String> conflicts = new ArrayList<>();

            for (String table : SYNC_TABLE_ORDER) {
                List<ContentValues> rows = validatedRowsByTable.get(table);
                if (rows == null || rows.isEmpty()) continue;

                Set<String> validColumns = getTableColumns(db, table);
                for (ContentValues rawCv : rows) {
                    ContentValues cv = filterToValidColumns(rawCv, validColumns);
                    int outcome = upsertAccountScopedRowWithConflictResolution(
                            db, table, cv, targetAccountId, conflicts);
                    if (outcome == OUTCOME_INSERTED) {
                        inserted++;
                    } else if (outcome == OUTCOME_UPDATED) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            // 4. Bridge legacy payments only if legacy payment rows were present,
            //    and derive invoice paid_amount/status strictly from actual allocations.
            List<ContentValues> importedLegacyPayments = validatedRowsByTable.get(PaymentEntry.TABLE_NAME);
            if (importedLegacyPayments != null && !importedLegacyPayments.isEmpty()) {
                bridgeLegacyPaymentsForAccount(db, targetAccountId);
            }
            syncAccountInvoiceMirrorColumns(db, targetAccountId);

            List<ContentValues> importedItems = validatedRowsByTable.get(InvoiceItemEntry.TABLE_NAME);
            List<ContentValues> importedAttendance = validatedRowsByTable.get(AttendanceEntry.TABLE_NAME);
            if ((importedItems != null && !importedItems.isEmpty())
                    || (importedAttendance != null && !importedAttendance.isEmpty())) {
                relinkBilledAttendanceRecords(db, targetAccountId);
            }

            ensureUniqueStudentDisplayIds(db, targetAccountId);
            ensureUniqueInvoiceDisplayIds(db, targetAccountId);

            if (ownTransaction) {
                db.setTransactionSuccessful();
            }
            return new ImportResult(inserted, updated, skipped, conflicts.size(), conflicts);
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

        Set<String> knownStudents = queryExistingIdsForAccount(db, StudentEntry.TABLE_NAME, targetAccountId);
        Set<String> knownSubjects = queryExistingIdsForAccount(db, SubjectEntry.TABLE_NAME, targetAccountId);
        Set<String> knownSchedules = queryExistingIdsForAccount(db, ScheduleEntry.TABLE_NAME, targetAccountId);
        Set<String> knownOccurrences = queryExistingIdsForAccount(db, OccurrenceEntry.TABLE_NAME, targetAccountId);
        Set<String> knownInvoices = queryExistingIdsForAccount(db, InvoiceEntry.TABLE_NAME, targetAccountId);
        Set<String> knownInvoiceItems = queryExistingIdsForAccount(db, InvoiceItemEntry.TABLE_NAME, targetAccountId);
        Set<String> knownTransactions = queryExistingIdsForAccount(db, TransactionEntry.TABLE_NAME, targetAccountId);
        knownTransactions.addAll(queryExistingIdsForAccount(db, PaymentEntry.TABLE_NAME, targetAccountId));

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

                validateRowAndRegisterId(
                        table, cv, id,
                        knownStudents, knownSubjects, knownSchedules,
                        knownOccurrences, knownInvoices, knownInvoiceItems, knownTransactions
                );

                tableList.add(cv);
            }
        }

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
                    throw new IllegalArgumentException("Attendance " + cv.getAsString("id")
                            + " references missing billed_invoice_item_id " + billedItemId);
                }
            }
        }

        List<ContentValues> occurrences = prepared.get(OccurrenceEntry.TABLE_NAME);
        if (occurrences != null) {
            for (ContentValues cv : occurrences) {
                String reschedId = cv.getAsString(OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID);
                if (reschedId != null && !reschedId.trim().isEmpty() && !knownOccurrences.contains(reschedId.trim())) {
                    throw new IllegalArgumentException("Occurrence " + cv.getAsString("id")
                            + " references missing rescheduled_from_id " + reschedId);
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
                String name = cv.getAsString(StudentEntry.COLUMN_NAME);
                if (name == null || name.trim().isEmpty()) {
                    throw new IllegalArgumentException("Student " + id + " is missing required name.");
                }
                knownStudents.add(id);
                break;
            }
            case SubjectEntry.TABLE_NAME: {
                String title = cv.getAsString(SubjectEntry.COLUMN_TITLE);
                if (title == null || title.trim().isEmpty()) {
                    throw new IllegalArgumentException("Subject " + id + " is missing required title.");
                }
                knownSubjects.add(id);
                break;
            }
            case StudentSubjectEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(StudentSubjectEntry.COLUMN_STUDENT_ID), "student_id", knownStudents);
                requireKnownFk(table, id, cv.getAsString(StudentSubjectEntry.COLUMN_SUBJECT_ID), "subject_id", knownSubjects);
                break;
            }
            case ScheduleFeeHistoryEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(ScheduleFeeHistoryEntry.COLUMN_STUDENT_ID), "student_id", knownStudents);
                break;
            }
            case ScheduleEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(ScheduleEntry.COLUMN_STUDENT_ID), "student_id", knownStudents);
                requireKnownFk(table, id, cv.getAsString(ScheduleEntry.COLUMN_SUBJECT_ID), "subject_id", knownSubjects);
                knownSchedules.add(id);
                break;
            }
            case OccurrenceEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(OccurrenceEntry.COLUMN_STUDENT_ID), "student_id", knownStudents);
                requireKnownFk(table, id, cv.getAsString(OccurrenceEntry.COLUMN_SUBJECT_ID), "subject_id", knownSubjects);
                String schedId = cv.getAsString(OccurrenceEntry.COLUMN_SCHEDULE_ID);
                if (schedId != null && !schedId.trim().isEmpty() && !knownSchedules.contains(schedId.trim())) {
                    throw new IllegalArgumentException("Occurrence " + id + " references missing schedule_id " + schedId);
                }
                knownOccurrences.add(id);
                break;
            }
            case AttendanceEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(AttendanceEntry.COLUMN_OCCURRENCE_ID), "occurrence_id", knownOccurrences);
                break;
            }
            case LessonEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(LessonEntry.COLUMN_STUDENT_ID), "student_id", knownStudents);
                requireKnownFk(table, id, cv.getAsString(LessonEntry.COLUMN_OCCURRENCE_ID), "occurrence_id", knownOccurrences);
                break;
            }
            case FeeAgreementEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(FeeAgreementEntry.COLUMN_STUDENT_ID), "student_id", knownStudents);
                break;
            }
            case InvoiceEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(InvoiceEntry.COLUMN_STUDENT_ID), "student_id", knownStudents);
                knownInvoices.add(id);
                break;
            }
            case InvoiceItemEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(InvoiceItemEntry.COLUMN_INVOICE_ID), "invoice_id", knownInvoices);
                String occId = cv.getAsString(InvoiceItemEntry.COLUMN_OCCURRENCE_ID);
                if (occId != null && !occId.trim().isEmpty() && !knownOccurrences.contains(occId.trim())) {
                    throw new IllegalArgumentException("InvoiceItem " + id + " references missing occurrence_id " + occId);
                }
                knownInvoiceItems.add(id);
                break;
            }
            case PaymentEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(PaymentEntry.COLUMN_INVOICE_ID), "invoice_id", knownInvoices);
                knownTransactions.add(id);
                break;
            }
            case TransactionEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(TransactionEntry.COLUMN_STUDENT_ID), "student_id", knownStudents);
                knownTransactions.add(id);
                break;
            }
            case PaymentAllocationEntry.TABLE_NAME: {
                requireKnownFk(table, id, cv.getAsString(PaymentAllocationEntry.COLUMN_TRANSACTION_ID), "transaction_id", knownTransactions);
                requireKnownFk(table, id, cv.getAsString(PaymentAllocationEntry.COLUMN_INVOICE_ID), "invoice_id", knownInvoices);
                break;
            }
            case AuditLogEntry.TABLE_NAME: {
                String stuId = cv.getAsString(AuditLogEntry.COLUMN_STUDENT_ID);
                if (stuId != null && !stuId.trim().isEmpty() && !knownStudents.contains(stuId.trim())) {
                    throw new IllegalArgumentException("AuditLog " + id + " references missing student_id " + stuId);
                }
                break;
            }
            default:
                break;
        }
    }

    private static void requireKnownFk(
            @NonNull String table,
            @NonNull String rowId,
            @Nullable String fkValue,
            @NonNull String fkColumn,
            @NonNull Set<String> knownIds) {
        if (fkValue == null || fkValue.trim().isEmpty() || !knownIds.contains(fkValue.trim())) {
            throw new IllegalArgumentException("Record " + rowId + " in " + table
                    + " references missing " + fkColumn + ": " + fkValue);
        }
    }

    // =========================================================================
    // UPSERT & INSERT-ONLY FINANCIAL CONFLICT HANDLING
    // =========================================================================

    private static int upsertAccountScopedRowWithConflictResolution(
            @NonNull SQLiteDatabase db,
            @NonNull String table,
            @NonNull ContentValues cv,
            @NonNull String accountId,
            @NonNull List<String> conflicts) {

        String id = cv.getAsString("id");
        long importedLastModified = getLongOrDefault(cv, "last_modified", 0L);

        // ---------------------------------------------------------------------
        // 1. Insert-only financial & audit tables: NEVER overwrite existing rows
        // ---------------------------------------------------------------------
        if (PaymentEntry.TABLE_NAME.equals(table)) {
            return insertOrSkipPaymentRow(db, cv, accountId, id, importedLastModified, conflicts);
        }
        if (TransactionEntry.TABLE_NAME.equals(table)) {
            return insertOrSkipTransactionRow(db, cv, accountId, id, importedLastModified, conflicts);
        }
        if (PaymentAllocationEntry.TABLE_NAME.equals(table)) {
            return insertOrSkipPaymentAllocationRow(db, cv, accountId, id, importedLastModified, conflicts);
        }
        if (AuditLogEntry.TABLE_NAME.equals(table)) {
            return insertOrSkipAuditLogRow(db, cv, accountId, id, importedLastModified, conflicts);
        }

        // ---------------------------------------------------------------------
        // 2. Natural-key deduplication for non-financial tables
        // ---------------------------------------------------------------------
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
                        return OUTCOME_UPDATED;
                    }
                    return OUTCOME_SKIPPED;
                }
            }
            db.insertOrThrow(table, null, cv);
            return OUTCOME_INSERTED;
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
                        return OUTCOME_UPDATED;
                    }
                    return OUTCOME_SKIPPED;
                }
            }
            db.insertOrThrow(table, null, cv);
            return OUTCOME_INSERTED;
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
                        return OUTCOME_UPDATED;
                    }
                    return OUTCOME_SKIPPED;
                }
            }
            db.insertOrThrow(table, null, cv);
            return OUTCOME_INSERTED;
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

        // Prevent invoice_id_display collision across different invoice UUIDs within the same account
        if (InvoiceEntry.TABLE_NAME.equals(table)) {
            String dispId = cv.getAsString(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY);
            if (dispId != null && !dispId.trim().isEmpty()) {
                try (Cursor c = db.query(table, new String[]{"id"},
                        "account_id = ? AND " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " = ? AND id != ?",
                        new String[]{accountId, dispId.trim(), id}, null, null, null)) {
                    if (c.moveToFirst()) {
                        cv.putNull(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY);
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 3. Standard lookup by composite primary key (account_id, id)
        // ---------------------------------------------------------------------
        try (Cursor c = db.query(table,
                new String[]{"last_modified", "updated_at"},
                "account_id = ? AND id = ?",
                new String[]{accountId, id}, null, null, null)) {
            if (c.moveToFirst()) {
                long existingLastMod = resolveExistingTimestamp(c, 0, 1);
                if (importedLastModified > existingLastMod) {
                    if (StudentEntry.TABLE_NAME.equals(table)
                            && cv.getAsString(StudentEntry.COLUMN_STUDENT_ID_DISPLAY) == null) {
                        cv.remove(StudentEntry.COLUMN_STUDENT_ID_DISPLAY);
                    }
                    if (InvoiceEntry.TABLE_NAME.equals(table)) {
                        if (cv.getAsString(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY) == null) {
                            cv.remove(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY);
                        }
                        // Never allow imported invoice fields to overwrite derived paid_amount/status directly
                        cv.remove(InvoiceEntry.COLUMN_PAID_AMOUNT);
                        cv.remove(InvoiceEntry.COLUMN_STATUS);
                    }
                    db.update(table, cv, "account_id = ? AND id = ?", new String[]{accountId, id});
                    return OUTCOME_UPDATED;
                }
                return OUTCOME_SKIPPED;
            }
        }

        if (InvoiceEntry.TABLE_NAME.equals(table)) {
            // Initialize paid_amount to 0 on insert; actual paid_amount and status are derived from allocations
            cv.put(InvoiceEntry.COLUMN_PAID_AMOUNT, 0);
            cv.put(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_UNPAID);
        }

        db.insertOrThrow(table, null, cv);
        return OUTCOME_INSERTED;
    }

    private static int insertOrSkipPaymentRow(
            @NonNull SQLiteDatabase db,
            @NonNull ContentValues cv,
            @NonNull String accountId,
            @NonNull String id,
            long importedLastModified,
            @NonNull List<String> conflicts) {

        String importedInvoiceId = normalizeStr(cv.getAsString(PaymentEntry.COLUMN_INVOICE_ID));
        int importedAmount = getIntOrDefault(cv, PaymentEntry.COLUMN_AMOUNT, 0);
        long importedDate = getLongOrDefault(cv, PaymentEntry.COLUMN_PAYMENT_DATE, 0L);
        String importedMethod = normalizeStr(cv.getAsString(PaymentEntry.COLUMN_METHOD));

        try (Cursor c = db.query(PaymentEntry.TABLE_NAME,
                new String[]{
                        PaymentEntry.COLUMN_INVOICE_ID,
                        PaymentEntry.COLUMN_AMOUNT,
                        PaymentEntry.COLUMN_PAYMENT_DATE,
                        PaymentEntry.COLUMN_METHOD,
                        PaymentEntry.COLUMN_LAST_MODIFIED,
                        PaymentEntry.COLUMN_UPDATED_AT
                },
                PaymentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + PaymentEntry.COLUMN_ID + " = ?",
                new String[]{accountId, id}, null, null, null)) {
            if (c.moveToFirst()) {
                String existingInvoiceId = normalizeStr(c.getString(0));
                int existingAmount = c.getInt(1);
                long existingDate = c.getLong(2);
                String existingMethod = normalizeStr(c.getString(3));
                long existingLastMod = resolveExistingTimestamp(c, 4, 5);

                boolean dataDiffers = !Objects.equals(existingInvoiceId, importedInvoiceId)
                        || existingAmount != importedAmount
                        || existingDate != importedDate
                        || !Objects.equals(existingMethod, importedMethod);

                if (dataDiffers || importedLastModified > existingLastMod) {
                    conflicts.add("Payment conflict for ID " + id + ": preserved existing record.");
                    return OUTCOME_CONFLICT;
                }
                return OUTCOME_SKIPPED;
            }
        }

        // Also check if this legacy payment ID already exists in financial_transactions
        try (Cursor tc = db.query(TransactionEntry.TABLE_NAME,
                new String[]{TransactionEntry.COLUMN_AMOUNT_MINOR, TransactionEntry.COLUMN_METHOD},
                TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_ID + " = ?",
                new String[]{accountId, id}, null, null, null)) {
            if (tc.moveToFirst()) {
                int existingTxnAmount = tc.getInt(0);
                String existingTxnMethod = normalizeStr(tc.getString(1));
                if (existingTxnAmount != importedAmount
                        || (!importedMethod.isEmpty() && !Objects.equals(existingTxnMethod, importedMethod))) {
                    conflicts.add("Payment conflict with existing transaction for ID " + id + ": preserved existing record.");
                    return OUTCOME_CONFLICT;
                }
            }
        }

        db.insertOrThrow(PaymentEntry.TABLE_NAME, null, cv);
        return OUTCOME_INSERTED;
    }

    private static int insertOrSkipTransactionRow(
            @NonNull SQLiteDatabase db,
            @NonNull ContentValues cv,
            @NonNull String accountId,
            @NonNull String id,
            long importedLastModified,
            @NonNull List<String> conflicts) {

        String importedStuId = normalizeStr(cv.getAsString(TransactionEntry.COLUMN_STUDENT_ID));
        String importedType = normalizeStr(cv.getAsString(TransactionEntry.COLUMN_TYPE));
        int importedAmount = getIntOrDefault(cv, TransactionEntry.COLUMN_AMOUNT_MINOR, 0);
        String importedMethod = normalizeStr(cv.getAsString(TransactionEntry.COLUMN_METHOD));
        String importedRef = normalizeStr(cv.getAsString(TransactionEntry.COLUMN_REFERENCE_NO));
        String importedNotes = normalizeStr(cv.getAsString(TransactionEntry.COLUMN_NOTES));
        String importedParentId = normalizeStr(cv.getAsString(TransactionEntry.COLUMN_PARENT_TXN_ID));
        int importedReversed = getIntOrDefault(cv, TransactionEntry.COLUMN_IS_REVERSED, 0);

        try (Cursor c = db.query(TransactionEntry.TABLE_NAME,
                new String[]{
                        TransactionEntry.COLUMN_STUDENT_ID,
                        TransactionEntry.COLUMN_TYPE,
                        TransactionEntry.COLUMN_AMOUNT_MINOR,
                        TransactionEntry.COLUMN_METHOD,
                        TransactionEntry.COLUMN_REFERENCE_NO,
                        TransactionEntry.COLUMN_NOTES,
                        TransactionEntry.COLUMN_PARENT_TXN_ID,
                        TransactionEntry.COLUMN_IS_REVERSED,
                        TransactionEntry.COLUMN_LAST_MODIFIED,
                        TransactionEntry.COLUMN_UPDATED_AT
                },
                TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_ID + " = ?",
                new String[]{accountId, id}, null, null, null)) {
            if (c.moveToFirst()) {
                String existingStuId = normalizeStr(c.getString(0));
                String existingType = normalizeStr(c.getString(1));
                int existingAmount = c.getInt(2);
                String existingMethod = normalizeStr(c.getString(3));
                String existingRef = normalizeStr(c.getString(4));
                String existingNotes = normalizeStr(c.getString(5));
                String existingParentId = normalizeStr(c.getString(6));
                int existingReversed = c.getInt(7);
                long existingLastMod = resolveExistingTimestamp(c, 8, 9);

                boolean dataDiffers = !Objects.equals(existingStuId, importedStuId)
                        || !Objects.equals(existingType, importedType)
                        || existingAmount != importedAmount
                        || !Objects.equals(existingMethod, importedMethod)
                        || !Objects.equals(existingRef, importedRef)
                        || !Objects.equals(existingNotes, importedNotes)
                        || !Objects.equals(existingParentId, importedParentId)
                        || existingReversed != importedReversed;

                if (dataDiffers || importedLastModified > existingLastMod) {
                    conflicts.add("FinancialTransaction conflict for ID " + id + ": preserved existing record.");
                    return OUTCOME_CONFLICT;
                }
                return OUTCOME_SKIPPED;
            }
        }

        // Ensure txn_id_display does not collide with another transaction in the same account
        String dispId = cv.getAsString(TransactionEntry.COLUMN_TXN_ID_DISPLAY);
        if (dispId == null || dispId.trim().isEmpty()) {
            cv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, generateNextTxnDisplayId(db, accountId));
        } else {
            try (Cursor dc = db.query(TransactionEntry.TABLE_NAME, new String[]{TransactionEntry.COLUMN_ID},
                    TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            TransactionEntry.COLUMN_TXN_ID_DISPLAY + " = ? AND " +
                            TransactionEntry.COLUMN_ID + " != ?",
                    new String[]{accountId, dispId.trim(), id}, null, null, null)) {
                if (dc.moveToFirst()) {
                    cv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, generateNextTxnDisplayId(db, accountId));
                }
            }
        }

        db.insertOrThrow(TransactionEntry.TABLE_NAME, null, cv);
        return OUTCOME_INSERTED;
    }

    private static int insertOrSkipPaymentAllocationRow(
            @NonNull SQLiteDatabase db,
            @NonNull ContentValues cv,
            @NonNull String accountId,
            @NonNull String id,
            long importedLastModified,
            @NonNull List<String> conflicts) {

        String txnId = normalizeStr(cv.getAsString(PaymentAllocationEntry.COLUMN_TRANSACTION_ID));
        String invId = normalizeStr(cv.getAsString(PaymentAllocationEntry.COLUMN_INVOICE_ID));
        int importedAllocated = getIntOrDefault(cv, PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, 0);

        try (Cursor c = db.query(PaymentAllocationEntry.TABLE_NAME,
                new String[]{
                        PaymentAllocationEntry.COLUMN_ID,
                        PaymentAllocationEntry.COLUMN_TRANSACTION_ID,
                        PaymentAllocationEntry.COLUMN_INVOICE_ID,
                        PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR,
                        PaymentAllocationEntry.COLUMN_LAST_MODIFIED,
                        PaymentAllocationEntry.COLUMN_UPDATED_AT
                },
                PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                        PaymentAllocationEntry.COLUMN_ID + " = ? OR (" +
                        PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ? AND " +
                        PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ?))",
                new String[]{accountId, id, txnId, invId}, null, null, null)) {
            if (c.moveToFirst()) {
                String existingId = normalizeStr(c.getString(0));
                String existingTxnId = normalizeStr(c.getString(1));
                String existingInvId = normalizeStr(c.getString(2));
                int existingAllocated = c.getInt(3);
                long existingLastMod = resolveExistingTimestamp(c, 4, 5);

                boolean dataDiffers = !Objects.equals(existingId, id)
                        || !Objects.equals(existingTxnId, txnId)
                        || !Objects.equals(existingInvId, invId)
                        || existingAllocated != importedAllocated;

                if (dataDiffers || importedLastModified > existingLastMod) {
                    conflicts.add("PaymentAllocation conflict for ID " + id
                            + " (txn=" + txnId + ", invoice=" + invId + "): preserved existing allocation.");
                    return OUTCOME_CONFLICT;
                }
                return OUTCOME_SKIPPED;
            }
        }

        // Prevent over-allocating an existing transaction beyond its recorded amount_minor
        try (Cursor tc = db.query(TransactionEntry.TABLE_NAME,
                new String[]{TransactionEntry.COLUMN_TYPE, TransactionEntry.COLUMN_AMOUNT_MINOR},
                TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_ID + " = ?",
                new String[]{accountId, txnId}, null, null, null)) {
            if (tc.moveToFirst()) {
                String txnType = normalizeStr(tc.getString(0));
                int txnAmountMinor = tc.getInt(1);

                int currentAllocatedSum = 0;
                try (Cursor sc = db.rawQuery(
                        "SELECT COALESCE(SUM(" + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + "), 0) " +
                                "FROM " + PaymentAllocationEntry.TABLE_NAME +
                                " WHERE " + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ?",
                        new String[]{accountId, txnId})) {
                    if (sc.moveToFirst()) {
                        currentAllocatedSum = sc.getInt(0);
                    }
                }

                if (FinancialTransaction.TYPE_REFUND.equalsIgnoreCase(txnType)) {
                    if (Math.abs(currentAllocatedSum + importedAllocated) > txnAmountMinor) {
                        conflicts.add("PaymentAllocation over-allocation conflict for refund transaction " + txnId);
                        return OUTCOME_CONFLICT;
                    }
                } else {
                    if (currentAllocatedSum + importedAllocated > txnAmountMinor) {
                        conflicts.add("PaymentAllocation over-allocation conflict for payment transaction " + txnId);
                        return OUTCOME_CONFLICT;
                    }
                }
            }
        }

        db.insertOrThrow(PaymentAllocationEntry.TABLE_NAME, null, cv);
        return OUTCOME_INSERTED;
    }

    private static int insertOrSkipAuditLogRow(
            @NonNull SQLiteDatabase db,
            @NonNull ContentValues cv,
            @NonNull String accountId,
            @NonNull String id,
            long importedLastModified,
            @NonNull List<String> conflicts) {

        String importedEntityId = normalizeStr(cv.getAsString(AuditLogEntry.COLUMN_ENTITY_ID));
        String importedAction = normalizeStr(cv.getAsString(AuditLogEntry.COLUMN_ACTION));
        String importedDetails = normalizeStr(cv.getAsString(AuditLogEntry.COLUMN_DETAILS));

        try (Cursor c = db.query(AuditLogEntry.TABLE_NAME,
                new String[]{
                        AuditLogEntry.COLUMN_ENTITY_ID,
                        AuditLogEntry.COLUMN_ACTION,
                        AuditLogEntry.COLUMN_DETAILS,
                        AuditLogEntry.COLUMN_LAST_MODIFIED,
                        AuditLogEntry.COLUMN_UPDATED_AT
                },
                AuditLogEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AuditLogEntry.COLUMN_ID + " = ?",
                new String[]{accountId, id}, null, null, null)) {
            if (c.moveToFirst()) {
                String existingEntityId = normalizeStr(c.getString(0));
                String existingAction = normalizeStr(c.getString(1));
                String existingDetails = normalizeStr(c.getString(2));
                long existingLastMod = resolveExistingTimestamp(c, 3, 4);

                boolean dataDiffers = !Objects.equals(existingEntityId, importedEntityId)
                        || !Objects.equals(existingAction, importedAction)
                        || !Objects.equals(existingDetails, importedDetails);

                if (dataDiffers || importedLastModified > existingLastMod) {
                    conflicts.add("AuditLog conflict for ID " + id + ": preserved existing audit log.");
                    return OUTCOME_CONFLICT;
                }
                return OUTCOME_SKIPPED;
            }
        }

        db.insertOrThrow(AuditLogEntry.TABLE_NAME, null, cv);
        return OUTCOME_INSERTED;
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
     * Bridges genuine legacy rows from the `payments` table into `financial_transactions`
     * and `payment_allocations` (insert-only, without duplicating or overwriting) and
     * synchronizes invoice `paid_amount` and `status` strictly from actual financial allocations.
     * Never fabricates synthetic payments when an invoice has `paid_amount > 0` without allocations.
     */
    public static void reconcileAccountFinancialData(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        if (accountId.trim().isEmpty()) return;

        bridgeLegacyPaymentsForAccount(db, accountId);
        syncAccountInvoiceMirrorColumns(db, accountId);
        relinkBilledAttendanceRecords(db, accountId);
    }

    public static void bridgeLegacyPaymentsForAccount(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        if (accountId.trim().isEmpty()) return;

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
                String method = pc.getString(4) != null && !pc.getString(4).trim().isEmpty() ? pc.getString(4) : "Cash";
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

                boolean allocExists = false;
                try (Cursor ac = db.query(PaymentAllocationEntry.TABLE_NAME,
                        new String[]{PaymentAllocationEntry.COLUMN_ID},
                        PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ? AND " +
                                PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ?",
                        new String[]{accountId, payId, invId}, null, null, null)) {
                    if (ac.moveToFirst()) allocExists = true;
                }

                if (!allocExists) {
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
        }
    }

    public static void syncAccountInvoiceMirrorColumns(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        if (accountId.trim().isEmpty()) return;

        String invSyncSql = "SELECT i." + InvoiceEntry.COLUMN_ID + ", i." + InvoiceEntry.COLUMN_TOTAL_AMOUNT + ", " +
                "i." + InvoiceEntry.COLUMN_PAID_AMOUNT + ", i." + InvoiceEntry.COLUMN_STATUS + ", i." + InvoiceEntry.COLUMN_DUE_DATE + ", " +
                "(COALESCE((SELECT SUM(pa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + ") " +
                "           FROM " + PaymentAllocationEntry.TABLE_NAME + " pa " +
                "           INNER JOIN " + TransactionEntry.TABLE_NAME + " t " +
                "           ON pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = t." + TransactionEntry.COLUMN_ID + " " +
                "           AND pa." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = t." + TransactionEntry.COLUMN_ACCOUNT_ID + " " +
                "           WHERE pa." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " " +
                "           AND pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + " " +
                "           AND t." + TransactionEntry.COLUMN_IS_REVERSED + " = 0), 0) + " +
                " COALESCE((SELECT SUM(p." + PaymentEntry.COLUMN_AMOUNT + ") " +
                "           FROM " + PaymentEntry.TABLE_NAME + " p " +
                "           WHERE p." + PaymentEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " " +
                "           AND p." + PaymentEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + " " +
                "           AND NOT EXISTS (SELECT 1 FROM " + PaymentAllocationEntry.TABLE_NAME + " pa2 " +
                "                           WHERE pa2." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = p." + PaymentEntry.COLUMN_ACCOUNT_ID + " " +
                "                           AND (pa2." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = p." + PaymentEntry.COLUMN_ID + " " +
                "                                OR pa2." + PaymentAllocationEntry.COLUMN_ID + " = p." + PaymentEntry.COLUMN_ID + "))), 0)" +
                ") AS derived_paid " +
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
                int effectivePaid = Math.max(0, c.getInt(5));

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
    }

    private static void relinkBilledAttendanceRecords(@NonNull SQLiteDatabase db, @NonNull String accountId) {
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
            String nextDisp = generateNextStudentDisplayId(db, accountId);
            ContentValues cv = new ContentValues();
            cv.put(StudentEntry.COLUMN_STUDENT_ID_DISPLAY, nextDisp);
            db.update(StudentEntry.TABLE_NAME, cv,
                    StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, stuId});
        }
    }

    private static void ensureUniqueInvoiceDisplayIds(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        List<String> missingIds = new ArrayList<>();
        try (Cursor c = db.query(InvoiceEntry.TABLE_NAME,
                new String[]{InvoiceEntry.COLUMN_ID},
                InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                        InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " IS NULL OR " +
                        InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " = '')",
                new String[]{accountId}, null, null, InvoiceEntry.COLUMN_CREATED_AT + " ASC")) {
            while (c.moveToNext()) {
                missingIds.add(c.getString(0));
            }
        }

        for (String invId : missingIds) {
            String nextDisp = generateNextInvoiceDisplayId(db, accountId);
            ContentValues cv = new ContentValues();
            cv.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, nextDisp);
            db.update(InvoiceEntry.TABLE_NAME, cv,
                    InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, invId});
        }
    }

    @NonNull
    private static String generateNextStudentDisplayId(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        int nextNum = 1;
        try (Cursor c = db.rawQuery(
                "SELECT " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY +
                        " FROM " + StudentEntry.TABLE_NAME +
                        " WHERE " + StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " LIKE 'STU-%' " +
                        " ORDER BY CAST(SUBSTR(" + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + ", 5) AS INTEGER) DESC LIMIT 1",
                new String[]{accountId})) {
            if (c.moveToFirst() && c.getString(0) != null) {
                try {
                    nextNum = Integer.parseInt(c.getString(0).substring(4)) + 1;
                } catch (Exception ignored) {}
            }
        }
        return String.format(Locale.US, "STU-%04d", nextNum);
    }

    @NonNull
    private static String generateNextInvoiceDisplayId(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = String.format(Locale.US, "TS-%04d-", year);
        int nextNum = 1;
        try (Cursor c = db.rawQuery(
                "SELECT " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY +
                        " FROM " + InvoiceEntry.TABLE_NAME +
                        " WHERE " + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " LIKE ? " +
                        " ORDER BY CAST(SUBSTR(" + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + ", 9) AS INTEGER) DESC LIMIT 1",
                new String[]{accountId, prefix + "%"})) {
            if (c.moveToFirst() && c.getString(0) != null) {
                try {
                    nextNum = Integer.parseInt(c.getString(0).substring(8)) + 1;
                } catch (Exception ignored) {}
            }
        }
        return prefix + String.format(Locale.US, "%06d", nextNum);
    }

    @NonNull
    private static String generateNextTxnDisplayId(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = String.format(Locale.US, "TXN-%04d-", year);
        int nextNum = 1;
        try (Cursor c = db.rawQuery(
                "SELECT " + TransactionEntry.COLUMN_TXN_ID_DISPLAY +
                        " FROM " + TransactionEntry.TABLE_NAME +
                        " WHERE " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        TransactionEntry.COLUMN_TXN_ID_DISPLAY + " LIKE ? " +
                        " ORDER BY CAST(SUBSTR(" + TransactionEntry.COLUMN_TXN_ID_DISPLAY + ", 10) AS INTEGER) DESC LIMIT 1",
                new String[]{accountId, prefix + "%"})) {
            if (c.moveToFirst() && c.getString(0) != null) {
                try {
                    nextNum = Integer.parseInt(c.getString(0).substring(9)) + 1;
                } catch (Exception ignored) {}
            }
        }
        return prefix + String.format(Locale.US, "%06d", nextNum);
    }

    // =========================================================================
    // LOW-LEVEL CURSOR / JSON HELPERS
    // =========================================================================

    @NonNull
    private static Set<String> queryExistingIdsForAccount(
            @NonNull SQLiteDatabase db,
            @NonNull String table,
            @NonNull String accountId) {
        Set<String> ids = new HashSet<>();
        try (Cursor c = db.query(table, new String[]{"id"},
                "account_id = ?", new String[]{accountId}, null, null, null)) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                if (id != null && !id.trim().isEmpty()) {
                    ids.add(id.trim());
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
    private static ContentValues filterToValidColumns(
            @NonNull ContentValues raw,
            @NonNull Set<String> validColumns) {
        ContentValues out = new ContentValues();
        for (String key : raw.keySet()) {
            if (validColumns.contains(key)) {
                Object val = raw.get(key);
                if (val == null) out.putNull(key);
                else if (val instanceof Integer) out.put(key, (Integer) val);
                else if (val instanceof Long) out.put(key, (Long) val);
                else if (val instanceof Double) out.put(key, (Double) val);
                else if (val instanceof Float) out.put(key, (Float) val);
                else if (val instanceof Boolean) out.put(key, ((Boolean) val) ? 1 : 0);
                else out.put(key, String.valueOf(val));
            }
        }
        return out;
    }

    @NonNull
    private static JSONObject cursorRowToJson(@NonNull Cursor c) throws JSONException {
        JSONObject obj = new JSONObject();
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            String colName = c.getColumnName(i);
            int type = c.getType(i);
            if (type == Cursor.FIELD_TYPE_NULL) {
                obj.put(colName, JSONObject.NULL);
            } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                obj.put(colName, c.getLong(i));
            } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                obj.put(colName, c.getDouble(i));
            } else if (type == Cursor.FIELD_TYPE_STRING) {
                obj.put(colName, c.getString(i));
            }
        }
        return obj;
    }

    @NonNull
    private static ContentValues jsonToContentValues(@NonNull JSONObject obj) throws JSONException {
        ContentValues cv = new ContentValues();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (obj.isNull(key)) {
                cv.putNull(key);
            } else {
                Object val = obj.get(key);
                if (val instanceof Integer) {
                    cv.put(key, (Integer) val);
                } else if (val instanceof Long) {
                    cv.put(key, (Long) val);
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

    private static long extractRecordLastModified(@NonNull String table, @NonNull ContentValues cv) {
        long ts = getLongOrDefault(cv, "last_modified", 0L);
        if (ts > 0) return ts;
        ts = getLongOrDefault(cv, "updated_at", 0L);
        if (ts > 0) return ts;
        ts = getLongOrDefault(cv, "created_at", 0L);
        if (ts > 0) return ts;

        if (OccurrenceEntry.TABLE_NAME.equals(table)) {
            ts = getLongOrDefault(cv, OccurrenceEntry.COLUMN_DATE, 0L);
        } else if (PaymentEntry.TABLE_NAME.equals(table)) {
            ts = getLongOrDefault(cv, PaymentEntry.COLUMN_PAYMENT_DATE, 0L);
        } else if (TransactionEntry.TABLE_NAME.equals(table)) {
            ts = getLongOrDefault(cv, TransactionEntry.COLUMN_TIMESTAMP, 0L);
        } else if (AuditLogEntry.TABLE_NAME.equals(table)) {
            ts = getLongOrDefault(cv, AuditLogEntry.COLUMN_TIMESTAMP, 0L);
        } else if (ScheduleFeeHistoryEntry.TABLE_NAME.equals(table)) {
            ts = getLongOrDefault(cv, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM, 0L);
        } else if (ScheduleEntry.TABLE_NAME.equals(table)) {
            ts = getLongOrDefault(cv, ScheduleEntry.COLUMN_START_DATE, 0L);
        } else if (InvoiceEntry.TABLE_NAME.equals(table)) {
            ts = getLongOrDefault(cv, InvoiceEntry.COLUMN_ISSUE_DATE, 0L);
        }
        return ts > 0 ? ts : 1L;
    }

    private static long resolveExistingTimestamp(@NonNull Cursor c, int lastModIdx, int updatedAtIdx) {
        long lastMod = (lastModIdx >= 0 && !c.isNull(lastModIdx)) ? c.getLong(lastModIdx) : 0L;
        if (lastMod > 0L) return lastMod;
        return (updatedAtIdx >= 0 && !c.isNull(updatedAtIdx)) ? c.getLong(updatedAtIdx) : 0L;
    }

    private static long getLongOrDefault(@NonNull ContentValues cv, @NonNull String key, long defaultVal) {
        Long val = cv.getAsLong(key);
        return val != null ? val : defaultVal;
    }

    private static int getIntOrDefault(@NonNull ContentValues cv, @NonNull String key, int defaultVal) {
        Integer val = cv.getAsInteger(key);
        return val != null ? val : defaultVal;
    }

    @NonNull
    private static String normalizeStr(@Nullable String s) {
        return s != null ? s.trim() : "";
    }

    @NonNull
    private static String getColumnStringOrEmpty(@NonNull Cursor c, @NonNull String col) {
        int idx = c.getColumnIndex(col);
        if (idx == -1 || c.isNull(idx)) return "";
        String v = c.getString(idx);
        return v != null ? v : "";
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
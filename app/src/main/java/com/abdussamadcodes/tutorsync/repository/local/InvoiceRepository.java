package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.InvoiceItem;
import com.abdussamadcodes.tutorsync.util.BackupRestoreManager;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class InvoiceRepository {

    private static final String SQL_SELECT_INVOICE_WITH_DERIVED_PAID =
            "SELECT i.*, " +
                    "COALESCE((SELECT SUM(pa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + ") " +
                    "          FROM " + PaymentAllocationEntry.TABLE_NAME + " pa " +
                    "          INNER JOIN " + TransactionEntry.TABLE_NAME + " t ON pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = t." + TransactionEntry.COLUMN_ID + " " +
                    "          WHERE pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + " " +
                    "          AND t." + TransactionEntry.COLUMN_IS_REVERSED + " = 0), 0) AS derived_paid_minor " +
                    "FROM " + InvoiceEntry.TABLE_NAME + " i ";

    public String generateNextInvoiceId(@NonNull String accountId, @NonNull SQLiteDatabase db) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = String.format(Locale.US, "TS-%04d-", year);
        int nextNumber = 1;

        try (Cursor cursor = db.rawQuery(
                "SELECT " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY +
                        " FROM " + InvoiceEntry.TABLE_NAME +
                        " WHERE " + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? " +
                        " AND " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " LIKE ?" +
                        " ORDER BY CAST(SUBSTR(" + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + ", 9) AS INTEGER) DESC LIMIT 1",
                new String[]{accountId, prefix + "%"})) {
            if (cursor.moveToFirst()) {
                String lastId = cursor.getString(0);
                try {
                    nextNumber = Integer.parseInt(lastId.substring(8)) + 1;
                } catch (Exception ignored) {}
            }
        }
        return prefix + String.format(Locale.US, "%06d", nextNumber);
    }

    public void migrateLegacyInvoiceIds(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            String selection = InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                    InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " IS NULL OR " +
                    InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " = '' OR " +
                    InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " LIKE '#INV-%')";

            List<String> needsMigration = new ArrayList<>();
            try (Cursor cursor = db.query(InvoiceEntry.TABLE_NAME, new String[]{InvoiceEntry.COLUMN_ID},
                    selection, new String[]{accountId}, null, null, InvoiceEntry.COLUMN_CREATED_AT + " ASC")) {
                while (cursor.moveToNext()) {
                    needsMigration.add(cursor.getString(0));
                }
            }

            for (String internalId : needsMigration) {
                String newDisplayId = generateNextInvoiceId(accountId, db);
                ContentValues values = new ContentValues();
                values.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, newDisplayId);
                db.update(InvoiceEntry.TABLE_NAME, values, InvoiceEntry.COLUMN_ID + " = ?", new String[]{internalId});
            }

            // Reconcile any imported invoices whose paid_amount / status were restored without allocations
            BackupRestoreManager.reconcileAccountFinancialData(db, accountId);
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public static long[] computeAnniversaryBillingWindow(int anchorDay, @NonNull YearMonth targetMonth) {
        int clampedStartDay = Math.min(Math.max(1, anchorDay), targetMonth.lengthOfMonth());
        LocalDate startDate = targetMonth.atDay(clampedStartDay);

        YearMonth nextMonth = targetMonth.plusMonths(1);
        int clampedNextAnchor = Math.min(Math.max(1, anchorDay), nextMonth.lengthOfMonth());
        LocalDate endDate = nextMonth.atDay(clampedNextAnchor).minusDays(1);

        ZoneId zone = ZoneId.systemDefault();
        long startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMillis = endDate.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli();
        return new long[]{startMillis, endMillis};
    }

    public int generateDueInvoicesForAccount(@NonNull String accountId) {
        return generateDueInvoicesInternal(accountId, null);
    }

    public int generateDueInvoicesForStudent(@NonNull String accountId, @NonNull String studentId) {
        return generateDueInvoicesInternal(accountId, studentId);
    }

    private int generateDueInvoicesInternal(@NonNull String accountId, @Nullable String onlyStudentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        int generatedInvoicesCount = 0;
        long now = System.currentTimeMillis();
        YearMonth currentYm = YearMonth.now();
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        db.beginTransaction();
        try {
            AttendanceRepository.healPerClassAttendanceRecords(db, accountId, onlyStudentId);

            List<String> studentIds = new ArrayList<>();
            if (onlyStudentId != null) {
                studentIds.add(onlyStudentId);
            } else {
                try (Cursor sc = db.query(StudentEntry.TABLE_NAME, new String[]{StudentEntry.COLUMN_ID},
                        StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + StudentEntry.COLUMN_IS_ACTIVE + " = 1 AND " + StudentEntry.COLUMN_IS_DELETED + " = 0",
                        new String[]{accountId}, null, null, null)) {
                    while (sc.moveToNext()) {
                        studentIds.add(sc.getString(0));
                    }
                }
            }

            for (String studentId : studentIds) {
                List<InvoiceItem> pendingItems = new ArrayList<>();
                List<String> attendanceIdsToLock = new ArrayList<>();
                String invoiceId = UUID.randomUUID().toString();
                long minPeriodStart = Long.MAX_VALUE;
                long maxPeriodEnd = 0L;

                // 1. Evaluate MONTHLY schedule groups for this student
                try (Cursor gc = db.rawQuery(
                        "SELECT " + ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + ", " +
                                ScheduleEntry.COLUMN_BILLING_MODEL + ", " +
                                ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR + ", " +
                                ScheduleEntry.COLUMN_ANCHOR_DAY + ", " +
                                ScheduleEntry.COLUMN_START_TIME_MINUTES + " " +
                                "FROM " + ScheduleEntry.TABLE_NAME +
                                " WHERE " + ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " +
                                ScheduleEntry.COLUMN_IS_ACTIVE + " = 1 " +
                                "GROUP BY COALESCE(" + ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + ", " + ScheduleEntry.COLUMN_START_TIME_MINUTES + ")",
                        new String[]{accountId, studentId})) {

                    while (gc.moveToNext()) {
                        String groupId = gc.getString(0);
                        String model = gc.getString(1);
                        int feeMinor = gc.getInt(2);
                        int anchorDay = gc.getInt(3);
                        int startMin = gc.getInt(4);

                        if (feeMinor <= 0) {
                            String[] fallback = AttendanceRepository.resolveBillingForOccurrence(db, studentId, null, groupId, startMin, now);
                            model = fallback[0];
                            feeMinor = Integer.parseInt(fallback[1]);
                        }
                        if (feeMinor <= 0) continue;
                        if (groupId == null) groupId = "GRP-" + studentId + "-" + startMin;

                        if ("MONTHLY".equals(model)) {
                            long[] window = computeAnniversaryBillingWindow(anchorDay, currentYm);
                            long pStart = window[0];
                            long pEnd = window[1];

                            boolean alreadyBilled = false;
                            try (Cursor bc = db.rawQuery(
                                    "SELECT ii." + InvoiceItemEntry.COLUMN_ID +
                                            " FROM " + InvoiceItemEntry.TABLE_NAME + " ii " +
                                            " INNER JOIN " + InvoiceEntry.TABLE_NAME + " i ON ii." + InvoiceItemEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID +
                                            " WHERE ii." + InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? " +
                                            " AND i." + InvoiceEntry.COLUMN_IS_VOID + " = 0 " +
                                            " AND i." + InvoiceEntry.COLUMN_PERIOD_START + " = ?",
                                    new String[]{groupId, String.valueOf(pStart)})) {
                                if (bc.moveToFirst()) alreadyBilled = true;
                            }

                            if (!alreadyBilled) {
                                minPeriodStart = Math.min(minPeriodStart, pStart);
                                maxPeriodEnd = Math.max(maxPeriodEnd, pEnd);
                                int h = (startMin / 60) % 12;
                                if (h == 0) h = 12;
                                String ampm = (startMin >= 720) ? "PM" : "AM";
                                String desc = String.format(Locale.US, "Monthly Schedule (%02d:%02d %s) · %s – %s",
                                        h, startMin % 60, ampm, dateFmt.format(new Date(pStart)), dateFmt.format(new Date(pEnd)));

                                pendingItems.add(new InvoiceItem(
                                        UUID.randomUUID().toString(), accountId, invoiceId,
                                        groupId, null, desc, "MONTHLY", 1, feeMinor, feeMinor, now
                                ));
                            }
                        }
                    }
                }

                // 2. Collect unbilled chargeable PER_CLASS occurrences
                Set<String> billedSessionsInRun = new HashSet<>();
                try (Cursor ac = db.rawQuery(
                        "SELECT a." + AttendanceEntry.COLUMN_ID + ", o." + OccurrenceEntry.COLUMN_ID + ", " +
                                "o." + OccurrenceEntry.COLUMN_SCHEDULE_ID + ", o." + OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID + ", " +
                                "o." + OccurrenceEntry.COLUMN_DATE + ", o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + ", " +
                                "a." + AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + ", a." + AttendanceEntry.COLUMN_STATUS + " " +
                                "FROM " + AttendanceEntry.TABLE_NAME + " a " +
                                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                                "WHERE o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? " +
                                "AND o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = ? " +
                                "AND a." + AttendanceEntry.COLUMN_CHARGE_DECISION + " = ? " +
                                "AND a." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " IS NULL " +
                                "ORDER BY o." + OccurrenceEntry.COLUMN_DATE + " ASC, o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + " ASC",
                        new String[]{accountId, studentId, AttendanceRecord.DECISION_CHARGE})) {

                    while (ac.moveToNext()) {
                        String attId = ac.getString(0);
                        String occId = ac.getString(1);
                        String schedId = ac.getString(2);
                        String groupId = ac.getString(3);
                        long occDate = ac.getLong(4);
                        int startMin = ac.getInt(5);
                        int feeMinor = ac.getInt(6);
                        String status = ac.getString(7);

                        if (feeMinor <= 0) {
                            String[] resolved = AttendanceRepository.resolveBillingForOccurrence(db, studentId, schedId, groupId, startMin, occDate);
                            feeMinor = Integer.parseInt(resolved[1]);
                        }
                        if (feeMinor <= 0) continue;

                        String sessionKey = occDate + ":" + startMin;
                        if (billedSessionsInRun.contains(sessionKey)) {
                            if (!pendingItems.isEmpty()) {
                                String lastItemId = pendingItems.get(pendingItems.size() - 1).getId();
                                attendanceIdsToLock.add(attId + ":" + lastItemId);
                            }
                            continue;
                        }
                        billedSessionsInRun.add(sessionKey);

                        minPeriodStart = Math.min(minPeriodStart, occDate);
                        maxPeriodEnd = Math.max(maxPeriodEnd, occDate);

                        String itemId = UUID.randomUUID().toString();
                        int h = (startMin / 60) % 12;
                        if (h == 0) h = 12;
                        String ampm = (startMin >= 720) ? "PM" : "AM";
                        String desc = String.format(Locale.US, "Class on %s at %02d:%02d %s (%s)",
                                dateFmt.format(new Date(occDate)), h, startMin % 60, ampm, status);

                        pendingItems.add(new InvoiceItem(
                                itemId, accountId, invoiceId, groupId, occId, desc,
                                "PER_CLASS", 1, feeMinor, feeMinor, now
                        ));
                        attendanceIdsToLock.add(attId + ":" + itemId);
                    }
                }

                if (pendingItems.isEmpty()) continue;

                int subtotalMinor = 0;
                for (InvoiceItem item : pendingItems) {
                    subtotalMinor += item.getAmountMinor();
                }

                String displayId = generateNextInvoiceId(accountId, db);
                String periodLabel = (minPeriodStart == maxPeriodEnd)
                        ? dateFmt.format(new Date(minPeriodStart))
                        : dateFmt.format(new Date(minPeriodStart)) + " – " + dateFmt.format(new Date(maxPeriodEnd));
                long dueDate = now + (7L * 24L * 60L * 60L * 1000L);

                ContentValues iv = new ContentValues();
                iv.put(InvoiceEntry.COLUMN_ID, invoiceId);
                iv.put(InvoiceEntry.COLUMN_ACCOUNT_ID, accountId);
                iv.put(InvoiceEntry.COLUMN_STUDENT_ID, studentId);
                iv.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, displayId);
                iv.put(InvoiceEntry.COLUMN_BILLING_PERIOD, periodLabel);
                iv.put(InvoiceEntry.COLUMN_PERIOD_START, minPeriodStart);
                iv.put(InvoiceEntry.COLUMN_PERIOD_END, maxPeriodEnd);
                iv.put(InvoiceEntry.COLUMN_ISSUE_DATE, now);
                iv.put(InvoiceEntry.COLUMN_DUE_DATE, dueDate);
                iv.put(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, subtotalMinor);
                iv.put(InvoiceEntry.COLUMN_DISCOUNT_MINOR, 0);
                iv.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, subtotalMinor);
                iv.put(InvoiceEntry.COLUMN_PAID_AMOUNT, 0);
                iv.put(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_UNPAID);
                iv.put(InvoiceEntry.COLUMN_IS_VOID, 0);
                iv.put(InvoiceEntry.COLUMN_CREATED_AT, now);
                iv.put(InvoiceEntry.COLUMN_UPDATED_AT, now);
                db.insertOrThrow(InvoiceEntry.TABLE_NAME, null, iv);

                for (InvoiceItem item : pendingItems) {
                    ContentValues iiv = new ContentValues();
                    iiv.put(InvoiceItemEntry.COLUMN_ID, item.getId());
                    iiv.put(InvoiceItemEntry.COLUMN_ACCOUNT_ID, accountId);
                    iiv.put(InvoiceItemEntry.COLUMN_INVOICE_ID, invoiceId);
                    iiv.put(InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID, item.getScheduleGroupId());
                    iiv.put(InvoiceItemEntry.COLUMN_OCCURRENCE_ID, item.getOccurrenceId());
                    iiv.put(InvoiceItemEntry.COLUMN_DESCRIPTION, item.getDescription());
                    iiv.put(InvoiceItemEntry.COLUMN_BILLING_MODEL, item.getBillingModel());
                    iiv.put(InvoiceItemEntry.COLUMN_QUANTITY, item.getQuantity());
                    iiv.put(InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR, item.getUnitPriceMinor());
                    iiv.put(InvoiceItemEntry.COLUMN_AMOUNT_MINOR, item.getAmountMinor());
                    iiv.put(InvoiceItemEntry.COLUMN_CREATED_AT, now);
                    db.insertOrThrow(InvoiceItemEntry.TABLE_NAME, null, iiv);
                }

                for (String pair : attendanceIdsToLock) {
                    String[] parts = pair.split(":");
                    ContentValues lockVal = new ContentValues();
                    lockVal.put(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID, parts[1]);
                    db.update(AttendanceEntry.TABLE_NAME, lockVal, AttendanceEntry.COLUMN_ID + " = ?", new String[]{parts[0]});
                }

                logAudit(db, accountId, studentId, "INVOICE", invoiceId, "CREATED",
                        "Generated invoice " + displayId + " for ৳" + String.format(Locale.US, "%.2f", subtotalMinor / 100.0));
                generatedInvoicesCount++;
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
        return generatedInvoicesCount;
    }

    @NonNull
    public List<Invoice> getInvoicesForAccount(@NonNull String accountId) {
        return getFilteredInvoicesForAccount(accountId, "ALL");
    }

    @NonNull
    public List<Invoice> getFilteredInvoicesForAccount(@NonNull String accountId, @NonNull String filterStatus) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Invoice> result = new ArrayList<>();
        String sql = SQL_SELECT_INVOICE_WITH_DERIVED_PAID +
                "WHERE i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND i." + InvoiceEntry.COLUMN_IS_VOID + " = 0 " +
                "ORDER BY i." + InvoiceEntry.COLUMN_CREATED_AT + " DESC";

        try (Cursor cursor = db.rawQuery(sql, new String[]{accountId})) {
            while (cursor.moveToNext()) {
                Invoice inv = mapCursorToInvoice(cursor);
                if ("ALL".equalsIgnoreCase(filterStatus)) {
                    result.add(inv);
                } else if ("OVERDUE".equalsIgnoreCase(filterStatus) && inv.isOverdue()) {
                    result.add(inv);
                } else if (filterStatus.equalsIgnoreCase(inv.getStatus())) {
                    result.add(inv);
                }
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return result;
    }

    @Nullable
    public Invoice getInvoiceById(@NonNull String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String sql = SQL_SELECT_INVOICE_WITH_DERIVED_PAID + "WHERE i." + InvoiceEntry.COLUMN_ID + " = ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{invoiceId})) {
            if (cursor.moveToFirst()) {
                return mapCursorToInvoice(cursor);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    @NonNull
    public List<InvoiceItem> getItemsForInvoice(@NonNull String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<InvoiceItem> items = new ArrayList<>();
        try (Cursor c = db.query(InvoiceItemEntry.TABLE_NAME, null,
                InvoiceItemEntry.COLUMN_INVOICE_ID + " = ?", new String[]{invoiceId}, null, null, InvoiceItemEntry.COLUMN_CREATED_AT + " ASC")) {
            while (c.moveToNext()) {
                items.add(new InvoiceItem(
                        c.getString(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_ID)),
                        c.getString(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_ACCOUNT_ID)),
                        c.getString(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_INVOICE_ID)),
                        c.getString(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID)),
                        c.getString(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_OCCURRENCE_ID)),
                        c.getString(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_DESCRIPTION)),
                        c.getString(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_BILLING_MODEL)),
                        c.getInt(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_QUANTITY)),
                        c.getInt(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR)),
                        c.getInt(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_AMOUNT_MINOR)),
                        c.getLong(c.getColumnIndexOrThrow(InvoiceItemEntry.COLUMN_CREATED_AT))
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return items;
    }

    public int getTotalRevenueForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        int netRevenueMinor = 0;
        try {
            BackupRestoreManager.reconcileAccountFinancialData(db, accountId);

            String sql = "SELECT COALESCE(SUM(CASE WHEN " + TransactionEntry.COLUMN_TYPE + " = 'PAYMENT' THEN " +
                    TransactionEntry.COLUMN_AMOUNT_MINOR + " ELSE -" + TransactionEntry.COLUMN_AMOUNT_MINOR + " END), 0) " +
                    "FROM " + TransactionEntry.TABLE_NAME +
                    " WHERE " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_IS_REVERSED + " = 0";
            try (Cursor c = db.rawQuery(sql, new String[]{accountId})) {
                if (c.moveToFirst()) netRevenueMinor = c.getInt(0);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return Math.max(0, netRevenueMinor);
    }

    private Invoice mapCursorToInvoice(Cursor cursor) {
        int derivedPaid = cursor.getInt(cursor.getColumnIndexOrThrow("derived_paid_minor"));
        int storedPaid = cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_PAID_AMOUNT));
        int effectivePaid = Math.max(derivedPaid, storedPaid);

        int totalAmount = cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_TOTAL_AMOUNT));
        int subtotal = cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_SUBTOTAL_MINOR));
        int discount = cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_DISCOUNT_MINOR));
        long created = cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_CREATED_AT));
        long issueDate = cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ISSUE_DATE));

        return new Invoice(
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ACCOUNT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_STUDENT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY)),
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_BILLING_PERIOD)),
                cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_PERIOD_START)),
                cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_PERIOD_END)),
                issueDate > 0 ? issueDate : created,
                cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_DUE_DATE)),
                subtotal > 0 ? subtotal : totalAmount,
                discount,
                totalAmount,
                effectivePaid,
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_NOTES)),
                created
        );
    }

    public static void logAudit(SQLiteDatabase db, String accountId, String studentId,
                                String entityType, String entityId, String action, String details) {
        ContentValues av = new ContentValues();
        av.put(AuditLogEntry.COLUMN_ID, UUID.randomUUID().toString());
        av.put(AuditLogEntry.COLUMN_ACCOUNT_ID, accountId);
        av.put(AuditLogEntry.COLUMN_STUDENT_ID, studentId);
        av.put(AuditLogEntry.COLUMN_ENTITY_TYPE, entityType);
        av.put(AuditLogEntry.COLUMN_ENTITY_ID, entityId);
        av.put(AuditLogEntry.COLUMN_ACTION, action);
        av.put(AuditLogEntry.COLUMN_DETAILS, details);
        av.put(AuditLogEntry.COLUMN_TIMESTAMP, System.currentTimeMillis());
        db.insert(AuditLogEntry.TABLE_NAME, null, av);
    }
}
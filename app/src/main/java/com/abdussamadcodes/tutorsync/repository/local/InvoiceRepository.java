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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InvoiceRepository {

    private static final String SQL_SELECT_INVOICE_WITH_DERIVED_PAID =
            "SELECT i.*, " +
                    "(COALESCE((SELECT SUM(pa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + ") " +
                    "           FROM " + PaymentAllocationEntry.TABLE_NAME + " pa " +
                    "           INNER JOIN " + TransactionEntry.TABLE_NAME + " t " +
                    "           ON pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = t." + TransactionEntry.COLUMN_ID + " " +
                    "           AND pa." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = t." + TransactionEntry.COLUMN_ACCOUNT_ID + " " +
                    "           WHERE pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + " " +
                    "           AND pa." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " " +
                    "           AND t." + TransactionEntry.COLUMN_IS_REVERSED + " = 0), 0) + " +
                    " COALESCE((SELECT SUM(p." + PaymentEntry.COLUMN_AMOUNT + ") " +
                    "           FROM " + PaymentEntry.TABLE_NAME + " p " +
                    "           WHERE p." + PaymentEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID + " " +
                    "           AND p." + PaymentEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " " +
                    "           AND NOT EXISTS (SELECT 1 FROM " + PaymentAllocationEntry.TABLE_NAME + " pa2 " +
                    "                           WHERE pa2." + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = p." + PaymentEntry.COLUMN_ACCOUNT_ID + " " +
                    "                           AND (pa2." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = p." + PaymentEntry.COLUMN_ID + " " +
                    "                                OR pa2." + PaymentAllocationEntry.COLUMN_ID + " = p." + PaymentEntry.COLUMN_ID + "))), 0)" +
                    ") AS derived_paid_minor " +
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

            long now = System.currentTimeMillis();
            for (String internalId : needsMigration) {
                String newDisplayId = generateNextInvoiceId(accountId, db);
                ContentValues values = new ContentValues();
                values.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, newDisplayId);
                values.put(InvoiceEntry.COLUMN_UPDATED_AT, now);
                values.put(InvoiceEntry.COLUMN_LAST_MODIFIED, now);
                db.update(InvoiceEntry.TABLE_NAME, values,
                        InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, internalId});
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public void reconcileAccountFinancialData(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            BackupRestoreManager.reconcileAccountFinancialData(db, accountId);
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public static void reconcileAccountFinancialData(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        BackupRestoreManager.reconcileAccountFinancialData(db, accountId);
    }

    // ------------------------------------------------------------------------------------
    // M2: monthly billing windows
    // ------------------------------------------------------------------------------------

    /** Billing windows are at least ~28 days apart, so a 3-day tolerance can never mix two windows. */
    private static final long MONTHLY_WINDOW_TOLERANCE_MS = 3L * 24L * 60L * 60L * 1000L;
    private static final long HALF_DAY_MS = 12L * 60L * 60L * 1000L;
    /** Safety limit: at most this many (most recent) missed windows are billed in one run. */
    private static final int MAX_MONTHLY_BACKFILL_WINDOWS = 24;

    /**
     * All monthly billing windows of a schedule group that are due now, oldest first.
     *
     * - starts with the window that contains the schedule's start date,
     * - includes every window that has already STARTED (never a future one),
     * - stops after the schedule's end date,
     * - when the schedule start is unknown only the window containing today is returned.
     */
    @NonNull
    static List<long[]> computeDueMonthlyWindows(int anchorDay, long groupStartMs, long groupEndMs, long nowMs) {
        ZoneId zone = ZoneId.systemDefault();
        int anchor = Math.max(1, anchorDay);

        YearMonth ym;
        if (groupStartMs > 0L) {
            // +12h normalises a start date that was stored in another timezone
            LocalDate d = java.time.Instant.ofEpochMilli(groupStartMs + HALF_DAY_MS).atZone(zone).toLocalDate();
            ym = YearMonth.from(d);
            if (d.getDayOfMonth() < Math.min(anchor, ym.lengthOfMonth())) {
                ym = ym.minusMonths(1);
            }
        } else {
            ym = YearMonth.from(java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate());
            if (computeAnniversaryBillingWindow(anchor, ym)[0] > nowMs) {
                ym = ym.minusMonths(1);
            }
        }

        List<long[]> windows = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            long[] w = computeAnniversaryBillingWindow(anchor, ym);
            if (w[0] > nowMs) break; // a window that has not started yet is never billed
            if (groupEndMs > 0L && w[0] > groupEndMs + MONTHLY_WINDOW_TOLERANCE_MS) break; // schedule already ended
            windows.add(w);
            ym = ym.plusMonths(1);
        }
        if (windows.size() > MAX_MONTHLY_BACKFILL_WINDOWS) {
            windows = new ArrayList<>(windows.subList(windows.size() - MAX_MONTHLY_BACKFILL_WINDOWS, windows.size()));
        }
        return windows;
    }

    /**
     * True when a non-void MONTHLY item of this student/group already pays for this window.
     * Stored window starts are compared with a tolerance so a timezone change cannot cause a
     * second invoice for the same month. Legacy items (no recorded window) count when they were
     * created during the window's calendar month.
     */
    private static boolean isMonthlyWindowAlreadyBilled(@NonNull SQLiteDatabase db, @NonNull String accountId,
                                                        @NonNull String studentId, @NonNull String groupId,
                                                        long windowStartMs) {
        ZoneId zone = ZoneId.systemDefault();
        YearMonth wm = YearMonth.from(java.time.Instant.ofEpochMilli(windowStartMs + HALF_DAY_MS).atZone(zone).toLocalDate());
        long monthStart = wm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long nextMonthStart = wm.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long lo = windowStartMs - MONTHLY_WINDOW_TOLERANCE_MS;
        long hi = windowStartMs + MONTHLY_WINDOW_TOLERANCE_MS;

        try (Cursor bc = db.rawQuery(
                "SELECT 1 FROM " + InvoiceItemEntry.TABLE_NAME + " ii " +
                        " INNER JOIN " + InvoiceEntry.TABLE_NAME + " i " +
                        " ON ii." + InvoiceItemEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID +
                        " AND ii." + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = i." + InvoiceEntry.COLUMN_ACCOUNT_ID +
                        " WHERE ii." + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = ? " +
                        " AND i." + InvoiceEntry.COLUMN_STUDENT_ID + " = ? " +
                        " AND ii." + InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? " +
                        " AND ii." + InvoiceItemEntry.COLUMN_BILLING_MODEL + " = 'MONTHLY' " +
                        " AND i." + InvoiceEntry.COLUMN_IS_VOID + " = 0 " +
                        " AND ((ii." + InvoiceItemEntry.COLUMN_PERIOD_START + " >= ? AND ii." + InvoiceItemEntry.COLUMN_PERIOD_START + " <= ?) " +
                        "      OR (ii." + InvoiceItemEntry.COLUMN_PERIOD_START + " = 0 " +
                        "          AND ((i." + InvoiceEntry.COLUMN_PERIOD_START + " >= ? AND i." + InvoiceEntry.COLUMN_PERIOD_START + " <= ?) " +
                        "               OR (ii." + InvoiceItemEntry.COLUMN_CREATED_AT + " >= ? " +
                        "                   AND ii." + InvoiceItemEntry.COLUMN_CREATED_AT + " < ?)))) " +
                        " LIMIT 1",
                new String[]{accountId, studentId, groupId,
                        String.valueOf(lo), String.valueOf(hi),
                        String.valueOf(lo), String.valueOf(hi),
                        String.valueOf(monthStart), String.valueOf(nextMonthStart)})) {
            return bc.moveToFirst();
        }
    }

    /**
     * Billing model and fee that applied to this group when the window STARTED:
     * the fee-history row covering that date; for windows older than the first history row the
     * earliest known row (never today's fee); otherwise the group's current model and fee.
     */
    @NonNull
    private static String[] resolveMonthlyWindowBilling(@NonNull SQLiteDatabase db, @NonNull String accountId,
                                                        @NonNull String groupId, long windowStartMs,
                                                        @Nullable String currentModel, int currentFeeMinor) {
        long endOfStartDay = windowStartMs + 86_399_999L;

        try (Cursor hc = db.query(ScheduleFeeHistoryEntry.TABLE_NAME,
                new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR},
                ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? AND " +
                        ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " <= ? AND (" +
                        ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " IS NULL OR " +
                        ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " >= ?)",
                new String[]{accountId, groupId, String.valueOf(endOfStartDay), String.valueOf(windowStartMs)},
                null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " DESC", "1")) {
            if (hc.moveToFirst() && hc.getInt(1) > 0) {
                return new String[]{hc.getString(0), String.valueOf(hc.getInt(1))};
            }
        }

        try (Cursor ec = db.query(ScheduleFeeHistoryEntry.TABLE_NAME,
                new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR,
                        ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM},
                ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ?",
                new String[]{accountId, groupId},
                null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " ASC", "1")) {
            if (ec.moveToFirst() && ec.getLong(2) > windowStartMs && ec.getInt(1) > 0) {
                return new String[]{ec.getString(0), String.valueOf(ec.getInt(1))};
            }
        }

        return new String[]{currentModel == null ? "MONTHLY" : currentModel, String.valueOf(Math.max(0, currentFeeMinor))};
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
        return generateDueInvoicesInternal(accountId, null, true);
    }

    public int generateDueInvoicesForStudent(@NonNull String accountId, @NonNull String studentId) {
        return generateDueInvoicesInternal(accountId, studentId, true);
    }

    /**
     * Invoices ONLY the outstanding per-class charges of one student (no monthly fee is billed).
     * Used before a student is deleted so unbilled classes are never lost.
     *
     * @return true if everything was billed (or nothing needed billing); false if billing failed
     *         and was rolled back.
     */
    public boolean billOutstandingPerClassChargesForStudent(@NonNull String accountId, @NonNull String studentId) {
        try {
            generateDueInvoicesInternal(accountId, studentId, false);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private int generateDueInvoicesInternal(@NonNull String accountId, @Nullable String onlyStudentId,
                                            boolean includeMonthly) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        int generatedInvoicesCount = 0;
        long now = System.currentTimeMillis();
        YearMonth currentYm = YearMonth.now();
        ZoneId zone = ZoneId.systemDefault();
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        db.beginTransaction();
        try {
            AttendanceRepository.healPerClassAttendanceRecords(db, accountId, onlyStudentId);

            List<String> studentIds = new ArrayList<>();
            if (onlyStudentId != null) {
                studentIds.add(onlyStudentId);
            } else {
                try (Cursor sc = db.query(StudentEntry.TABLE_NAME, new String[]{StudentEntry.COLUMN_ID},
                        StudentEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                StudentEntry.COLUMN_IS_ACTIVE + " = 1 AND " +
                                StudentEntry.COLUMN_IS_DELETED + " = 0",
                        new String[]{accountId}, null, null, null)) {
                    while (sc.moveToNext()) {
                        studentIds.add(sc.getString(0));
                    }
                }
            }

            for (String studentId : studentIds) {
                List<InvoiceItem> pendingItems = new ArrayList<>();
                List<String> attendanceIdsToLock = new ArrayList<>();
                // MONTHLY item id -> start of the billing window that item pays for
                Map<String, Long> monthlyPeriodStartByItemId = new HashMap<>();
                String invoiceId = UUID.randomUUID().toString();
                long minPeriodStart = Long.MAX_VALUE;
                long maxPeriodEnd = 0L;
                long monthlyFirstWindowStart = Long.MAX_VALUE;
                long monthlyLastWindowStart = 0L;

                // 1. Evaluate MONTHLY schedule groups for this student
                try (Cursor gc = db.rawQuery(
                        "SELECT " + ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + ", " +
                                ScheduleEntry.COLUMN_BILLING_MODEL + ", " +
                                ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR + ", " +
                                ScheduleEntry.COLUMN_ANCHOR_DAY + ", " +
                                ScheduleEntry.COLUMN_START_TIME_MINUTES + ", " +
                                "MIN(" + ScheduleEntry.COLUMN_START_DATE + "), " +
                                "MAX(" + ScheduleEntry.COLUMN_END_DATE + ") " +
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
                        long groupStart = gc.getLong(5);
                        long groupEnd = gc.getLong(6);

                        if (feeMinor <= 0) {
                            String[] fallback = AttendanceRepository.resolveBillingForOccurrence(db, accountId, studentId, null, groupId, startMin, now);
                            model = fallback[0];
                            feeMinor = Integer.parseInt(fallback[1]);
                        }
                        if (groupId == null) groupId = "GRP-" + studentId + "-" + startMin;
                        if (!includeMonthly) continue;

                        // M2: every window that has already started and is not billed yet (oldest first),
                        // inside the schedule's start/end dates; never a window that starts in the future.
                        for (long[] window : computeDueMonthlyWindows(anchorDay, groupStart, groupEnd, now)) {
                            long pStart = window[0];
                            long pEnd = window[1];

                            if (isMonthlyWindowAlreadyBilled(db, accountId, studentId, groupId, pStart)) continue;

                            // The fee/model that applied when this window started (not necessarily today's).
                            String[] windowBilling = resolveMonthlyWindowBilling(db, accountId, groupId, pStart, model, feeMinor);
                            if (!"MONTHLY".equals(windowBilling[0])) continue;
                            int windowFeeMinor = Integer.parseInt(windowBilling[1]);
                            if (windowFeeMinor <= 0) continue;

                            minPeriodStart = Math.min(minPeriodStart, pStart);
                            maxPeriodEnd = Math.max(maxPeriodEnd, pEnd);
                            monthlyFirstWindowStart = Math.min(monthlyFirstWindowStart, pStart);
                            monthlyLastWindowStart = Math.max(monthlyLastWindowStart, pStart);

                            int h = (startMin / 60) % 12;
                            if (h == 0) h = 12;
                            String ampm = (startMin >= 720) ? "PM" : "AM";
                            String desc = String.format(Locale.US, "Monthly Schedule (%02d:%02d %s) · %s – %s",
                                    h, startMin % 60, ampm, dateFmt.format(new Date(pStart)), dateFmt.format(new Date(pEnd)));

                            String monthlyItemId = UUID.randomUUID().toString();
                            monthlyPeriodStartByItemId.put(monthlyItemId, pStart);
                            pendingItems.add(new InvoiceItem(
                                    monthlyItemId, accountId, invoiceId,
                                    groupId, null, desc, "MONTHLY", 1, windowFeeMinor, windowFeeMinor, now
                            ));
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
                                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o " +
                                "ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                                "AND a." + AttendanceEntry.COLUMN_ACCOUNT_ID + " = o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " " +
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
                            String[] resolved = AttendanceRepository.resolveBillingForOccurrence(db, accountId, studentId, schedId, groupId, startMin, occDate);
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
                        String desc = String.format(Locale.US, "Class on %s (%02d:%02d %s) · %s",
                                dateFmt.format(new Date(occDate)), h, startMin % 60, ampm, status);

                        pendingItems.add(new InvoiceItem(
                                itemId, accountId, invoiceId,
                                groupId, occId, desc, "PER_CLASS", 1, feeMinor, feeMinor, now
                        ));
                        attendanceIdsToLock.add(attId + ":" + itemId);
                    }
                }

                if (pendingItems.isEmpty()) continue;

                // M5: add up in 64 bits; an invoice that would not fit in an int is never created
                long subtotalLong = 0L;
                for (InvoiceItem item : pendingItems) {
                    subtotalLong += item.getAmountMinor();
                }
                if (subtotalLong <= 0L || subtotalLong > Integer.MAX_VALUE) continue;
                int subtotalMinor = (int) subtotalLong;

                String displayId = generateNextInvoiceId(accountId, db);
                SimpleDateFormat periodFmt = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
                String billingPeriod;
                if (monthlyFirstWindowStart == Long.MAX_VALUE) {
                    billingPeriod = periodFmt.format(new Date(now));
                } else {
                    String first = periodFmt.format(new Date(monthlyFirstWindowStart));
                    String last = periodFmt.format(new Date(monthlyLastWindowStart));
                    billingPeriod = first.equals(last) ? first : first + " – " + last;
                }
                long dueDate = now + (7L * 24L * 60L * 60L * 1000L);

                ContentValues invVal = new ContentValues();
                invVal.put(InvoiceEntry.COLUMN_ID, invoiceId);
                invVal.put(InvoiceEntry.COLUMN_ACCOUNT_ID, accountId);
                invVal.put(InvoiceEntry.COLUMN_STUDENT_ID, studentId);
                invVal.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, displayId);
                invVal.put(InvoiceEntry.COLUMN_BILLING_PERIOD, billingPeriod);
                invVal.put(InvoiceEntry.COLUMN_PERIOD_START, minPeriodStart == Long.MAX_VALUE ? now : minPeriodStart);
                invVal.put(InvoiceEntry.COLUMN_PERIOD_END, maxPeriodEnd == 0L ? now : maxPeriodEnd);
                invVal.put(InvoiceEntry.COLUMN_ISSUE_DATE, now);
                invVal.put(InvoiceEntry.COLUMN_DUE_DATE, dueDate);
                invVal.put(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, subtotalMinor);
                invVal.put(InvoiceEntry.COLUMN_DISCOUNT_MINOR, 0);
                invVal.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, subtotalMinor);
                invVal.put(InvoiceEntry.COLUMN_PAID_AMOUNT, 0);
                invVal.put(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_UNPAID);
                invVal.put(InvoiceEntry.COLUMN_IS_VOID, 0);
                invVal.put(InvoiceEntry.COLUMN_CREATED_AT, now);
                invVal.put(InvoiceEntry.COLUMN_UPDATED_AT, now);
                invVal.put(InvoiceEntry.COLUMN_LAST_MODIFIED, now);
                db.insertOrThrow(InvoiceEntry.TABLE_NAME, null, invVal);

                for (InvoiceItem item : pendingItems) {
                    ContentValues itemVal = new ContentValues();
                    itemVal.put(InvoiceItemEntry.COLUMN_ID, item.getId());
                    itemVal.put(InvoiceItemEntry.COLUMN_ACCOUNT_ID, accountId);
                    itemVal.put(InvoiceItemEntry.COLUMN_INVOICE_ID, invoiceId);
                    itemVal.put(InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID, item.getScheduleGroupId());
                    itemVal.put(InvoiceItemEntry.COLUMN_OCCURRENCE_ID, item.getOccurrenceId());
                    itemVal.put(InvoiceItemEntry.COLUMN_DESCRIPTION, item.getDescription());
                    itemVal.put(InvoiceItemEntry.COLUMN_BILLING_MODEL, item.getBillingModel());
                    itemVal.put(InvoiceItemEntry.COLUMN_QUANTITY, item.getQuantity());
                    itemVal.put(InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR, item.getUnitPriceMinor());
                    itemVal.put(InvoiceItemEntry.COLUMN_AMOUNT_MINOR, item.getAmountMinor());
                    Long billedWindowStart = monthlyPeriodStartByItemId.get(item.getId());
                    itemVal.put(InvoiceItemEntry.COLUMN_PERIOD_START, billedWindowStart != null ? billedWindowStart : 0L);
                    itemVal.put(InvoiceItemEntry.COLUMN_CREATED_AT, now);
                    itemVal.put(InvoiceItemEntry.COLUMN_UPDATED_AT, now);
                    itemVal.put(InvoiceItemEntry.COLUMN_LAST_MODIFIED, now);
                    db.insertOrThrow(InvoiceItemEntry.TABLE_NAME, null, itemVal);
                }

                for (String lockPair : attendanceIdsToLock) {
                    String[] parts = lockPair.split(":");
                    ContentValues lockVal = new ContentValues();
                    lockVal.put(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID, parts[1]);
                    lockVal.put(AttendanceEntry.COLUMN_UPDATED_AT, now);
                    lockVal.put(AttendanceEntry.COLUMN_LAST_MODIFIED, now);
                    db.update(AttendanceEntry.TABLE_NAME, lockVal,
                            AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_ID + " = ?",
                            new String[]{accountId, parts[0]});
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

    @NonNull
    public List<Invoice> getInvoicesForStudent(@NonNull String accountId, @NonNull String studentId) {
        List<Invoice> result = new ArrayList<>();
        if (accountId.trim().isEmpty() || studentId.trim().isEmpty()) return result;
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String sql = SQL_SELECT_INVOICE_WITH_DERIVED_PAID +
                "WHERE i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND i." + InvoiceEntry.COLUMN_STUDENT_ID + " = ? " +
                "AND i." + InvoiceEntry.COLUMN_IS_VOID + " = 0 " +
                "ORDER BY i." + InvoiceEntry.COLUMN_CREATED_AT + " DESC";
        try (Cursor cursor = db.rawQuery(sql, new String[]{accountId, studentId})) {
            while (cursor.moveToNext()) {
                result.add(mapCursorToInvoice(cursor));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return result;
    }

    @Nullable
    public Invoice getInvoiceById(@NonNull String accountId, @NonNull String invoiceId) {
        if (accountId.trim().isEmpty() || invoiceId.trim().isEmpty()) return null;
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String sql = SQL_SELECT_INVOICE_WITH_DERIVED_PAID +
                "WHERE i." + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND i." + InvoiceEntry.COLUMN_ID + " = ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{accountId, invoiceId})) {
            if (cursor.moveToFirst()) {
                return mapCursorToInvoice(cursor);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    @NonNull
    public List<InvoiceItem> getItemsForInvoice(@NonNull String accountId, @NonNull String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<InvoiceItem> items = new ArrayList<>();
        try (Cursor c = db.query(InvoiceItemEntry.TABLE_NAME, null,
                InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceItemEntry.COLUMN_INVOICE_ID + " = ?",
                new String[]{accountId, invoiceId}, null, null, InvoiceItemEntry.COLUMN_CREATED_AT + " ASC")) {
            while (c.moveToNext()) {
                items.add(mapCursorToInvoiceItem(c));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return items;
    }

    @NonNull
    private InvoiceItem mapCursorToInvoiceItem(@NonNull Cursor c) {
        return new InvoiceItem(
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
        );
    }

    public int getTotalRevenueForAccount(@NonNull String accountId) {
        if (accountId.trim().isEmpty()) return 0;
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        int netRevenueMinor = 0;
        try {
            String sql = "SELECT " +
                    "COALESCE((SELECT SUM(CASE WHEN " + TransactionEntry.COLUMN_TYPE + " = 'PAYMENT' THEN " +
                    TransactionEntry.COLUMN_AMOUNT_MINOR + " ELSE -" + TransactionEntry.COLUMN_AMOUNT_MINOR + " END) " +
                    "FROM " + TransactionEntry.TABLE_NAME +
                    " WHERE " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_IS_REVERSED + " = 0), 0) + " +
                    "COALESCE((SELECT SUM(p." + PaymentEntry.COLUMN_AMOUNT + ") " +
                    "FROM " + PaymentEntry.TABLE_NAME + " p " +
                    "WHERE p." + PaymentEntry.COLUMN_ACCOUNT_ID + " = ? " +
                    "AND NOT EXISTS (SELECT 1 FROM " + TransactionEntry.TABLE_NAME + " t " +
                    "                WHERE t." + TransactionEntry.COLUMN_ACCOUNT_ID + " = p." + PaymentEntry.COLUMN_ACCOUNT_ID + " " +
                    "                AND t." + TransactionEntry.COLUMN_ID + " = p." + PaymentEntry.COLUMN_ID + ")), 0)";
            try (Cursor c = db.rawQuery(sql, new String[]{accountId, accountId})) {
                if (c.moveToFirst()) netRevenueMinor = c.getInt(0);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return Math.max(0, netRevenueMinor);
    }

    private Invoice mapCursorToInvoice(Cursor cursor) {
        int derivedPaid = cursor.getInt(cursor.getColumnIndexOrThrow("derived_paid_minor"));
        int effectivePaid = Math.max(0, derivedPaid);

        int totalAmount = cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_TOTAL_AMOUNT));
        int subtotal = cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_SUBTOTAL_MINOR));
        int discount = cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_DISCOUNT_MINOR));
        long created = cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_CREATED_AT));
        long issueDate = cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ISSUE_DATE));
        long dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_DUE_DATE));
        long pStart = cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_PERIOD_START));
        long pEnd = cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_PERIOD_END));

        long updated = created;
        int lastModIdx = cursor.getColumnIndex(InvoiceEntry.COLUMN_LAST_MODIFIED);
        if (lastModIdx != -1 && !cursor.isNull(lastModIdx) && cursor.getLong(lastModIdx) > 0) {
            updated = cursor.getLong(lastModIdx);
        } else {
            int upIdx = cursor.getColumnIndex(InvoiceEntry.COLUMN_UPDATED_AT);
            if (upIdx != -1 && !cursor.isNull(upIdx) && cursor.getLong(upIdx) > 0) {
                updated = cursor.getLong(upIdx);
            }
        }

        return new Invoice(
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ACCOUNT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_STUDENT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY)),
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_BILLING_PERIOD)),
                pStart > 0 ? pStart : created,
                pEnd > 0 ? pEnd : created,
                issueDate > 0 ? issueDate : created,
                dueDate,
                subtotal > 0 ? subtotal : totalAmount,
                discount,
                totalAmount,
                effectivePaid,
                cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_NOTES)),
                created,
                updated
        );
    }

    public static void logAudit(@NonNull SQLiteDatabase db,
                                @NonNull String accountId,
                                @Nullable String studentId,
                                @NonNull String entityType,
                                @NonNull String entityId,
                                @NonNull String action,
                                @NonNull String details) {
        long now = System.currentTimeMillis();
        ContentValues av = new ContentValues();
        av.put(AuditLogEntry.COLUMN_ID, UUID.randomUUID().toString());
        av.put(AuditLogEntry.COLUMN_ACCOUNT_ID, accountId);
        av.put(AuditLogEntry.COLUMN_STUDENT_ID, studentId);
        av.put(AuditLogEntry.COLUMN_ENTITY_TYPE, entityType);
        av.put(AuditLogEntry.COLUMN_ENTITY_ID, entityId);
        av.put(AuditLogEntry.COLUMN_ACTION, action);
        av.put(AuditLogEntry.COLUMN_DETAILS, details);
        av.put(AuditLogEntry.COLUMN_TIMESTAMP, now);
        av.put(AuditLogEntry.COLUMN_CREATED_AT, now);
        av.put(AuditLogEntry.COLUMN_UPDATED_AT, now);
        av.put(AuditLogEntry.COLUMN_LAST_MODIFIED, now);
        db.insertWithOnConflict(AuditLogEntry.TABLE_NAME, null, av, SQLiteDatabase.CONFLICT_IGNORE);
    }
}
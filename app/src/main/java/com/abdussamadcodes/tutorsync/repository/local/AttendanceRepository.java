package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import com.abdussamadcodes.tutorsync.model.AttendanceHistoryItem;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AttendanceRepository {

    public static class UnbilledClassCharge {
        public final String attendanceId;
        public final String occurrenceId;
        public final long dateMillis;
        public final int startTimeMinutes;
        public final String status;
        public final int feeMinor;
        public final boolean isBilled;

        public UnbilledClassCharge(String attendanceId, String occurrenceId, long dateMillis,
                                   int startTimeMinutes, String status, int feeMinor, boolean isBilled) {
            this.attendanceId = attendanceId;
            this.occurrenceId = occurrenceId;
            this.dateMillis = dateMillis;
            this.startTimeMinutes = startTimeMinutes;
            this.status = status;
            this.feeMinor = feeMinor;
            this.isBilled = isBilled;
        }
    }

    /**
     * Billing model and fee that applied to a class on its date.
     * (Kept for existing callers; see the overload with subjectId.)
     */
    @NonNull
    public static String[] resolveBillingForOccurrence(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @Nullable String studentId,
            @Nullable String scheduleId,
            @Nullable String scheduleGroupId,
            int startTimeMinutes,
            long occDateMillis) {
        return resolveBillingForOccurrence(db, accountId, studentId, null, scheduleId,
                scheduleGroupId, startTimeMinutes, occDateMillis);
    }

    /**
     * M3: the billing model and fee that applied to a class ON ITS DATE.
     *
     * 1. the fee-history row in force on the class date (or the closest earlier one);
     * 2. when the class is older than every history row, the EARLIEST known row - never the
     *    latest fee, so changing a fee today cannot re-price old classes;
     * 3. only when the group has no history at all (legacy data): the schedule row, then the
     *    student's schedule at the same time (same subject when known), then the legacy fee agreement.
     */
    @NonNull
    public static String[] resolveBillingForOccurrence(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @Nullable String studentId,
            @Nullable String subjectId,
            @Nullable String scheduleId,
            @Nullable String scheduleGroupId,
            int startTimeMinutes,
            long occDateMillis) {

        String billingModel = null;
        int feeMinor = 0;

        // 1 + 2. fee history of the schedule group
        if (scheduleGroupId != null && !scheduleGroupId.isEmpty()) {
            String[] hist = lookupFeeHistoryForDate(db, accountId, scheduleGroupId, occDateMillis);
            if (hist != null) {
                billingModel = hist[0];
                feeMinor = Integer.parseInt(hist[1]);
            }
        }

        // 3a. recurring_schedules by scheduleId
        if ((billingModel == null || feeMinor <= 0) && scheduleId != null && !scheduleId.isEmpty()) {
            try (Cursor sc = db.query(ScheduleEntry.TABLE_NAME,
                    new String[]{ScheduleEntry.COLUMN_BILLING_MODEL, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR},
                    ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, scheduleId}, null, null, null)) {
                if (sc.moveToFirst()) {
                    billingModel = sc.getString(0);
                    feeMinor = sc.getInt(1);
                }
            }
        }

        // 3b. recurring_schedules of the same student at the same time (same subject when known)
        if ((billingModel == null || feeMinor <= 0) && studentId != null && !studentId.isEmpty()) {
            String sel = ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                    ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " +
                    ScheduleEntry.COLUMN_START_TIME_MINUTES + " = ?";
            String[] args;
            if (subjectId != null && !subjectId.isEmpty()) {
                sel += " AND " + ScheduleEntry.COLUMN_SUBJECT_ID + " = ?";
                args = new String[]{accountId, studentId, String.valueOf(startTimeMinutes), subjectId};
            } else {
                args = new String[]{accountId, studentId, String.valueOf(startTimeMinutes)};
            }
            try (Cursor sc2 = db.query(ScheduleEntry.TABLE_NAME,
                    new String[]{ScheduleEntry.COLUMN_BILLING_MODEL, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR},
                    sel, args, null, null, ScheduleEntry.COLUMN_LAST_MODIFIED + " DESC", "1")) {
                if (sc2.moveToFirst()) {
                    billingModel = sc2.getString(0);
                    feeMinor = sc2.getInt(1);
                }
            }
        }

        // 3c. legacy fee agreement of the student
        if ((billingModel == null || feeMinor <= 0) && studentId != null && !studentId.isEmpty()) {
            try (Cursor fc = db.query(FeeAgreementEntry.TABLE_NAME,
                    new String[]{FeeAgreementEntry.COLUMN_FEE_TYPE, FeeAgreementEntry.COLUMN_AMOUNT_MINOR},
                    FeeAgreementEntry.COLUMN_ACCOUNT_ID + " = ? AND " + FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?",
                    new String[]{accountId, studentId}, null, null, null)) {
                if (fc.moveToFirst()) {
                    billingModel = fc.getString(0);
                    feeMinor = fc.getInt(1);
                }
            }
        }

        if (billingModel == null || billingModel.isEmpty()) billingModel = "MONTHLY";
        return new String[]{billingModel, String.valueOf(Math.max(0, feeMinor))};
    }

    /**
     * Fee-history row for a class date: the row in force on that day (or the closest earlier row);
     * if the date is older than every row, the earliest row. Returns {model, fee} or null when the
     * group has no usable history.
     */
    @Nullable
    static String[] lookupFeeHistoryForDate(@NonNull SQLiteDatabase db, @NonNull String accountId,
                                            @NonNull String scheduleGroupId, long occDateMillis) {
        long endOfDayMillis = occDateMillis + 86_399_999L;
        String[] cols = new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR};

        try (Cursor hc = db.query(ScheduleFeeHistoryEntry.TABLE_NAME, cols,
                ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? AND " +
                        ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " <= ?",
                new String[]{accountId, scheduleGroupId, String.valueOf(endOfDayMillis)},
                null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " DESC", "1")) {
            if (hc.moveToFirst() && hc.getInt(1) > 0) {
                return new String[]{hc.getString(0), String.valueOf(hc.getInt(1))};
            }
        }

        try (Cursor ec = db.query(ScheduleFeeHistoryEntry.TABLE_NAME, cols,
                ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ?",
                new String[]{accountId, scheduleGroupId},
                null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " ASC", "1")) {
            if (ec.moveToFirst() && ec.getInt(1) > 0) {
                return new String[]{ec.getString(0), String.valueOf(ec.getInt(1))};
            }
        }
        return null;
    }

    /**
     * Like {@link #lookupFeeHistoryForDate} but ONLY when a history row really covers that day.
     * Used before an old billing decision is changed: no hard evidence, no change.
     */
    @Nullable
    static String[] lookupCoveringFeeHistory(@NonNull SQLiteDatabase db, @NonNull String accountId,
                                             @NonNull String scheduleGroupId, long occDateMillis) {
        long endOfDayMillis = occDateMillis + 86_399_999L;
        try (Cursor hc = db.query(ScheduleFeeHistoryEntry.TABLE_NAME,
                new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR},
                ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? AND " +
                        ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " <= ? AND (" +
                        ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " IS NULL OR " +
                        ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " >= ?)",
                new String[]{accountId, scheduleGroupId, String.valueOf(endOfDayMillis), String.valueOf(occDateMillis)},
                null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " DESC", "1")) {
            if (hc.moveToFirst() && hc.getInt(1) > 0) {
                return new String[]{hc.getString(0), String.valueOf(hc.getInt(1))};
            }
        }
        return null;
    }

    // Result codes of recordAttendanceWithBilling()
    public static final int RESULT_FAILED = 0;
    public static final int RESULT_OK = 1;
    /** The class is already on an invoice, so its attendance can no longer be changed. */
    public static final int RESULT_BILLED_LOCKED = -2;
    /** The old makeup class of this record already has attendance/billing and cannot be replaced. */
    public static final int RESULT_MAKEUP_RECORDED = -3;

    /**
     * Records OR safely corrects the attendance of one class (account-scoped, one transaction).
     *
     * Correction rules (H2):
     * - a class that is already on an invoice is never modified (RESULT_BILLED_LOCKED),
     * - the billing link of an existing record is never wiped,
     * - an existing makeup class is kept when unchanged, replaced when a new makeup is requested,
     *   removed when no longer wanted, and never duplicated; if the old makeup class already has
     *   attendance or billing the correction is refused (RESULT_MAKEUP_RECORDED),
     * - a fee that was already fixed for this class is kept (not re-priced with today's fee).
     */
    public int recordAttendanceWithBilling(
            @NonNull AttendanceRecord record,
            boolean createMakeup,
            long makeupDateMillis,
            int makeupStartMinutes) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            String accountId = record.getAccountId();
            String occId = record.getOccurrenceId();
            long now = System.currentTimeMillis();
            long modTime = record.getUpdatedAt() > 0 ? record.getUpdatedAt() : now;

            String studentId = null;
            String subjectId = null;
            String scheduleId = null;
            String scheduleGroupId = null;
            long occDate = now;
            int startMin = 0;
            int durationMin = 60;

            try (Cursor oc = db.query(OccurrenceEntry.TABLE_NAME, null,
                    OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + OccurrenceEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, occId}, null, null, null)) {
                if (oc.moveToFirst()) {
                    studentId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_STUDENT_ID));
                    subjectId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SUBJECT_ID));
                    scheduleId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SCHEDULE_ID));
                    scheduleGroupId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID));
                    occDate = oc.getLong(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DATE));
                    startMin = oc.getInt(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_START_TIME_MINUTES));
                    durationMin = oc.getInt(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DURATION_MINUTES));
                } else {
                    // The class does not exist in this account: never write anything.
                    return RESULT_FAILED;
                }
            }

            // ---- existing attendance row of this class (if any) ----
            String existingId = null;
            long createdAt = modTime;
            String existingBilled = null;
            String existingMakeupId = null;
            String existingDecision = null;
            int existingFee = 0;
            try (Cursor ec = db.query(AttendanceEntry.TABLE_NAME,
                    new String[]{AttendanceEntry.COLUMN_ID, AttendanceEntry.COLUMN_CREATED_AT,
                            AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID,
                            AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID,
                            AttendanceEntry.COLUMN_CHARGE_DECISION,
                            AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR},
                    AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = ?",
                    new String[]{accountId, occId}, null, null, null)) {
                if (ec.moveToFirst()) {
                    existingId = ec.getString(0);
                    if (!ec.isNull(1) && ec.getLong(1) > 0) {
                        createdAt = ec.getLong(1);
                    }
                    existingBilled = ec.isNull(2) ? null : ec.getString(2);
                    existingMakeupId = ec.isNull(3) ? null : ec.getString(3);
                    existingDecision = ec.getString(4);
                    existingFee = ec.getInt(5);
                }
            }

            // A class that is already invoiced is locked.
            if (existingId != null && existingBilled != null && !existingBilled.trim().isEmpty()) {
                return RESULT_BILLED_LOCKED;
            }

            // ---- billing decision ----
            String[] resolved = resolveBillingForOccurrence(db, accountId, studentId, subjectId, scheduleId, scheduleGroupId, startMin, occDate);
            String billingModel = resolved[0];
            int resolvedFee = Integer.parseInt(resolved[1]);

            String finalDecision = record.getChargeDecision();
            int finalFeeMinor = record.getApplicableFeeMinor() > 0 ? record.getApplicableFeeMinor() : resolvedFee;

            // Keep the fee that was already fixed for this class instead of re-pricing it.
            if (record.getApplicableFeeMinor() <= 0
                    && existingId != null
                    && AttendanceRecord.DECISION_CHARGE.equals(existingDecision)
                    && existingFee > 0) {
                finalFeeMinor = existingFee;
            }

            if ("MONTHLY".equals(billingModel)) {
                if (AttendanceRecord.DECISION_CHARGE.equals(finalDecision)) {
                    finalDecision = AttendanceRecord.DECISION_INCLUDED_MONTHLY;
                }
                finalFeeMinor = 0;
            } else if (!AttendanceRecord.DECISION_CHARGE.equals(finalDecision)) {
                finalFeeMinor = 0;
            }

            // ---- makeup class handling ----
            boolean wantsNewMakeup = createMakeup && makeupDateMillis > 0 && studentId != null;
            String makeupOccId = null;
            if (existingMakeupId != null && !existingMakeupId.trim().isEmpty()) {
                boolean keepOld = !wantsNewMakeup && existingMakeupId.equals(record.getMakeupOccurrenceId());
                if (keepOld) {
                    makeupOccId = existingMakeupId;
                } else {
                    if (occurrenceHasAttendanceOrBilling(db, accountId, existingMakeupId)) {
                        return RESULT_MAKEUP_RECORDED;
                    }
                    db.delete(OccurrenceEntry.TABLE_NAME,
                            OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + OccurrenceEntry.COLUMN_ID + " = ?",
                            new String[]{accountId, existingMakeupId});
                }
            } else {
                makeupOccId = record.getMakeupOccurrenceId();
            }

            if (wantsNewMakeup) {
                makeupOccId = UUID.randomUUID().toString();
                ContentValues mcv = new ContentValues();
                mcv.put(OccurrenceEntry.COLUMN_ID, makeupOccId);
                mcv.put(OccurrenceEntry.COLUMN_ACCOUNT_ID, accountId);
                mcv.put(OccurrenceEntry.COLUMN_STUDENT_ID, studentId);
                mcv.put(OccurrenceEntry.COLUMN_SUBJECT_ID, subjectId);
                mcv.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, scheduleId);
                mcv.put(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, scheduleGroupId);
                mcv.put(OccurrenceEntry.COLUMN_DATE, makeupDateMillis);
                mcv.put(OccurrenceEntry.COLUMN_START_TIME_MINUTES, makeupStartMinutes >= 0 ? makeupStartMinutes : startMin);
                mcv.put(OccurrenceEntry.COLUMN_DURATION_MINUTES, durationMin);
                mcv.put(OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID, occId);
                mcv.put(OccurrenceEntry.COLUMN_CREATED_AT, modTime);
                mcv.put(OccurrenceEntry.COLUMN_UPDATED_AT, modTime);
                mcv.put(OccurrenceEntry.COLUMN_LAST_MODIFIED, modTime);
                db.insertOrThrow(OccurrenceEntry.TABLE_NAME, null, mcv);
            }

            String targetId = existingId != null ? existingId : TutorSyncContract.ensureValidUuid(record.getId());

            ContentValues values = new ContentValues();
            values.put(AttendanceEntry.COLUMN_ID, targetId);
            values.put(AttendanceEntry.COLUMN_ACCOUNT_ID, accountId);
            values.put(AttendanceEntry.COLUMN_OCCURRENCE_ID, occId);
            values.put(AttendanceEntry.COLUMN_STATUS, record.getStatus());
            values.put(AttendanceEntry.COLUMN_CHARGE_DECISION, finalDecision);
            values.put(AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, finalFeeMinor);
            values.put(AttendanceEntry.COLUMN_NEEDS_RESCHEDULE, record.isNeedsReschedule() ? 1 : 0);
            values.put(AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID, makeupOccId);
            // Never wipe an existing billing link (a billed class returned above, so this is null
            // for corrections and for brand-new records).
            values.put(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID,
                    existingId != null ? existingBilled : record.getBilledInvoiceItemId());
            values.put(AttendanceEntry.COLUMN_CREATED_AT, createdAt);
            values.put(AttendanceEntry.COLUMN_UPDATED_AT, modTime);
            values.put(AttendanceEntry.COLUMN_LAST_MODIFIED, modTime);

            boolean ok;
            if (existingId != null) {
                ok = db.update(AttendanceEntry.TABLE_NAME, values,
                        AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, existingId}) > 0;
            } else {
                ok = db.insert(AttendanceEntry.TABLE_NAME, null, values) != -1;
            }
            if (!ok) {
                return RESULT_FAILED;
            }
            db.setTransactionSuccessful();
            return RESULT_OK;
        } catch (Exception e) {
            return RESULT_FAILED;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private static boolean occurrenceHasAttendanceOrBilling(@NonNull SQLiteDatabase db,
                                                            @NonNull String accountId,
                                                            @NonNull String occurrenceId) {
        try (Cursor c = db.query(AttendanceEntry.TABLE_NAME,
                new String[]{AttendanceEntry.COLUMN_ID},
                AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = ?",
                new String[]{accountId, occurrenceId}, null, null, null, "1")) {
            if (c.moveToFirst()) return true;
        }
        try (Cursor c = db.query(InvoiceItemEntry.TABLE_NAME,
                new String[]{InvoiceItemEntry.COLUMN_ID},
                InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " = ?",
                new String[]{accountId, occurrenceId}, null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    /** What the correction form needs to pre-fill an already recorded class. */
    public static class AttendanceEditState {
        public final String status;
        public final String chargeDecision;
        public final int feeMinor;
        public final boolean billed;
        @Nullable public final String makeupOccurrenceId;
        public final long makeupDateMillis;
        public final int makeupStartMinutes;

        AttendanceEditState(String status, String chargeDecision, int feeMinor, boolean billed,
                            @Nullable String makeupOccurrenceId, long makeupDateMillis, int makeupStartMinutes) {
            this.status = status;
            this.chargeDecision = chargeDecision;
            this.feeMinor = feeMinor;
            this.billed = billed;
            this.makeupOccurrenceId = makeupOccurrenceId;
            this.makeupDateMillis = makeupDateMillis;
            this.makeupStartMinutes = makeupStartMinutes;
        }
    }

    /** Returns the recorded attendance of one class, or null when it was never recorded. */
    @Nullable
    public AttendanceEditState getAttendanceEditState(@NonNull String accountId, @NonNull String occurrenceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor c = db.query(AttendanceEntry.TABLE_NAME,
                new String[]{AttendanceEntry.COLUMN_STATUS, AttendanceEntry.COLUMN_CHARGE_DECISION,
                        AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID,
                        AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID},
                AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = ?",
                new String[]{accountId, occurrenceId}, null, null, null, "1")) {
            if (!c.moveToFirst()) {
                return null;
            }
            String makeupId = c.isNull(4) ? null : c.getString(4);
            long makeupDate = 0L;
            int makeupStart = -1;
            if (makeupId != null && !makeupId.trim().isEmpty()) {
                try (Cursor mc = db.query(OccurrenceEntry.TABLE_NAME,
                        new String[]{OccurrenceEntry.COLUMN_DATE, OccurrenceEntry.COLUMN_START_TIME_MINUTES},
                        OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + OccurrenceEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, makeupId}, null, null, null, "1")) {
                    if (mc.moveToFirst()) {
                        makeupDate = mc.getLong(0);
                        makeupStart = mc.getInt(1);
                    } else {
                        makeupId = null;
                    }
                }
            } else {
                makeupId = null;
            }
            return new AttendanceEditState(c.getString(0), c.getString(1), c.getInt(2),
                    !c.isNull(3) && !c.getString(3).trim().isEmpty(), makeupId, makeupDate, makeupStart);
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    /** True when at least one of these classes is already on an invoice. */
    public boolean isAnyAttendanceBilled(@NonNull String accountId, @NonNull List<String> occurrenceIds) {
        if (occurrenceIds.isEmpty()) return false;
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            StringBuilder in = new StringBuilder();
            String[] args = new String[occurrenceIds.size() + 1];
            args[0] = accountId;
            for (int i = 0; i < occurrenceIds.size(); i++) {
                in.append(i == 0 ? "?" : ",?");
                args[i + 1] = occurrenceIds.get(i);
            }
            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM " + AttendanceEntry.TABLE_NAME + " WHERE " +
                            AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            AttendanceEntry.COLUMN_OCCURRENCE_ID + " IN (" + in + ") AND " +
                            AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " IS NOT NULL LIMIT 1", args)) {
                return c.moveToFirst();
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    /**
     * Classes of the last {@code lookbackDays} days (up to the end of today) for the Attendance
     * History screen, newest first. Classes of the same student at the same time are merged.
     * Classes of deleted students/subjects and today's classes that have not ended yet (and are
     * not recorded) are left out.
     */
    @NonNull
    public List<AttendanceHistoryItem> getClassHistory(@NonNull String accountId, int lookbackDays) {
        List<AttendanceHistoryItem> result = new ArrayList<>();
        if (accountId.trim().isEmpty()) return result;

        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfToday = cal.getTimeInMillis();
        cal.add(Calendar.DATE, 1);
        long startOfTomorrow = cal.getTimeInMillis();
        cal.setTimeInMillis(startOfToday);
        cal.add(Calendar.DATE, -Math.max(1, lookbackDays));
        long from = cal.getTimeInMillis();

        String sql = "SELECT o." + OccurrenceEntry.COLUMN_ID + ", o." + OccurrenceEntry.COLUMN_STUDENT_ID + ", " +
                "st." + StudentEntry.COLUMN_NAME + ", sb." + SubjectEntry.COLUMN_TITLE + ", " +
                "o." + OccurrenceEntry.COLUMN_DATE + ", o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + ", " +
                "o." + OccurrenceEntry.COLUMN_DURATION_MINUTES + ", o." + OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID + ", " +
                "a." + AttendanceEntry.COLUMN_ID + ", a." + AttendanceEntry.COLUMN_STATUS + ", " +
                "a." + AttendanceEntry.COLUMN_CHARGE_DECISION + ", a." + AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + ", " +
                "a." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + ", a." + AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID + " " +
                "FROM " + OccurrenceEntry.TABLE_NAME + " o " +
                "INNER JOIN " + StudentEntry.TABLE_NAME + " st ON st." + StudentEntry.COLUMN_ACCOUNT_ID + " = o." +
                OccurrenceEntry.COLUMN_ACCOUNT_ID + " AND st." + StudentEntry.COLUMN_ID + " = o." + OccurrenceEntry.COLUMN_STUDENT_ID + " " +
                "INNER JOIN " + SubjectEntry.TABLE_NAME + " sb ON sb." + SubjectEntry.COLUMN_ACCOUNT_ID + " = o." +
                OccurrenceEntry.COLUMN_ACCOUNT_ID + " AND sb." + SubjectEntry.COLUMN_ID + " = o." + OccurrenceEntry.COLUMN_SUBJECT_ID + " " +
                "LEFT JOIN " + AttendanceEntry.TABLE_NAME + " a ON a." + AttendanceEntry.COLUMN_ACCOUNT_ID + " = o." +
                OccurrenceEntry.COLUMN_ACCOUNT_ID + " AND a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                "WHERE o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? " +
                "AND o." + OccurrenceEntry.COLUMN_DATE + " >= ? AND o." + OccurrenceEntry.COLUMN_DATE + " < ? " +
                "AND st." + StudentEntry.COLUMN_IS_DELETED + " = 0 AND sb." + SubjectEntry.COLUMN_IS_DELETED + " = 0 " +
                "ORDER BY o." + OccurrenceEntry.COLUMN_DATE + " DESC, o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + " DESC, " +
                "st." + StudentEntry.COLUMN_NAME + " COLLATE NOCASE ASC, o." + OccurrenceEntry.COLUMN_ID + " ASC";

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        Map<String, AttendanceHistoryItem> groups = new LinkedHashMap<>();
        Map<String, Boolean> primaryHasCharge = new LinkedHashMap<>();
        try (Cursor c = db.rawQuery(sql, new String[]{accountId, String.valueOf(from), String.valueOf(startOfTomorrow)})) {
            while (c.moveToNext()) {
                String occId = c.getString(0);
                String studentId = c.getString(1);
                long date = c.getLong(4);
                int startMin = c.getInt(5);
                String key = studentId + "|" + date + "|" + startMin;

                AttendanceHistoryItem item = groups.get(key);
                if (item == null) {
                    item = new AttendanceHistoryItem();
                    item.studentId = studentId;
                    item.studentName = c.getString(2);
                    item.subjectSummary = "";
                    item.dateMillis = date;
                    item.startTimeMinutes = startMin;
                    item.durationMinutes = c.getInt(6);
                    item.makeupClass = !c.isNull(7) && !c.getString(7).trim().isEmpty();
                    groups.put(key, item);
                    primaryHasCharge.put(key, Boolean.FALSE);
                }

                String subject = c.getString(3);
                if (subject != null && !subject.trim().isEmpty()
                        && !item.subjectSummary.contains(subject)) {
                    item.subjectSummary = item.subjectSummary.isEmpty() ? subject : item.subjectSummary + ", " + subject;
                }

                boolean hasAttendance = !c.isNull(8);
                String status = hasAttendance ? c.getString(9) : null;
                String decision = hasAttendance ? c.getString(10) : null;
                int fee = hasAttendance ? c.getInt(11) : 0;
                boolean billed = hasAttendance && !c.isNull(12) && !c.getString(12).trim().isEmpty();
                boolean hasMakeup = hasAttendance && !c.isNull(13) && !c.getString(13).trim().isEmpty();

                boolean isChargeRow = AttendanceRecord.DECISION_CHARGE.equals(decision)
                        || AttendanceRecord.DECISION_INCLUDED_MONTHLY.equals(decision);
                // The row that carries the charge becomes the primary (first) occurrence.
                if (isChargeRow && !Boolean.TRUE.equals(primaryHasCharge.get(key))) {
                    item.occurrenceIds.add(0, occId);
                    primaryHasCharge.put(key, Boolean.TRUE);
                    item.status = status;
                    item.chargeDecision = decision;
                } else {
                    item.occurrenceIds.add(occId);
                    if (item.status == null && hasAttendance) {
                        item.status = status;
                        item.chargeDecision = decision;
                    }
                }

                if (!hasAttendance) item.needsAttendance = true;
                if (AttendanceRecord.DECISION_CHARGE.equals(decision)) item.feeMinor += fee;
                if (billed) item.billed = true;
                if (hasMakeup) item.makeupScheduled = true;
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }

        for (AttendanceHistoryItem item : groups.values()) {
            long classEnd = item.dateMillis + (long) (item.startTimeMinutes + Math.max(0, item.durationMinutes)) * 60_000L;
            // Unrecorded classes that are still upcoming today belong on the dashboard.
            if (item.needsAttendance && classEnd > now) {
                continue;
            }
            if (item.needsAttendance) {
                item.status = null;
                item.chargeDecision = null;
            }
            result.add(item);
        }
        return result;
    }

    /** Number of recent classes (last 30 days) that still have no attendance. */
    public int countClassesNeedingAttendance(@NonNull String accountId) {
        int count = 0;
        for (AttendanceHistoryItem item : getClassHistory(accountId, 30)) {
            if (item.needsAttendance) count++;
        }
        return count;
    }

    public int recordAttendance(@NonNull AttendanceRecord record) {
        return recordAttendanceWithBilling(record, false, -1L, 0);
    }

    @NonNull
    public List<UnbilledClassCharge> getPerClassAttendanceChargesForStudent(@NonNull String accountId, @NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<UnbilledClassCharge> list = new ArrayList<>();

        healPerClassAttendanceRecords(db, accountId, studentId);

        String sql = "SELECT a." + AttendanceEntry.COLUMN_ID + ", o." + OccurrenceEntry.COLUMN_ID + ", " +
                "o." + OccurrenceEntry.COLUMN_DATE + ", o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + ", " +
                "a." + AttendanceEntry.COLUMN_STATUS + ", a." + AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + ", " +
                "a." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " " +
                "FROM " + AttendanceEntry.TABLE_NAME + " a " +
                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o " +
                "ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                "AND a." + AttendanceEntry.COLUMN_ACCOUNT_ID + " = o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " " +
                "WHERE o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = ? " +
                "AND a." + AttendanceEntry.COLUMN_CHARGE_DECISION + " = 'CHARGE' " +
                "GROUP BY o." + OccurrenceEntry.COLUMN_DATE + ", o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + " " +
                "ORDER BY o." + OccurrenceEntry.COLUMN_DATE + " DESC, o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + " DESC";

        try (Cursor c = db.rawQuery(sql, new String[]{accountId, studentId})) {
            while (c.moveToNext()) {
                list.add(new UnbilledClassCharge(
                        c.getString(0),
                        c.getString(1),
                        c.getLong(2),
                        c.getInt(3),
                        c.getString(4),
                        c.getInt(5),
                        c.getString(6) != null
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return list;
    }

    public static void healPerClassAttendanceRecords(@NonNull SQLiteDatabase db, @NonNull String accountId, @Nullable String studentFilterId) {
        String sql = "SELECT a." + AttendanceEntry.COLUMN_ID + ", o." + OccurrenceEntry.COLUMN_STUDENT_ID + ", " +
                "o." + OccurrenceEntry.COLUMN_SCHEDULE_ID + ", o." + OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID + ", " +
                "o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + ", o." + OccurrenceEntry.COLUMN_DATE + ", " +
                "a." + AttendanceEntry.COLUMN_STATUS + ", a." + AttendanceEntry.COLUMN_CHARGE_DECISION + ", " +
                "a." + AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + ", o." + OccurrenceEntry.COLUMN_SUBJECT_ID + " " +
                "FROM " + AttendanceEntry.TABLE_NAME + " a " +
                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o " +
                "ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                "AND a." + AttendanceEntry.COLUMN_ACCOUNT_ID + " = o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " " +
                "WHERE o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? " +
                "AND a." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " IS NULL" +
                (studentFilterId != null ? " AND o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = ?" : "");

        String[] args = studentFilterId != null ? new String[]{accountId, studentFilterId} : new String[]{accountId};

        try (Cursor c = db.rawQuery(sql, args)) {
            long now = System.currentTimeMillis();
            while (c.moveToNext()) {
                String attId = c.getString(0);
                String stuId = c.getString(1);
                String schedId = c.getString(2);
                String groupId = c.getString(3);
                int startMin = c.getInt(4);
                long occDate = c.getLong(5);
                String status = c.getString(6);
                String decision = c.getString(7);
                int feeMinor = c.getInt(8);
                String subjId = c.getString(9);

                if (AttendanceRecord.DECISION_CHARGE.equals(decision) && feeMinor <= 0) {
                    String[] resolved = resolveBillingForOccurrence(db, accountId, stuId, subjId, schedId, groupId, startMin, occDate);
                    int resolvedFee = Integer.parseInt(resolved[1]);
                    if ("PER_CLASS".equals(resolved[0]) && resolvedFee > 0) {
                        ContentValues cv = new ContentValues();
                        cv.put(AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, resolvedFee);
                        cv.put(AttendanceEntry.COLUMN_UPDATED_AT, now);
                        cv.put(AttendanceEntry.COLUMN_LAST_MODIFIED, now);
                        db.update(AttendanceEntry.TABLE_NAME, cv,
                                AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_ID + " = ?",
                                new String[]{accountId, attId});
                    }
                } else if (AttendanceRecord.STATUS_PRESENT.equals(status) && AttendanceRecord.DECISION_INCLUDED_MONTHLY.equals(decision)) {
                    // M3: an old "included in monthly" decision is changed ONLY when the fee history
                    // clearly shows that a per-class fee was in force on that class date. A guess
                    // from today's fee model must never turn old monthly classes into charges.
                    if (groupId == null || groupId.isEmpty()) continue;
                    String[] resolved = lookupCoveringFeeHistory(db, accountId, groupId, occDate);
                    if (resolved == null) continue;
                    int resolvedFee = Integer.parseInt(resolved[1]);
                    if ("PER_CLASS".equals(resolved[0]) && resolvedFee > 0) {
                        ContentValues cv = new ContentValues();
                        cv.put(AttendanceEntry.COLUMN_CHARGE_DECISION, AttendanceRecord.DECISION_CHARGE);
                        cv.put(AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, resolvedFee);
                        cv.put(AttendanceEntry.COLUMN_UPDATED_AT, now);
                        cv.put(AttendanceEntry.COLUMN_LAST_MODIFIED, now);
                        db.update(AttendanceEntry.TABLE_NAME, cv,
                                AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_ID + " = ?",
                                new String[]{accountId, attId});
                    }
                }
            }
        }
    }

    public int getPresentCountForStudentThisMonth(@NonNull String accountId, @NonNull String studentId) {
        if (accountId.trim().isEmpty() || studentId.trim().isEmpty()) return 0;
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        int chargeableCount = 0;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfMonth = cal.getTimeInMillis();

        cal.add(Calendar.MONTH, 1);
        long endOfMonth = cal.getTimeInMillis();

        String query = "SELECT COUNT(DISTINCT o." + OccurrenceEntry.COLUMN_DATE + " || '-' || o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + ") " +
                "FROM " + AttendanceEntry.TABLE_NAME + " a " +
                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o " +
                "ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                "AND a." + AttendanceEntry.COLUMN_ACCOUNT_ID + " = o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " " +
                "WHERE o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? " +
                "AND o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = ? " +
                "AND a." + AttendanceEntry.COLUMN_CHARGE_DECISION + " = 'CHARGE' " +
                "AND o." + OccurrenceEntry.COLUMN_DATE + " >= ? " +
                "AND o." + OccurrenceEntry.COLUMN_DATE + " < ?";

        try (Cursor cursor = db.rawQuery(query,
                new String[]{accountId, studentId, String.valueOf(startOfMonth), String.valueOf(endOfMonth)})) {
            if (cursor.moveToFirst()) {
                chargeableCount = cursor.getInt(0);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return chargeableCount;
    }
}
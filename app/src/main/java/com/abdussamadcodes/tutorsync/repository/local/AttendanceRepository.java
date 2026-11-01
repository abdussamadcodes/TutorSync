package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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

    @NonNull
    public static String[] resolveBillingForOccurrence(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @Nullable String studentId,
            @Nullable String scheduleId,
            @Nullable String scheduleGroupId,
            int startTimeMinutes,
            long occDateMillis) {

        String billingModel = null;
        int feeMinor = 0;
        long endOfDayMillis = occDateMillis + 86_399_999L;

        // 1. Check schedule_fee_history at end of occurrence day
        if (scheduleGroupId != null && !scheduleGroupId.isEmpty()) {
            String sel = ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                    ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? AND " +
                    ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " <= ? AND (" +
                    ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " IS NULL OR " +
                    ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " >= ?)";
            String[] args = new String[]{accountId, scheduleGroupId, String.valueOf(endOfDayMillis), String.valueOf(occDateMillis)};

            try (Cursor hc = db.query(ScheduleFeeHistoryEntry.TABLE_NAME,
                    new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR},
                    sel, args, null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " DESC", "1")) {
                if (hc.moveToFirst()) {
                    billingModel = hc.getString(0);
                    feeMinor = hc.getInt(1);
                }
            }

            // 2. Fallback to latest schedule_fee_history for this group
            if (billingModel == null || feeMinor <= 0) {
                String sel2 = ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ?";
                String[] args2 = new String[]{accountId, scheduleGroupId};
                try (Cursor hc2 = db.query(ScheduleFeeHistoryEntry.TABLE_NAME,
                        new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR},
                        sel2, args2, null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " DESC", "1")) {
                    if (hc2.moveToFirst()) {
                        billingModel = hc2.getString(0);
                        feeMinor = hc2.getInt(1);
                    }
                }
            }
        }

        // 3. Fallback to recurring_schedules by scheduleId
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

        // 4. Fallback to recurring_schedules by studentId + startTimeMinutes
        if ((billingModel == null || feeMinor <= 0) && studentId != null && !studentId.isEmpty()) {
            try (Cursor sc2 = db.query(ScheduleEntry.TABLE_NAME,
                    new String[]{ScheduleEntry.COLUMN_BILLING_MODEL, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR},
                    ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " +
                            ScheduleEntry.COLUMN_START_TIME_MINUTES + " = ?",
                    new String[]{accountId, studentId, String.valueOf(startTimeMinutes)},
                    null, null, ScheduleEntry.COLUMN_LAST_MODIFIED + " DESC", "1")) {
                if (sc2.moveToFirst()) {
                    billingModel = sc2.getString(0);
                    feeMinor = sc2.getInt(1);
                }
            }
        }

        // 5. Fallback to fee_agreements for student
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
                }
            }

            String[] resolved = resolveBillingForOccurrence(db, accountId, studentId, scheduleId, scheduleGroupId, startMin, occDate);
            String billingModel = resolved[0];
            int resolvedFee = Integer.parseInt(resolved[1]);

            String finalDecision = record.getChargeDecision();
            int finalFeeMinor = record.getApplicableFeeMinor() > 0 ? record.getApplicableFeeMinor() : resolvedFee;

            if ("MONTHLY".equals(billingModel)) {
                if (AttendanceRecord.DECISION_CHARGE.equals(finalDecision)) {
                    finalDecision = AttendanceRecord.DECISION_INCLUDED_MONTHLY;
                }
                finalFeeMinor = 0;
            } else if (!AttendanceRecord.DECISION_CHARGE.equals(finalDecision)) {
                finalFeeMinor = 0;
            }

            String makeupOccId = record.getMakeupOccurrenceId();
            if (createMakeup && makeupDateMillis > 0 && studentId != null) {
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

            String existingId = null;
            long createdAt = modTime;
            try (Cursor ec = db.query(AttendanceEntry.TABLE_NAME,
                    new String[]{AttendanceEntry.COLUMN_ID, AttendanceEntry.COLUMN_CREATED_AT},
                    AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                            AttendanceEntry.COLUMN_ID + " = ? OR " +
                            AttendanceEntry.COLUMN_OCCURRENCE_ID + " = ?)",
                    new String[]{accountId, record.getId(), occId}, null, null, null)) {
                if (ec.moveToFirst()) {
                    existingId = ec.getString(0);
                    if (!ec.isNull(1) && ec.getLong(1) > 0) {
                        createdAt = ec.getLong(1);
                    }
                }
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
            values.put(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID, record.getBilledInvoiceItemId());
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
            db.setTransactionSuccessful();
            return ok ? 1 : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
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
                "a." + AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + " " +
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

                if (AttendanceRecord.DECISION_CHARGE.equals(decision) && feeMinor <= 0) {
                    String[] resolved = resolveBillingForOccurrence(db, accountId, stuId, schedId, groupId, startMin, occDate);
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
                    String[] resolved = resolveBillingForOccurrence(db, accountId, stuId, schedId, groupId, startMin, occDate);
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
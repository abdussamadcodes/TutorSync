package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.ScheduleFeeHistoryEntry;

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
     * Resolves [billingModel, feeAmountMinor] for an occurrence using a 4-step fallback:
     * 1. schedule_fee_history effective on the day of occDate (end-of-day inclusive)
     * 2. Latest schedule_fee_history for scheduleGroupId
     * 3. Active recurring_schedules by scheduleId, scheduleGroupId, or (studentId + startTimeMinutes)
     * 4. Legacy fee_agreements for studentId
     */
    @NonNull
    public static String[] resolveBillingForOccurrence(
            @NonNull SQLiteDatabase db,
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
            try (Cursor hc = db.query(ScheduleFeeHistoryEntry.TABLE_NAME,
                    new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR},
                    ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? AND " +
                            ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " <= ? AND (" +
                            ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " IS NULL OR " +
                            ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " >= ?)",
                    new String[]{scheduleGroupId, String.valueOf(endOfDayMillis), String.valueOf(occDateMillis)},
                    null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " DESC", "1")) {
                if (hc.moveToFirst()) {
                    billingModel = hc.getString(0);
                    feeMinor = hc.getInt(1);
                }
            }

            // 2. Fallback to latest schedule_fee_history for this group
            if (billingModel == null || feeMinor <= 0) {
                try (Cursor hc = db.query(ScheduleFeeHistoryEntry.TABLE_NAME,
                        new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR},
                        ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ?",
                        new String[]{scheduleGroupId},
                        null, null, ScheduleFeeHistoryEntry.COLUMN_CREATED_AT + " DESC", "1")) {
                    if (hc.moveToFirst()) {
                        billingModel = hc.getString(0);
                        feeMinor = hc.getInt(1);
                    }
                }
            }
        }

        // 3. Fallback to recurring_schedules (by schedule_id, schedule_group_id, or student_id)
        if ((billingModel == null || feeMinor <= 0) && scheduleId != null) {
            try (Cursor sc = db.query(ScheduleEntry.TABLE_NAME,
                    new String[]{ScheduleEntry.COLUMN_BILLING_MODEL, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR},
                    ScheduleEntry.COLUMN_ID + " = ?", new String[]{scheduleId}, null, null, null, "1")) {
                if (sc.moveToFirst()) {
                    billingModel = sc.getString(0);
                    feeMinor = sc.getInt(1);
                }
            }
        }

        if ((billingModel == null || feeMinor <= 0) && scheduleGroupId != null) {
            try (Cursor sc = db.query(ScheduleEntry.TABLE_NAME,
                    new String[]{ScheduleEntry.COLUMN_BILLING_MODEL, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR},
                    ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + " = ?", new String[]{scheduleGroupId}, null, null, null, "1")) {
                if (sc.moveToFirst()) {
                    billingModel = sc.getString(0);
                    feeMinor = sc.getInt(1);
                }
            }
        }

        if ((billingModel == null || feeMinor <= 0) && studentId != null) {
            try (Cursor sc = db.query(ScheduleEntry.TABLE_NAME,
                    new String[]{ScheduleEntry.COLUMN_BILLING_MODEL, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR},
                    ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " + ScheduleEntry.COLUMN_START_TIME_MINUTES + " = ?",
                    new String[]{studentId, String.valueOf(startTimeMinutes)},
                    null, null, ScheduleEntry.COLUMN_UPDATED_AT + " DESC", "1")) {
                if (sc.moveToFirst()) {
                    billingModel = sc.getString(0);
                    feeMinor = sc.getInt(1);
                }
            }
        }

        if ((billingModel == null || feeMinor <= 0) && studentId != null) {
            try (Cursor sc = db.query(ScheduleEntry.TABLE_NAME,
                    new String[]{ScheduleEntry.COLUMN_BILLING_MODEL, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR},
                    ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " + ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR + " > 0",
                    new String[]{studentId},
                    null, null, ScheduleEntry.COLUMN_UPDATED_AT + " DESC", "1")) {
                if (sc.moveToFirst()) {
                    billingModel = sc.getString(0);
                    feeMinor = sc.getInt(1);
                }
            }
        }

        // 4. Fallback to legacy student fee_agreements
        if ((billingModel == null || feeMinor <= 0) && studentId != null) {
            try (Cursor fc = db.query(FeeAgreementEntry.TABLE_NAME,
                    new String[]{FeeAgreementEntry.COLUMN_FEE_TYPE, FeeAgreementEntry.COLUMN_AMOUNT_MINOR},
                    FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?", new String[]{studentId}, null, null, null, "1")) {
                if (fc.moveToFirst()) {
                    billingModel = fc.getString(0);
                    feeMinor = fc.getInt(1);
                }
            }
        }

        if (billingModel == null) billingModel = "MONTHLY";
        return new String[]{billingModel, String.valueOf(feeMinor)};
    }

    public int recordAttendanceWithBilling(
            @NonNull AttendanceRecord record,
            boolean scheduleMakeupClass,
            long makeupDateMillis,
            int makeupStartMinutes) {

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            String scheduleGroupId = null;
            String studentId = null;
            String subjectId = null;
            String scheduleId = null;
            long occDate = System.currentTimeMillis();
            int startTimeMinutes = 960;
            int duration = 60;

            try (Cursor oc = db.query(OccurrenceEntry.TABLE_NAME, null,
                    OccurrenceEntry.COLUMN_ID + " = ?", new String[]{record.getOccurrenceId()}, null, null, null)) {
                if (oc.moveToFirst()) {
                    studentId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_STUDENT_ID));
                    subjectId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SUBJECT_ID));
                    scheduleId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SCHEDULE_ID));
                    scheduleGroupId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID));
                    occDate = oc.getLong(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DATE));
                    startTimeMinutes = oc.getInt(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_START_TIME_MINUTES));
                    duration = oc.getInt(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DURATION_MINUTES));
                }
            }

            String[] resolved = resolveBillingForOccurrence(db, studentId, scheduleId, scheduleGroupId, startTimeMinutes, occDate);
            String billingModel = resolved[0];
            int applicableFeeMinor = record.getApplicableFeeMinor() > 0
                    ? record.getApplicableFeeMinor() : Integer.parseInt(resolved[1]);

            String finalDecision = record.getChargeDecision();
            String status = record.getStatus();

            if ("MONTHLY".equals(billingModel)) {
                finalDecision = AttendanceRecord.DECISION_INCLUDED_MONTHLY;
                applicableFeeMinor = 0;
            } else {
                // PER_CLASS rules
                if (AttendanceRecord.DECISION_WAIVE.equals(finalDecision)) {
                    applicableFeeMinor = 0;
                } else if (AttendanceRecord.STATUS_PRESENT.equals(status) || AttendanceRecord.STATUS_STUDENT_ABSENT.equals(status)) {
                    finalDecision = AttendanceRecord.DECISION_CHARGE;
                } else if (AttendanceRecord.STATUS_RESCHEDULED.equals(status) || scheduleMakeupClass) {
                    finalDecision = AttendanceRecord.DECISION_WAIVE;
                    applicableFeeMinor = 0;
                }
            }

            String makeupOccId = record.getMakeupOccurrenceId();
            if (scheduleMakeupClass && makeupDateMillis > 0 && studentId != null) {
                makeupOccId = UUID.randomUUID().toString();
                ContentValues occVal = new ContentValues();
                occVal.put(OccurrenceEntry.COLUMN_ID, makeupOccId);
                occVal.put(OccurrenceEntry.COLUMN_ACCOUNT_ID, record.getAccountId());
                occVal.put(OccurrenceEntry.COLUMN_STUDENT_ID, studentId);
                occVal.put(OccurrenceEntry.COLUMN_SUBJECT_ID, subjectId);
                occVal.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, scheduleId);
                occVal.put(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, scheduleGroupId);
                occVal.put(OccurrenceEntry.COLUMN_DATE, makeupDateMillis);
                occVal.put(OccurrenceEntry.COLUMN_START_TIME_MINUTES, makeupStartMinutes);
                occVal.put(OccurrenceEntry.COLUMN_DURATION_MINUTES, duration);
                occVal.put(OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID, record.getOccurrenceId());
                db.insert(OccurrenceEntry.TABLE_NAME, null, occVal);
            }

            ContentValues values = new ContentValues();
            values.put(AttendanceEntry.COLUMN_ID, record.getId());
            values.put(AttendanceEntry.COLUMN_ACCOUNT_ID, record.getAccountId());
            values.put(AttendanceEntry.COLUMN_OCCURRENCE_ID, record.getOccurrenceId());
            values.put(AttendanceEntry.COLUMN_STATUS, status);
            values.put(AttendanceEntry.COLUMN_CHARGE_DECISION, finalDecision);
            values.put(AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, applicableFeeMinor);
            values.put(AttendanceEntry.COLUMN_NEEDS_RESCHEDULE, record.isNeedsReschedule() ? 1 : 0);
            values.put(AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID, makeupOccId);
            values.put(AttendanceEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            long rowId = db.insertWithOnConflict(AttendanceEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
            return rowId != -1 ? 1 : 0;
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

    /**
     * Returns all attended / chargeable per-class records for a student (both unbilled and billed)
     * so the tutor can see immediate per-class charges in the Student Fee Hub.
     */
    @NonNull
    public List<UnbilledClassCharge> getPerClassAttendanceChargesForStudent(@NonNull String accountId, @NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<UnbilledClassCharge> list = new ArrayList<>();

        // First heal any misclassified per-class attendance rows for this student
        healPerClassAttendanceRecords(db, accountId, studentId);

        String sql = "SELECT a." + AttendanceEntry.COLUMN_ID + ", o." + OccurrenceEntry.COLUMN_ID + ", " +
                "o." + OccurrenceEntry.COLUMN_DATE + ", o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + ", " +
                "a." + AttendanceEntry.COLUMN_STATUS + ", a." + AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + ", " +
                "a." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " " +
                "FROM " + AttendanceEntry.TABLE_NAME + " a " +
                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
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

    /**
     * Auto-heals any attendance records where the student's schedule is PER_CLASS but the attendance
     * record was previously saved with 0 fee or INCLUDED_IN_MONTHLY due to midnight timestamp comparison.
     */
    public static void healPerClassAttendanceRecords(@NonNull SQLiteDatabase db, @NonNull String accountId, @Nullable String studentFilterId) {
        String whereStudent = studentFilterId != null ? " AND o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = '" + studentFilterId + "' " : "";
        String sql = "SELECT a." + AttendanceEntry.COLUMN_ID + ", o." + OccurrenceEntry.COLUMN_STUDENT_ID + ", " +
                "o." + OccurrenceEntry.COLUMN_SCHEDULE_ID + ", o." + OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID + ", " +
                "o." + OccurrenceEntry.COLUMN_START_TIME_MINUTES + ", o." + OccurrenceEntry.COLUMN_DATE + ", " +
                "a." + AttendanceEntry.COLUMN_STATUS + ", a." + AttendanceEntry.COLUMN_CHARGE_DECISION + ", " +
                "a." + AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + " " +
                "FROM " + AttendanceEntry.TABLE_NAME + " a " +
                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                "WHERE o." + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? " +
                "AND a." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " IS NULL" + whereStudent;

        try (Cursor c = db.rawQuery(sql, new String[]{accountId})) {
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

                String[] resolved = resolveBillingForOccurrence(db, stuId, schedId, groupId, startMin, occDate);
                String model = resolved[0];
                int resolvedFee = Integer.parseInt(resolved[1]);

                if ("PER_CLASS".equals(model) && resolvedFee > 0) {
                    boolean isAttendable = "PRESENT".equals(status) || "STUDENT_ABSENT".equals(status);
                    if (isAttendable && ("INCLUDED_IN_MONTHLY".equals(decision) || "PENDING".equals(decision) || feeMinor <= 0)) {
                        ContentValues up = new ContentValues();
                        up.put(AttendanceEntry.COLUMN_CHARGE_DECISION, AttendanceRecord.DECISION_CHARGE);
                        up.put(AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, resolvedFee);
                        db.update(AttendanceEntry.TABLE_NAME, up, AttendanceEntry.COLUMN_ID + " = ?", new String[]{attId});
                    }
                }
            }
        }
    }

    public int getPresentCountForStudentThisMonth(@NonNull String studentId) {
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
                "INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID + " " +
                "WHERE o." + OccurrenceEntry.COLUMN_STUDENT_ID + " = ? " +
                "AND a." + AttendanceEntry.COLUMN_CHARGE_DECISION + " = 'CHARGE' " +
                "AND o." + OccurrenceEntry.COLUMN_DATE + " >= ? " +
                "AND o." + OccurrenceEntry.COLUMN_DATE + " < ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{studentId, String.valueOf(startOfMonth), String.valueOf(endOfMonth)})) {
            if (cursor.moveToFirst()) {
                chargeableCount = cursor.getInt(0);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return chargeableCount;
    }
}
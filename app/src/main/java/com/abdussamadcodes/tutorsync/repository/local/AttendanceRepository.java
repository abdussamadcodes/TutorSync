package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AttendanceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.ScheduleEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.ScheduleFeeHistoryEntry;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import java.util.Calendar;
import java.util.UUID;

public class AttendanceRepository {

    /**
     * Records attendance and enforces billing rules for both MONTHLY and PER_CLASS schedules:
     * - Fixed Monthly: tracks attendance/rescheduling independently; marks decision INCLUDED_IN_MONTHLY.
     * - Per-Class:
     *   - PRESENT or STUDENT_ABSENT (tutor present): defaults to CHARGE at the schedule's historical rate.
     *   - CANCELLED_BY_STUDENT or CANCELLED_BY_TUTOR: uses tutor's explicit decision (CHARGE or WAIVE).
     *   - RESCHEDULED / TUTOR_ABSENT: marks WAIVE on original occurrence and optionally creates makeup class.
     */
    public int recordAttendanceWithBilling(
            @NonNull AttendanceRecord record,
            boolean scheduleMakeupClass,
            long makeupDateMillis,
            int makeupStartMinutes) {

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            // 1. Lookup occurrence and schedule billing settings
            String scheduleGroupId = null;
            String studentId = null;
            String subjectId = null;
            String scheduleId = null;
            long occDate = System.currentTimeMillis();
            int duration = 60;

            try (Cursor oc = db.query(OccurrenceEntry.TABLE_NAME, null,
                    OccurrenceEntry.COLUMN_ID + " = ?", new String[]{record.getOccurrenceId()}, null, null, null)) {
                if (oc.moveToFirst()) {
                    studentId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_STUDENT_ID));
                    subjectId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SUBJECT_ID));
                    scheduleId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SCHEDULE_ID));
                    scheduleGroupId = oc.getString(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID));
                    occDate = oc.getLong(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DATE));
                    duration = oc.getInt(oc.getColumnIndexOrThrow(OccurrenceEntry.COLUMN_DURATION_MINUTES));
                }
            }

            String billingModel = "MONTHLY";
            int applicableFeeMinor = record.getApplicableFeeMinor();

            if (scheduleGroupId != null) {
                // Resolve historical rate effective at occDate
                try (Cursor hc = db.query(ScheduleFeeHistoryEntry.TABLE_NAME,
                        new String[]{ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR},
                        ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? AND " +
                                ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " <= ? AND (" +
                                ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " IS NULL OR " +
                                ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " >= ?)",
                        new String[]{scheduleGroupId, String.valueOf(occDate), String.valueOf(occDate)},
                        null, null, ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " DESC", "1")) {
                    if (hc.moveToFirst()) {
                        billingModel = hc.getString(0);
                        applicableFeeMinor = hc.getInt(1);
                    }
                }
            } else if (scheduleId != null) {
                try (Cursor sc = db.query(ScheduleEntry.TABLE_NAME,
                        new String[]{ScheduleEntry.COLUMN_BILLING_MODEL, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR},
                        ScheduleEntry.COLUMN_ID + " = ?", new String[]{scheduleId}, null, null, null)) {
                    if (sc.moveToFirst()) {
                        billingModel = sc.getString(0);
                        applicableFeeMinor = sc.getInt(1);
                    }
                }
            }

            // 2. Determine final charge decision based on billing model & rules
            String finalDecision = record.getChargeDecision();
            String status = record.getStatus();

            if ("MONTHLY".equals(billingModel)) {
                finalDecision = AttendanceRecord.DECISION_INCLUDED_MONTHLY;
                applicableFeeMinor = 0; // Billed on recurring monthly schedule cycle, not per occurrence
            } else {
                // PER_CLASS rules
                if (AttendanceRecord.STATUS_PRESENT.equals(status) || AttendanceRecord.STATUS_STUDENT_ABSENT.equals(status)) {
                    // Tutor is present -> charge class fee even if student is absent
                    if (!AttendanceRecord.DECISION_WAIVE.equals(finalDecision)) {
                        finalDecision = AttendanceRecord.DECISION_CHARGE;
                    }
                } else if (AttendanceRecord.STATUS_RESCHEDULED.equals(status) || scheduleMakeupClass) {
                    // Prevent duplicate charges when rescheduled
                    finalDecision = AttendanceRecord.DECISION_WAIVE;
                }
            }

            // 3. Create makeup occurrence if requested
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

            // 4. Upsert attendance record (avoid overwriting billed_invoice_item_id if already billed)
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

        String query = "SELECT COUNT(*) FROM " + AttendanceEntry.TABLE_NAME + " a " +
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
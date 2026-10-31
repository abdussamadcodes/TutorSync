package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ScheduleRepository {

    public boolean insertScheduleWithFee(
            @NonNull RecurringSchedule schedule,
            @NonNull String scheduleGroupId,
            @NonNull String billingModel,
            int feeAmountMinor,
            int anchorDay) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long now = System.currentTimeMillis();
            long modTime = schedule.getUpdatedAt() > 0 ? schedule.getUpdatedAt() : now;

            ContentValues values = new ContentValues();
            values.put(ScheduleEntry.COLUMN_ID, TutorSyncContract.ensureValidUuid(schedule.getId()));
            values.put(ScheduleEntry.COLUMN_ACCOUNT_ID, schedule.getAccountId());
            values.put(ScheduleEntry.COLUMN_STUDENT_ID, schedule.getStudentId());
            values.put(ScheduleEntry.COLUMN_SUBJECT_ID, schedule.getSubjectId());
            values.put(ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID, scheduleGroupId);
            values.put(ScheduleEntry.COLUMN_DAY_OF_WEEK, schedule.getDayOfWeek());
            values.put(ScheduleEntry.COLUMN_START_TIME_MINUTES, schedule.getStartTimeMinutes());
            values.put(ScheduleEntry.COLUMN_DURATION_MINUTES, schedule.getDurationMinutes());
            values.put(ScheduleEntry.COLUMN_START_DATE, schedule.getStartDate());
            values.put(ScheduleEntry.COLUMN_END_DATE, schedule.getEndDate());
            values.put(ScheduleEntry.COLUMN_BILLING_MODEL, billingModel);
            values.put(ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, feeAmountMinor);
            values.put(ScheduleEntry.COLUMN_ANCHOR_DAY, anchorDay);
            values.put(ScheduleEntry.COLUMN_IS_ACTIVE, 1);
            values.put(ScheduleEntry.COLUMN_CREATED_AT, modTime);
            values.put(ScheduleEntry.COLUMN_UPDATED_AT, modTime);
            values.put(ScheduleEntry.COLUMN_LAST_MODIFIED, modTime);

            boolean inserted = db.insert(ScheduleEntry.TABLE_NAME, null, values) != -1;
            if (inserted && feeAmountMinor > 0) {
                ensureInitialFeeHistoryExists(db, schedule.getAccountId(), schedule.getStudentId(),
                        scheduleGroupId, billingModel, feeAmountMinor, anchorDay, schedule.getStartDate(), modTime);
            }
            return inserted;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private void ensureInitialFeeHistoryExists(SQLiteDatabase db, String accountId, String studentId,
                                               String groupId, String billingModel, int feeMinor,
                                               int anchorDay, long effectiveFrom, long now) {
        try (Cursor c = db.query(ScheduleFeeHistoryEntry.TABLE_NAME, new String[]{ScheduleFeeHistoryEntry.COLUMN_ID},
                ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ?",
                new String[]{accountId, groupId}, null, null, null)) {
            if (!c.moveToFirst()) {
                ContentValues hv = new ContentValues();
                hv.put(ScheduleFeeHistoryEntry.COLUMN_ID, UUID.randomUUID().toString());
                hv.put(ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID, accountId);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_STUDENT_ID, studentId);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, billingModel);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR, feeMinor);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_ANCHOR_DAY, anchorDay);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM, effectiveFrom);
                hv.putNull(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_CREATED_AT, now);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_UPDATED_AT, now);
                hv.put(ScheduleFeeHistoryEntry.COLUMN_LAST_MODIFIED, now);
                db.insert(ScheduleFeeHistoryEntry.TABLE_NAME, null, hv);
            }
        }
    }

    public boolean insertSchedule(@NonNull RecurringSchedule schedule) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(schedule.getStartDate());
        String groupId = schedule.getScheduleGroupId() != null ? schedule.getScheduleGroupId() : UUID.randomUUID().toString();
        return insertScheduleWithFee(schedule, groupId, schedule.getBillingModel(),
                schedule.getFeeAmountMinor(), cal.get(Calendar.DAY_OF_MONTH));
    }

    public boolean updateScheduleGroupFee(
            @NonNull String accountId,
            @NonNull String studentId,
            @NonNull String scheduleGroupId,
            @NonNull String newBillingModel,
            int newFeeAmountMinor,
            int anchorDay) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();

            ContentValues closeHist = new ContentValues();
            closeHist.put(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO, now - 1);
            closeHist.put(ScheduleFeeHistoryEntry.COLUMN_UPDATED_AT, now);
            closeHist.put(ScheduleFeeHistoryEntry.COLUMN_LAST_MODIFIED, now);
            db.update(ScheduleFeeHistoryEntry.TABLE_NAME, closeHist,
                    ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " = ? AND " +
                            ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " IS NULL",
                    new String[]{accountId, scheduleGroupId});

            ContentValues newHist = new ContentValues();
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_ID, UUID.randomUUID().toString());
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID, accountId);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_STUDENT_ID, studentId);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID, scheduleGroupId);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, newBillingModel);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR, newFeeAmountMinor);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_ANCHOR_DAY, anchorDay);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM, now);
            newHist.putNull(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_CREATED_AT, now);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_UPDATED_AT, now);
            newHist.put(ScheduleFeeHistoryEntry.COLUMN_LAST_MODIFIED, now);
            db.insertOrThrow(ScheduleFeeHistoryEntry.TABLE_NAME, null, newHist);

            ContentValues sv = new ContentValues();
            sv.put(ScheduleEntry.COLUMN_BILLING_MODEL, newBillingModel);
            sv.put(ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, newFeeAmountMinor);
            sv.put(ScheduleEntry.COLUMN_ANCHOR_DAY, anchorDay);
            sv.put(ScheduleEntry.COLUMN_UPDATED_AT, now);
            sv.put(ScheduleEntry.COLUMN_LAST_MODIFIED, now);
            db.update(ScheduleEntry.TABLE_NAME, sv,
                    ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + " = ?",
                    new String[]{accountId, scheduleGroupId});

            InvoiceRepository.logAudit(db, accountId, studentId, "SCHEDULE_FEE", scheduleGroupId, "FEE_UPDATED",
                    String.format(Locale.US, "Updated schedule fee to %s ৳%.2f", newBillingModel, newFeeAmountMinor / 100.0));

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteScheduleSafely(@NonNull String accountId, @NonNull String scheduleId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long now = System.currentTimeMillis();
            ContentValues occValues = new ContentValues();
            occValues.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, (String) null);
            occValues.put(OccurrenceEntry.COLUMN_UPDATED_AT, now);
            occValues.put(OccurrenceEntry.COLUMN_LAST_MODIFIED, now);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            db.update(OccurrenceEntry.TABLE_NAME, occValues,
                    OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                            OccurrenceEntry.COLUMN_SCHEDULE_ID + " = ? AND " +
                            OccurrenceEntry.COLUMN_DATE + " < ?",
                    new String[]{accountId, scheduleId, String.valueOf(cal.getTimeInMillis())});

            return db.delete(ScheduleEntry.TABLE_NAME,
                    ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, scheduleId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteScheduleSafely(@NonNull String scheduleId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long now = System.currentTimeMillis();
            ContentValues occValues = new ContentValues();
            occValues.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, (String) null);
            occValues.put(OccurrenceEntry.COLUMN_UPDATED_AT, now);
            occValues.put(OccurrenceEntry.COLUMN_LAST_MODIFIED, now);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            db.update(OccurrenceEntry.TABLE_NAME, occValues,
                    OccurrenceEntry.COLUMN_SCHEDULE_ID + " = ? AND " + OccurrenceEntry.COLUMN_DATE + " < ?",
                    new String[]{scheduleId, String.valueOf(cal.getTimeInMillis())});

            return db.delete(ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_ID + " = ?", new String[]{scheduleId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<RecurringSchedule> getSchedulesForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<RecurringSchedule> schedules = new ArrayList<>();
        String selection = ScheduleEntry.COLUMN_ACCOUNT_ID + " = ?";
        try (Cursor cursor = db.query(ScheduleEntry.TABLE_NAME, null, selection,
                new String[]{accountId}, null, null, ScheduleEntry.COLUMN_DAY_OF_WEEK + " ASC")) {
            while (cursor.moveToNext()) {
                long lastMod = 0L;
                int lastModIdx = cursor.getColumnIndex(ScheduleEntry.COLUMN_LAST_MODIFIED);
                if (lastModIdx != -1 && !cursor.isNull(lastModIdx)) {
                    lastMod = cursor.getLong(lastModIdx);
                }
                if (lastMod <= 0L) {
                    int upIdx = cursor.getColumnIndex(ScheduleEntry.COLUMN_UPDATED_AT);
                    if (upIdx != -1 && !cursor.isNull(upIdx)) {
                        lastMod = cursor.getLong(upIdx);
                    }
                }
                schedules.add(new RecurringSchedule(
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_STUDENT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_SUBJECT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_DAY_OF_WEEK)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_START_TIME_MINUTES)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_DURATION_MINUTES)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_START_DATE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_END_DATE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_BILLING_MODEL)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(ScheduleEntry.COLUMN_ANCHOR_DAY)),
                        lastMod
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return schedules;
    }
}
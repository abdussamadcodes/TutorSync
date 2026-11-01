package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ScheduleRepository {

    private static final class ExistingScheduleRow {
        final String id;
        final String studentId;
        final String subjectId;
        final String scheduleGroupId;
        final int dayOfWeek;
        final long startDate;
        final long createdAt;

        ExistingScheduleRow(String id, String studentId, String subjectId, String scheduleGroupId,
                            int dayOfWeek, long startDate, long createdAt) {
            this.id = id;
            this.studentId = studentId;
            this.subjectId = subjectId;
            this.scheduleGroupId = scheduleGroupId;
            this.dayOfWeek = dayOfWeek;
            this.startDate = startDate;
            this.createdAt = createdAt;
        }
    }

    private static final class TargetScheduleSlot {
        final String studentId;
        final String subjectId;
        final int dayOfWeek;
        String assignedScheduleId;
        ExistingScheduleRow matchedExisting;

        TargetScheduleSlot(String studentId, String subjectId, int dayOfWeek) {
            this.studentId = studentId;
            this.subjectId = subjectId;
            this.dayOfWeek = dayOfWeek;
        }
    }

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
            updateScheduleGroupFeeInternal(db, accountId, studentId, scheduleGroupId,
                    newBillingModel, newFeeAmountMinor, anchorDay, System.currentTimeMillis());
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private void updateScheduleGroupFeeInternal(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull String studentId,
            @NonNull String scheduleGroupId,
            @NonNull String newBillingModel,
            int newFeeAmountMinor,
            int anchorDay,
            long now) {
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
    }

    /**
     * Atomically creates or edits a schedule group inside a single SQLite transaction.
     * Validates all inputs before making any changes, preserves existing schedule UUIDs when editing,
     * and preserves all historical occurrences (attendance, lessons, billing).
     */
    public boolean saveScheduleFormAtomically(
            @NonNull String accountId,
            boolean isEditMode,
            @Nullable String editScheduleGroupId,
            @Nullable List<String> editScheduleIds,
            @NonNull List<String> studentIds,
            @NonNull List<String> subjectIds,
            boolean isRecurring,
            @Nullable List<Integer> selectedDays,
            long selectedDateMillis,
            int startTimeMinutes,
            int durationMinutes,
            @NonNull String billingModel,
            int feeMinor,
            int anchorDay) {

        // 1. Validate inputs strictly before opening transaction or modifying any existing rows
        if (accountId.trim().isEmpty()) return false;
        if (studentIds.isEmpty() || subjectIds.isEmpty()) return false;
        if (startTimeMinutes < 0 || startTimeMinutes >= 1440 || durationMinutes <= 0) return false;
        if (isRecurring) {
            if (selectedDays == null || selectedDays.isEmpty()) return false;
        } else {
            if (selectedDateMillis <= 0L) return false;
        }

        Calendar startOfTodayCal = Calendar.getInstance();
        startOfTodayCal.set(Calendar.HOUR_OF_DAY, 0);
        startOfTodayCal.set(Calendar.MINUTE, 0);
        startOfTodayCal.set(Calendar.SECOND, 0);
        startOfTodayCal.set(Calendar.MILLISECOND, 0);
        long startOfTodayMillis = startOfTodayCal.getTimeInMillis();
        long now = System.currentTimeMillis();
        int effectiveAnchorDay = anchorDay > 0 ? anchorDay : startOfTodayCal.get(Calendar.DAY_OF_MONTH);

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            // Load existing schedule rows if editing
            Map<String, ExistingScheduleRow> existingById = new LinkedHashMap<>();
            if (isEditMode) {
                if (editScheduleIds != null && !editScheduleIds.isEmpty()) {
                    for (String oldId : editScheduleIds) {
                        if (oldId == null || oldId.trim().isEmpty()) continue;
                        ExistingScheduleRow row = queryExistingScheduleRow(db, accountId, oldId);
                        if (row != null) {
                            existingById.put(row.id, row);
                        }
                    }
                }
                if (existingById.isEmpty() && editScheduleGroupId != null && !editScheduleGroupId.trim().isEmpty()) {
                    try (Cursor c = db.query(ScheduleEntry.TABLE_NAME,
                            new String[]{
                                    ScheduleEntry.COLUMN_ID,
                                    ScheduleEntry.COLUMN_STUDENT_ID,
                                    ScheduleEntry.COLUMN_SUBJECT_ID,
                                    ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID,
                                    ScheduleEntry.COLUMN_DAY_OF_WEEK,
                                    ScheduleEntry.COLUMN_START_DATE,
                                    ScheduleEntry.COLUMN_CREATED_AT
                            },
                            ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + " = ?",
                            new String[]{accountId, editScheduleGroupId},
                            null, null, ScheduleEntry.COLUMN_CREATED_AT + " ASC")) {
                        while (c.moveToNext()) {
                            ExistingScheduleRow row = new ExistingScheduleRow(
                                    c.getString(0), c.getString(1), c.getString(2),
                                    c.getString(3), c.getInt(4), c.getLong(5), c.getLong(6));
                            existingById.put(row.id, row);
                        }
                    }
                }
            }

            Set<String> claimedExistingIds = new HashSet<>();

            for (String studentId : studentIds) {
                String groupId = (isEditMode && editScheduleGroupId != null && !editScheduleGroupId.trim().isEmpty())
                        ? editScheduleGroupId
                        : UUID.randomUUID().toString();

                if (feeMinor > 0) {
                    updateScheduleGroupFeeInternal(db, accountId, studentId, groupId,
                            billingModel, feeMinor, effectiveAnchorDay, now);
                    upsertLegacyFeeAgreementInternal(db, accountId, studentId, billingModel, feeMinor, now);
                }

                if (isRecurring) {
                    List<TargetScheduleSlot> slots = new ArrayList<>();
                    for (String subjectId : subjectIds) {
                        for (int dayOfWeek : selectedDays) {
                            slots.add(new TargetScheduleSlot(studentId, subjectId, dayOfWeek));
                        }
                    }

                    // Pass 1: Exact match on (studentId, subjectId, dayOfWeek) -> preserve exact UUID
                    for (TargetScheduleSlot slot : slots) {
                        for (ExistingScheduleRow existing : existingById.values()) {
                            if (!claimedExistingIds.contains(existing.id)
                                    && existing.studentId.equals(slot.studentId)
                                    && existing.subjectId.equals(slot.subjectId)
                                    && existing.dayOfWeek == slot.dayOfWeek) {
                                slot.assignedScheduleId = existing.id;
                                slot.matchedExisting = existing;
                                claimedExistingIds.add(existing.id);
                                break;
                            }
                        }
                    }

                    // Pass 2: Same (studentId, subjectId) -> preserve UUID when dayOfWeek is changed
                    for (TargetScheduleSlot slot : slots) {
                        if (slot.assignedScheduleId != null) continue;
                        for (ExistingScheduleRow existing : existingById.values()) {
                            if (!claimedExistingIds.contains(existing.id)
                                    && existing.studentId.equals(slot.studentId)
                                    && existing.subjectId.equals(slot.subjectId)) {
                                slot.assignedScheduleId = existing.id;
                                slot.matchedExisting = existing;
                                claimedExistingIds.add(existing.id);
                                break;
                            }
                        }
                    }

                    // Pass 3: Reuse any remaining unclaimed schedule UUID from the edited group
                    for (TargetScheduleSlot slot : slots) {
                        if (slot.assignedScheduleId != null) continue;
                        for (ExistingScheduleRow existing : existingById.values()) {
                            if (!claimedExistingIds.contains(existing.id)) {
                                slot.assignedScheduleId = existing.id;
                                slot.matchedExisting = existing;
                                claimedExistingIds.add(existing.id);
                                break;
                            }
                        }
                    }

                    // Pass 4: Persist each target schedule slot (update in-place if reused, insert if new)
                    for (TargetScheduleSlot slot : slots) {
                        boolean isReused = (slot.assignedScheduleId != null && slot.matchedExisting != null);
                        String scheduleId = isReused ? slot.assignedScheduleId : UUID.randomUUID().toString();
                        long startDate = isReused && slot.matchedExisting.startDate > 0
                                ? slot.matchedExisting.startDate : startOfTodayMillis;
                        long endDate = startOfTodayMillis + 31536000000L;
                        long createdAt = isReused && slot.matchedExisting.createdAt > 0
                                ? slot.matchedExisting.createdAt : now;

                        ContentValues sv = new ContentValues();
                        sv.put(ScheduleEntry.COLUMN_STUDENT_ID, slot.studentId);
                        sv.put(ScheduleEntry.COLUMN_SUBJECT_ID, slot.subjectId);
                        sv.put(ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
                        sv.put(ScheduleEntry.COLUMN_DAY_OF_WEEK, slot.dayOfWeek);
                        sv.put(ScheduleEntry.COLUMN_START_TIME_MINUTES, startTimeMinutes);
                        sv.put(ScheduleEntry.COLUMN_DURATION_MINUTES, durationMinutes);
                        sv.put(ScheduleEntry.COLUMN_START_DATE, startDate);
                        sv.put(ScheduleEntry.COLUMN_END_DATE, endDate);
                        sv.put(ScheduleEntry.COLUMN_BILLING_MODEL, billingModel);
                        sv.put(ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, feeMinor);
                        sv.put(ScheduleEntry.COLUMN_ANCHOR_DAY, effectiveAnchorDay);
                        sv.put(ScheduleEntry.COLUMN_IS_ACTIVE, 1);
                        sv.put(ScheduleEntry.COLUMN_UPDATED_AT, now);
                        sv.put(ScheduleEntry.COLUMN_LAST_MODIFIED, now);

                        if (isReused) {
                            // Detach any past or recorded occurrences if student/subject changed on this reused UUID
                            if (!slot.matchedExisting.studentId.equals(slot.studentId)
                                    || !slot.matchedExisting.subjectId.equals(slot.subjectId)) {
                                detachPastAndRecordedOccurrencesForSchedule(db, accountId, scheduleId, startOfTodayMillis, now);
                            }
                            int updated = db.update(ScheduleEntry.TABLE_NAME, sv,
                                    ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleEntry.COLUMN_ID + " = ?",
                                    new String[]{accountId, scheduleId});
                            if (updated <= 0) {
                                throw new IllegalStateException("Failed to update schedule " + scheduleId);
                            }
                            // Remove only unrecorded future occurrences before regenerating 30-day window
                            deleteUnrecordedFutureOccurrencesForSchedule(db, accountId, scheduleId, startOfTodayMillis);
                        } else {
                            sv.put(ScheduleEntry.COLUMN_ID, scheduleId);
                            sv.put(ScheduleEntry.COLUMN_ACCOUNT_ID, accountId);
                            sv.put(ScheduleEntry.COLUMN_CREATED_AT, createdAt);
                            if (db.insertOrThrow(ScheduleEntry.TABLE_NAME, null, sv) == -1) {
                                throw new IllegalStateException("Failed to insert schedule " + scheduleId);
                            }
                        }

                        if (feeMinor > 0) {
                            ensureInitialFeeHistoryExists(db, accountId, slot.studentId,
                                    groupId, billingModel, feeMinor, effectiveAnchorDay, startDate, now);
                        }

                        generateOccurrencesFor30DaysInternal(
                                db, accountId, slot.studentId, slot.subjectId,
                                scheduleId, groupId, slot.dayOfWeek,
                                startTimeMinutes, durationMinutes, startOfTodayMillis, now);
                    }
                } else {
                    for (String subjectId : subjectIds) {
                        insertSingleOccurrenceInternal(
                                db, accountId, studentId, subjectId, null, groupId,
                                selectedDateMillis, startTimeMinutes, durationMinutes, now);
                    }
                }

                // Link any orphaned occurrences for this student & time in this account to this groupId
                ContentValues upOcc = new ContentValues();
                upOcc.put(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
                db.update(OccurrenceEntry.TABLE_NAME, upOcc,
                        OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                OccurrenceEntry.COLUMN_STUDENT_ID + " = ? AND " +
                                OccurrenceEntry.COLUMN_START_TIME_MINUTES + " = ? AND " +
                                OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID + " IS NULL",
                        new String[]{accountId, studentId, String.valueOf(startTimeMinutes)});
            }

            // Safely remove any old schedules in the edited group that were not reused
            if (isEditMode) {
                for (ExistingScheduleRow existing : existingById.values()) {
                    if (!claimedExistingIds.contains(existing.id)) {
                        deleteScheduleSafelyInternal(db, accountId, existing.id, startOfTodayMillis, now);
                    }
                }
            }

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @Nullable
    private ExistingScheduleRow queryExistingScheduleRow(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull String scheduleId) {
        try (Cursor c = db.query(ScheduleEntry.TABLE_NAME,
                new String[]{
                        ScheduleEntry.COLUMN_ID,
                        ScheduleEntry.COLUMN_STUDENT_ID,
                        ScheduleEntry.COLUMN_SUBJECT_ID,
                        ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID,
                        ScheduleEntry.COLUMN_DAY_OF_WEEK,
                        ScheduleEntry.COLUMN_START_DATE,
                        ScheduleEntry.COLUMN_CREATED_AT
                },
                ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleEntry.COLUMN_ID + " = ?",
                new String[]{accountId, scheduleId},
                null, null, null)) {
            if (c.moveToFirst()) {
                return new ExistingScheduleRow(
                        c.getString(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getInt(4), c.getLong(5), c.getLong(6));
            }
        }
        return null;
    }

    private void upsertLegacyFeeAgreementInternal(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull String studentId,
            @NonNull String billingModel,
            int feeMinor,
            long now) {
        String existingId = null;
        long createdAt = now;
        try (Cursor c = db.query(FeeAgreementEntry.TABLE_NAME,
                new String[]{FeeAgreementEntry.COLUMN_ID, FeeAgreementEntry.COLUMN_CREATED_AT},
                FeeAgreementEntry.COLUMN_ACCOUNT_ID + " = ? AND " + FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?",
                new String[]{accountId, studentId}, null, null, null)) {
            if (c.moveToFirst()) {
                existingId = c.getString(0);
                if (!c.isNull(1) && c.getLong(1) > 0) {
                    createdAt = c.getLong(1);
                }
            }
        }

        String targetId = existingId != null ? existingId : UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put(FeeAgreementEntry.COLUMN_ID, targetId);
        values.put(FeeAgreementEntry.COLUMN_ACCOUNT_ID, accountId);
        values.put(FeeAgreementEntry.COLUMN_STUDENT_ID, studentId);
        values.put(FeeAgreementEntry.COLUMN_FEE_TYPE, billingModel);
        values.put(FeeAgreementEntry.COLUMN_AMOUNT_MINOR, feeMinor);
        values.put(FeeAgreementEntry.COLUMN_CREATED_AT, createdAt);
        values.put(FeeAgreementEntry.COLUMN_UPDATED_AT, now);
        values.put(FeeAgreementEntry.COLUMN_LAST_MODIFIED, now);

        if (existingId != null) {
            db.update(FeeAgreementEntry.TABLE_NAME, values,
                    FeeAgreementEntry.COLUMN_ACCOUNT_ID + " = ? AND " + FeeAgreementEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, existingId});
        } else {
            db.insertOrThrow(FeeAgreementEntry.TABLE_NAME, null, values);
        }
    }

    private void generateOccurrencesFor30DaysInternal(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull String studentId,
            @NonNull String subjectId,
            @NonNull String scheduleId,
            @Nullable String groupId,
            int dayOfWeek,
            int startTimeMinutes,
            int durationMinutes,
            long startOfTodayMillis,
            long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startOfTodayMillis);

        for (int i = 0; i < 30; i++) {
            if (cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek) {
                long dateMillis = cal.getTimeInMillis();
                // Skip if a preserved/recorded occurrence already exists for this schedule on this date
                boolean exists = false;
                try (Cursor c = db.query(OccurrenceEntry.TABLE_NAME,
                        new String[]{OccurrenceEntry.COLUMN_ID},
                        OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                OccurrenceEntry.COLUMN_SCHEDULE_ID + " = ? AND " +
                                OccurrenceEntry.COLUMN_DATE + " = ?",
                        new String[]{accountId, scheduleId, String.valueOf(dateMillis)},
                        null, null, null, "1")) {
                    if (c.moveToFirst()) {
                        exists = true;
                    }
                }
                if (!exists) {
                    insertSingleOccurrenceInternal(
                            db, accountId, studentId, subjectId, scheduleId, groupId,
                            dateMillis, startTimeMinutes, durationMinutes, now);
                }
            }
            cal.add(Calendar.DATE, 1);
        }
    }

    private void insertSingleOccurrenceInternal(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull String studentId,
            @Nullable String subjectId,
            @Nullable String scheduleId,
            @Nullable String groupId,
            long dateMillis,
            int startTimeMinutes,
            int durationMinutes,
            long now) {
        ContentValues values = new ContentValues();
        values.put(OccurrenceEntry.COLUMN_ID, UUID.randomUUID().toString());
        values.put(OccurrenceEntry.COLUMN_ACCOUNT_ID, accountId);
        values.put(OccurrenceEntry.COLUMN_STUDENT_ID, studentId);
        values.put(OccurrenceEntry.COLUMN_SUBJECT_ID, subjectId);
        values.put(OccurrenceEntry.COLUMN_SCHEDULE_ID, scheduleId);
        values.put(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
        values.put(OccurrenceEntry.COLUMN_DATE, dateMillis);
        values.put(OccurrenceEntry.COLUMN_START_TIME_MINUTES, startTimeMinutes);
        values.put(OccurrenceEntry.COLUMN_DURATION_MINUTES, durationMinutes);
        values.put(OccurrenceEntry.COLUMN_CREATED_AT, now);
        values.put(OccurrenceEntry.COLUMN_UPDATED_AT, now);
        values.put(OccurrenceEntry.COLUMN_LAST_MODIFIED, now);
        if (db.insertOrThrow(OccurrenceEntry.TABLE_NAME, null, values) == -1) {
            throw new IllegalStateException("Failed to insert class occurrence");
        }
    }

    public static void detachPastAndRecordedOccurrencesForSchedule(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull String scheduleId,
            long startOfTodayMillis,
            long now) {
        ContentValues occValues = new ContentValues();
        occValues.putNull(OccurrenceEntry.COLUMN_SCHEDULE_ID);
        occValues.put(OccurrenceEntry.COLUMN_UPDATED_AT, now);
        occValues.put(OccurrenceEntry.COLUMN_LAST_MODIFIED, now);

        String where = OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                OccurrenceEntry.COLUMN_SCHEDULE_ID + " = ? AND (" +
                OccurrenceEntry.COLUMN_DATE + " < ? OR " +
                OccurrenceEntry.COLUMN_ID + " IN (SELECT " + AttendanceEntry.COLUMN_OCCURRENCE_ID +
                " FROM " + AttendanceEntry.TABLE_NAME + " WHERE " + AttendanceEntry.COLUMN_ACCOUNT_ID + " = ?) OR " +
                OccurrenceEntry.COLUMN_ID + " IN (SELECT " + LessonEntry.COLUMN_OCCURRENCE_ID +
                " FROM " + LessonEntry.TABLE_NAME + " WHERE " + LessonEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                LessonEntry.COLUMN_OCCURRENCE_ID + " IS NOT NULL) OR " +
                OccurrenceEntry.COLUMN_ID + " IN (SELECT " + InvoiceItemEntry.COLUMN_OCCURRENCE_ID +
                " FROM " + InvoiceItemEntry.TABLE_NAME + " WHERE " + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " IS NOT NULL))";

        db.update(OccurrenceEntry.TABLE_NAME, occValues, where,
                new String[]{accountId, scheduleId, String.valueOf(startOfTodayMillis), accountId, accountId, accountId});
    }

    public static void deleteUnrecordedFutureOccurrencesForSchedule(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull String scheduleId,
            long startOfTodayMillis) {
        String where = OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                OccurrenceEntry.COLUMN_SCHEDULE_ID + " = ? AND " +
                OccurrenceEntry.COLUMN_DATE + " >= ? AND " +
                OccurrenceEntry.COLUMN_ID + " NOT IN (SELECT " + AttendanceEntry.COLUMN_OCCURRENCE_ID +
                " FROM " + AttendanceEntry.TABLE_NAME + " WHERE " + AttendanceEntry.COLUMN_ACCOUNT_ID + " = ?) AND " +
                OccurrenceEntry.COLUMN_ID + " NOT IN (SELECT " + LessonEntry.COLUMN_OCCURRENCE_ID +
                " FROM " + LessonEntry.TABLE_NAME + " WHERE " + LessonEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                LessonEntry.COLUMN_OCCURRENCE_ID + " IS NOT NULL) AND " +
                OccurrenceEntry.COLUMN_ID + " NOT IN (SELECT " + InvoiceItemEntry.COLUMN_OCCURRENCE_ID +
                " FROM " + InvoiceItemEntry.TABLE_NAME + " WHERE " + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " IS NOT NULL)";

        db.delete(OccurrenceEntry.TABLE_NAME, where,
                new String[]{accountId, scheduleId, String.valueOf(startOfTodayMillis), accountId, accountId, accountId});
    }

    private boolean deleteScheduleSafelyInternal(
            @NonNull SQLiteDatabase db,
            @NonNull String accountId,
            @NonNull String scheduleId,
            long startOfTodayMillis,
            long now) {
        detachPastAndRecordedOccurrencesForSchedule(db, accountId, scheduleId, startOfTodayMillis, now);
        deleteUnrecordedFutureOccurrencesForSchedule(db, accountId, scheduleId, startOfTodayMillis);
        return db.delete(ScheduleEntry.TABLE_NAME,
                ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleEntry.COLUMN_ID + " = ?",
                new String[]{accountId, scheduleId}) > 0;
    }

    public boolean deleteScheduleGroupSafely(@NonNull String accountId, @NonNull List<String> scheduleIds) {
        if (accountId.trim().isEmpty() || scheduleIds.isEmpty()) return false;
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfTodayMillis = cal.getTimeInMillis();
            long now = System.currentTimeMillis();

            boolean anyDeleted = false;
            for (String scheduleId : scheduleIds) {
                if (scheduleId == null || scheduleId.trim().isEmpty()) continue;
                if (deleteScheduleSafelyInternal(db, accountId, scheduleId, startOfTodayMillis, now)) {
                    anyDeleted = true;
                }
            }
            db.setTransactionSuccessful();
            return anyDeleted;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteScheduleSafely(@NonNull String accountId, @NonNull String scheduleId) {
        if (accountId.trim().isEmpty() || scheduleId.trim().isEmpty()) return false;
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            boolean deleted = deleteScheduleSafelyInternal(
                    db, accountId, scheduleId, cal.getTimeInMillis(), System.currentTimeMillis());
            db.setTransactionSuccessful();
            return deleted;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean deleteScheduleSafely(@NonNull String scheduleId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String accountId = null;
        try (Cursor c = db.query(ScheduleEntry.TABLE_NAME, new String[]{ScheduleEntry.COLUMN_ACCOUNT_ID},
                ScheduleEntry.COLUMN_ID + " = ?", new String[]{scheduleId}, null, null, null)) {
            if (c.moveToFirst()) {
                accountId = c.getString(0);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        if (accountId == null || accountId.isEmpty()) return false;
        return deleteScheduleSafely(accountId, scheduleId);
    }

    @NonNull
    public List<RecurringSchedule> getSchedulesForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<RecurringSchedule> schedules = new ArrayList<>();
        String selection = ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " + ScheduleEntry.COLUMN_IS_ACTIVE + " = 1";
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
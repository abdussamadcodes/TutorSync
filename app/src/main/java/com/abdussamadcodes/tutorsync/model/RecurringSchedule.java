package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;

public class RecurringSchedule {
    public static final String MODEL_MONTHLY = "MONTHLY";
    public static final String MODEL_PER_CLASS = "PER_CLASS";

    private final String id;
    private final String accountId;
    private final String studentId;
    private final String subjectId;
    private final String scheduleGroupId;
    private final int dayOfWeek;
    private final int startTimeMinutes;
    private final int durationMinutes;
    private final long startDate;
    private final long endDate;
    private final String billingModel;
    private final int feeAmountMinor;
    private final int anchorDay;
    private final long updatedAt;

    public RecurringSchedule(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                             @NonNull String subjectId, int dayOfWeek, int startTimeMinutes,
                             int durationMinutes, long startDate, long endDate) {
        this(id, accountId, studentId, subjectId, null, dayOfWeek, startTimeMinutes,
                durationMinutes, startDate, endDate, MODEL_MONTHLY, 0, 1, System.currentTimeMillis());
    }

    public RecurringSchedule(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                             @NonNull String subjectId, @Nullable String scheduleGroupId,
                             int dayOfWeek, int startTimeMinutes, int durationMinutes,
                             long startDate, long endDate, @NonNull String billingModel,
                             int feeAmountMinor, int anchorDay) {
        this(id, accountId, studentId, subjectId, scheduleGroupId, dayOfWeek, startTimeMinutes,
                durationMinutes, startDate, endDate, billingModel, feeAmountMinor, anchorDay, System.currentTimeMillis());
    }

    public RecurringSchedule(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                             @NonNull String subjectId, @Nullable String scheduleGroupId,
                             int dayOfWeek, int startTimeMinutes, int durationMinutes,
                             long startDate, long endDate, @NonNull String billingModel,
                             int feeAmountMinor, int anchorDay, long updatedAt) {
        this.id = TutorSyncContract.ensureValidUuid(id);
        this.accountId = accountId;
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.scheduleGroupId = scheduleGroupId;
        this.dayOfWeek = dayOfWeek;
        this.startTimeMinutes = startTimeMinutes;
        this.durationMinutes = durationMinutes;
        this.startDate = startDate;
        this.endDate = endDate;
        this.billingModel = billingModel;
        this.feeAmountMinor = feeAmountMinor;
        this.anchorDay = anchorDay;
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @NonNull public String getSubjectId() { return subjectId; }
    @Nullable public String getScheduleGroupId() { return scheduleGroupId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public int getStartTimeMinutes() { return startTimeMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public long getStartDate() { return startDate; }
    public long getEndDate() { return endDate; }
    @NonNull public String getBillingModel() { return billingModel; }
    public int getFeeAmountMinor() { return feeAmountMinor; }
    public int getAnchorDay() { return anchorDay; }
    public long getUpdatedAt() { return updatedAt; }
    public long getLastModified() { return updatedAt; }
}
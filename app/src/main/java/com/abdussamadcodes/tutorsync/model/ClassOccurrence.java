package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;

public class ClassOccurrence {
    private final String id;
    private final String accountId;
    private final String studentId;
    private final String subjectId;
    private final String scheduleId;
    private final long date;
    private final int startTimeMinutes;
    private final int durationMinutes;
    private final long updatedAt;

    public ClassOccurrence(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                           @Nullable String subjectId, @Nullable String scheduleId, long date,
                           int startTimeMinutes, int durationMinutes) {
        this(id, accountId, studentId, subjectId, scheduleId, date, startTimeMinutes, durationMinutes, System.currentTimeMillis());
    }

    public ClassOccurrence(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                           @Nullable String subjectId, @Nullable String scheduleId, long date,
                           int startTimeMinutes, int durationMinutes, long updatedAt) {
        this.id = TutorSyncContract.ensureValidUuid(id);
        this.accountId = accountId;
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.scheduleId = scheduleId;
        this.date = date;
        this.startTimeMinutes = startTimeMinutes;
        this.durationMinutes = durationMinutes;
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @Nullable public String getSubjectId() { return subjectId; }
    @Nullable public String getScheduleId() { return scheduleId; }
    public long getDate() { return date; }
    public int getStartTimeMinutes() { return startTimeMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public long getUpdatedAt() { return updatedAt; }
    public long getLastModified() { return updatedAt; }
}
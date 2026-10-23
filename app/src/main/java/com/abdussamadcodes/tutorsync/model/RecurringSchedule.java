package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;

public class RecurringSchedule {
    private final String id;
    private final String accountId;
    private final String studentId;
    private final String subjectId;
    private final int dayOfWeek; // e.g., 1=Sunday, 2=Monday
    private final int startTimeMinutes; // Minutes from midnight (e.g., 600 = 10:00 AM)
    private final int durationMinutes;
    private final long startDate;
    private final long endDate;

    public RecurringSchedule(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                             @NonNull String subjectId, int dayOfWeek, int startTimeMinutes,
                             int durationMinutes, long startDate, long endDate) {
        this.id = id;
        this.accountId = accountId;
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.dayOfWeek = dayOfWeek;
        this.startTimeMinutes = startTimeMinutes;
        this.durationMinutes = durationMinutes;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @NonNull public String getSubjectId() { return subjectId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public int getStartTimeMinutes() { return startTimeMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public long getStartDate() { return startDate; }
    public long getEndDate() { return endDate; }
}
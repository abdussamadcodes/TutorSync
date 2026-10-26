package com.abdussamadcodes.tutorsync.model;

import java.util.ArrayList;
import java.util.List;

public class ScheduleGroup {
    private final String scheduleGroupId;
    private final String studentId;
    private final int startTimeMinutes;
    private final int durationMinutes;
    private final String billingModel;
    private final int feeAmountMinor;
    private final int anchorDay;
    private final List<Integer> daysOfWeek = new ArrayList<>();
    private final List<String> subjectIds = new ArrayList<>();
    private final List<String> scheduleIds = new ArrayList<>();

    public ScheduleGroup(String studentId, int startTimeMinutes, int durationMinutes) {
        this(null, studentId, startTimeMinutes, durationMinutes, "MONTHLY", 0, 1);
    }

    public ScheduleGroup(String scheduleGroupId, String studentId, int startTimeMinutes,
                         int durationMinutes, String billingModel, int feeAmountMinor, int anchorDay) {
        this.scheduleGroupId = scheduleGroupId;
        this.studentId = studentId;
        this.startTimeMinutes = startTimeMinutes;
        this.durationMinutes = durationMinutes;
        this.billingModel = billingModel != null ? billingModel : "MONTHLY";
        this.feeAmountMinor = feeAmountMinor;
        this.anchorDay = anchorDay > 0 ? anchorDay : 1;
    }

    public String getScheduleGroupId() { return scheduleGroupId; }
    public String getStudentId() { return studentId; }
    public int getStartTimeMinutes() { return startTimeMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getBillingModel() { return billingModel; }
    public int getFeeAmountMinor() { return feeAmountMinor; }
    public int getAnchorDay() { return anchorDay; }
    public List<Integer> getDaysOfWeek() { return daysOfWeek; }
    public List<String> getSubjectIds() { return subjectIds; }
    public List<String> getScheduleIds() { return scheduleIds; }
}
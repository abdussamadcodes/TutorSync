package com.abdussamadcodes.tutorsync.model;

import java.util.ArrayList;
import java.util.List;

public class ScheduleGroup {
    private final String studentId;
    private final int startTimeMinutes;
    private final int durationMinutes;
    private final List<Integer> daysOfWeek = new ArrayList<>();
    private final List<String> subjectIds = new ArrayList<>();
    private final List<String> scheduleIds = new ArrayList<>();

    public ScheduleGroup(String studentId, int startTimeMinutes, int durationMinutes) {
        this.studentId = studentId;
        this.startTimeMinutes = startTimeMinutes;
        this.durationMinutes = durationMinutes;
    }

    public String getStudentId() { return studentId; }
    public int getStartTimeMinutes() { return startTimeMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public List<Integer> getDaysOfWeek() { return daysOfWeek; }
    public List<String> getSubjectIds() { return subjectIds; }
    public List<String> getScheduleIds() { return scheduleIds; }
}
package com.abdussamadcodes.tutorsync.model;

import java.util.ArrayList;
import java.util.List;

public class OccurrenceGroup {
    private final String studentId;
    private final long date;
    private final int startTimeMinutes;
    private final int durationMinutes;
    private final List<String> subjectIds = new ArrayList<>();
    private final List<String> occurrenceIds = new ArrayList<>();

    public OccurrenceGroup(String studentId, long date, int startTimeMinutes, int durationMinutes) {
        this.studentId = studentId;
        this.date = date;
        this.startTimeMinutes = startTimeMinutes;
        this.durationMinutes = durationMinutes;
    }

    public String getStudentId() { return studentId; }
    public long getDate() { return date; }
    public int getStartTimeMinutes() { return startTimeMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public List<String> getSubjectIds() { return subjectIds; }
    public List<String> getOccurrenceIds() { return occurrenceIds; }
}
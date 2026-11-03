package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * One class session (one student, one date, one start time) as shown on the Attendance History
 * screen. When several subjects are taught in the same slot they are merged into one item.
 */
public class AttendanceHistoryItem {
    public String studentId;
    public String studentName;
    /** Comma-separated subject titles, e.g. "Math, Physics". */
    public String subjectSummary;
    public long dateMillis;
    public int startTimeMinutes;
    public int durationMinutes;

    /** Occurrence ids of this session. The first one is the "primary" one that carries the charge. */
    public final ArrayList<String> occurrenceIds = new ArrayList<>();

    /** True when at least one occurrence of this session has no attendance record yet. */
    public boolean needsAttendance;

    /** Attendance status of the primary record, null while {@link #needsAttendance} is true. */
    @Nullable public String status;
    @Nullable public String chargeDecision;
    /** Total per-class fee (minor units) that is charged for this session. */
    public int feeMinor;

    /** True when the session is already on an invoice, so it can no longer be changed. */
    public boolean billed;
    /** True when this session is itself a makeup class for an earlier cancelled class. */
    public boolean makeupClass;
    /** True when a makeup class was scheduled for this session. */
    public boolean makeupScheduled;

    public boolean isEditable() {
        return !billed;
    }
}

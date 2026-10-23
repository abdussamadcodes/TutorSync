package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;

public class AttendanceRecord {
    private final String id;
    private final String accountId;
    private final String occurrenceId;
    private final String status; // PRESENT, ABSENT, CANCELLED, RESCHEDULED

    public AttendanceRecord(@NonNull String id, @NonNull String accountId,
                            @NonNull String occurrenceId, @NonNull String status) {
        this.id = id;
        this.accountId = accountId;
        this.occurrenceId = occurrenceId;
        this.status = status;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getOccurrenceId() { return occurrenceId; }
    @NonNull public String getStatus() { return status; }
}
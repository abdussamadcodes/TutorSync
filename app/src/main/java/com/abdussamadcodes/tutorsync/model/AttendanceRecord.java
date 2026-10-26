package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AttendanceRecord {
    public static final String STATUS_PRESENT = "PRESENT";
    public static final String STATUS_STUDENT_ABSENT = "STUDENT_ABSENT";
    public static final String STATUS_TUTOR_ABSENT = "TUTOR_ABSENT";
    public static final String STATUS_CANCELLED_BY_STUDENT = "CANCELLED_BY_STUDENT";
    public static final String STATUS_CANCELLED_BY_TUTOR = "CANCELLED_BY_TUTOR";
    public static final String STATUS_RESCHEDULED = "RESCHEDULED";

    public static final String DECISION_CHARGE = "CHARGE";
    public static final String DECISION_WAIVE = "WAIVE";
    public static final String DECISION_PENDING = "PENDING";
    public static final String DECISION_INCLUDED_MONTHLY = "INCLUDED_IN_MONTHLY";

    private final String id;
    private final String accountId;
    private final String occurrenceId;
    private final String status;
    private final String chargeDecision;
    private final int applicableFeeMinor;
    private final boolean needsReschedule;
    private final String makeupOccurrenceId;
    private final String billedInvoiceItemId;
    private final long updatedAt;

    public AttendanceRecord(@NonNull String id, @NonNull String accountId,
                            @NonNull String occurrenceId, @NonNull String status) {
        this(id, accountId, occurrenceId, status,
                STATUS_PRESENT.equals(status) ? DECISION_CHARGE : DECISION_PENDING,
                0, false, null, null, System.currentTimeMillis());
    }

    public AttendanceRecord(@NonNull String id, @NonNull String accountId,
                            @NonNull String occurrenceId, @NonNull String status,
                            @NonNull String chargeDecision, int applicableFeeMinor,
                            boolean needsReschedule, @Nullable String makeupOccurrenceId,
                            @Nullable String billedInvoiceItemId, long updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.occurrenceId = occurrenceId;
        this.status = status;
        this.chargeDecision = chargeDecision;
        this.applicableFeeMinor = applicableFeeMinor;
        this.needsReschedule = needsReschedule;
        this.makeupOccurrenceId = makeupOccurrenceId;
        this.billedInvoiceItemId = billedInvoiceItemId;
        this.updatedAt = updatedAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getOccurrenceId() { return occurrenceId; }
    @NonNull public String getStatus() { return status; }
    @NonNull public String getChargeDecision() { return chargeDecision; }
    public int getApplicableFeeMinor() { return applicableFeeMinor; }
    public boolean isNeedsReschedule() { return needsReschedule; }
    @Nullable public String getMakeupOccurrenceId() { return makeupOccurrenceId; }
    @Nullable public String getBilledInvoiceItemId() { return billedInvoiceItemId; }
    public long getUpdatedAt() { return updatedAt; }
}
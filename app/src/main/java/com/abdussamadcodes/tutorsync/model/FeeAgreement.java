package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;

public class FeeAgreement {
    public static final String TYPE_MONTHLY = "MONTHLY";
    public static final String TYPE_PER_CLASS = "PER_CLASS";

    private final String id;
    private final String accountId;
    private final String studentId;
    private final String feeType;
    private final int amountMinorUnits;
    private final long updatedAt;

    public FeeAgreement(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                        @NonNull String feeType, int amountMinorUnits, long updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.studentId = studentId;
        this.feeType = feeType;
        this.amountMinorUnits = amountMinorUnits;
        this.updatedAt = updatedAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @NonNull public String getFeeType() { return feeType; }
    public int getAmountMinorUnits() { return amountMinorUnits; }
    public long getUpdatedAt() { return updatedAt; }
}
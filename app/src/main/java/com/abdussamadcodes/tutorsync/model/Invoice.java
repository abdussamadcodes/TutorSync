package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;

public class Invoice {
    public static final String STATUS_UNPAID = "UNPAID";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_PAID = "PAID";

    private final String id;
    private final String accountId;
    private final String studentId;
    private final String billingPeriod;
    private final int totalAmount;
    private final int paidAmount;
    private final String status;
    private final long dueDate;
    private final long createdAt;

    public Invoice(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                   @NonNull String billingPeriod, int totalAmount, int paidAmount,
                   @NonNull String status, long dueDate, long createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.studentId = studentId;
        this.billingPeriod = billingPeriod;
        this.totalAmount = totalAmount;
        this.paidAmount = paidAmount;
        this.status = status;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @NonNull public String getBillingPeriod() { return billingPeriod; }
    public int getTotalAmount() { return totalAmount; }
    public int getPaidAmount() { return paidAmount; }
    @NonNull public String getStatus() { return status; }
    public long getDueDate() { return dueDate; }
    public long getCreatedAt() { return createdAt; }
}
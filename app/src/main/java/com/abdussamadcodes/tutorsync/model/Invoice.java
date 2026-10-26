package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Invoice {
    public static final String STATUS_UNPAID = "UNPAID";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_OVERDUE = "OVERDUE";

    private final String id;
    private final String accountId;
    private final String studentId;
    private final String invoiceIdDisplay;
    private final String billingPeriod;
    private final long periodStart;
    private final long periodEnd;
    private final long issueDate;
    private final long dueDate;
    private final int subtotalMinor;
    private final int discountMinor;
    private final int totalAmount;
    private final int paidAmount; // Derived from payment_allocations
    private final String notes;
    private final long createdAt;

    public Invoice(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                   @Nullable String invoiceIdDisplay, @NonNull String billingPeriod,
                   int totalAmount, int paidAmount, @NonNull String status, long dueDate, long createdAt) {
        this(id, accountId, studentId, invoiceIdDisplay, billingPeriod, createdAt, createdAt,
                createdAt, dueDate, totalAmount, 0, totalAmount, paidAmount, null, createdAt);
    }

    public Invoice(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                   @Nullable String invoiceIdDisplay, @NonNull String billingPeriod,
                   long periodStart, long periodEnd, long issueDate, long dueDate,
                   int subtotalMinor, int discountMinor, int totalAmount, int paidAmount,
                   @Nullable String notes, long createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.studentId = studentId;
        this.invoiceIdDisplay = invoiceIdDisplay;
        this.billingPeriod = billingPeriod;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.subtotalMinor = subtotalMinor;
        this.discountMinor = discountMinor;
        this.totalAmount = totalAmount;
        this.paidAmount = Math.max(0, paidAmount);
        this.notes = notes;
        this.createdAt = createdAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @NonNull public String getInvoiceIdDisplay() {
        return invoiceIdDisplay != null && !invoiceIdDisplay.isEmpty() ? invoiceIdDisplay : "TS-PENDING";
    }
    @NonNull public String getBillingPeriod() { return billingPeriod; }
    public long getPeriodStart() { return periodStart; }
    public long getPeriodEnd() { return periodEnd; }
    public long getIssueDate() { return issueDate; }
    public long getDueDate() { return dueDate; }
    public int getSubtotalMinor() { return subtotalMinor; }
    public int getDiscountMinor() { return discountMinor; }
    public int getTotalAmount() { return totalAmount; }
    public int getPaidAmount() { return paidAmount; }
    public int getOutstandingBalance() { return Math.max(0, totalAmount - paidAmount); }
    @Nullable public String getNotes() { return notes; }
    public long getCreatedAt() { return createdAt; }

    /**
     * Dynamically calculates invoice payment status from underlying amounts and due date.
     */
    @NonNull
    public String getStatus() {
        int balance = getOutstandingBalance();
        if (balance <= 0 && totalAmount > 0) {
            return STATUS_PAID;
        }
        long now = System.currentTimeMillis();
        if (balance > 0 && dueDate > 0 && now > dueDate) {
            return STATUS_OVERDUE;
        }
        if (paidAmount > 0 && balance > 0) {
            return STATUS_PARTIAL;
        }
        return STATUS_UNPAID;
    }

    public boolean isOverdue() {
        return getOutstandingBalance() > 0 && dueDate > 0 && System.currentTimeMillis() > dueDate;
    }
}
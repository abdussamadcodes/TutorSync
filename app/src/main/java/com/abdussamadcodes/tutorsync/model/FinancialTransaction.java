package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class FinancialTransaction {
    public static final String TYPE_PAYMENT = "PAYMENT";
    public static final String TYPE_REFUND = "REFUND";

    private final String id;
    private final String accountId;
    private final String studentId;
    private final String txnIdDisplay;
    private final String type;
    private final int amountMinor;
    private final long timestamp;
    private final String method;
    private final String referenceNo;
    private final String notes;
    private final String parentTxnId;
    private final boolean isReversed;
    private final long createdAt;
    private final List<String> relatedInvoiceDisplayIds = new ArrayList<>();

    public FinancialTransaction(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                                @NonNull String txnIdDisplay, @NonNull String type, int amountMinor,
                                long timestamp, @NonNull String method, @Nullable String referenceNo,
                                @Nullable String notes, @Nullable String parentTxnId,
                                boolean isReversed, long createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.studentId = studentId;
        this.txnIdDisplay = txnIdDisplay;
        this.type = type;
        this.amountMinor = amountMinor;
        this.timestamp = timestamp;
        this.method = method;
        this.referenceNo = referenceNo;
        this.notes = notes;
        this.parentTxnId = parentTxnId;
        this.isReversed = isReversed;
        this.createdAt = createdAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @NonNull public String getTxnIdDisplay() { return txnIdDisplay; }
    @NonNull public String getType() { return type; }
    public int getAmountMinor() { return amountMinor; }
    public long getTimestamp() { return timestamp; }
    @NonNull public String getMethod() { return method; }
    @Nullable public String getReferenceNo() { return referenceNo; }
    @Nullable public String getNotes() { return notes; }
    @Nullable public String getParentTxnId() { return parentTxnId; }
    public boolean isReversed() { return isReversed; }
    public long getCreatedAt() { return createdAt; }
    @NonNull public List<String> getRelatedInvoiceDisplayIds() { return relatedInvoiceDisplayIds; }
}
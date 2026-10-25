package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;

public class Payment {
    private final String id;
    private final String accountId;
    private final String invoiceId;
    private final int amount;
    private final long paymentDate;
    private final String method;

    public Payment(@NonNull String id, @NonNull String accountId, @NonNull String invoiceId,
                   int amount, long paymentDate, @NonNull String method) {
        this.id = id;
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.method = method;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getInvoiceId() { return invoiceId; }
    public int getAmount() { return amount; }
    public long getPaymentDate() { return paymentDate; }
    @NonNull public String getMethod() { return method; }
}
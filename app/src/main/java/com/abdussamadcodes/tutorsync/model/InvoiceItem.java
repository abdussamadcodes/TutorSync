package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class InvoiceItem {
    private final String id;
    private final String accountId;
    private final String invoiceId;
    private final String scheduleGroupId;
    private final String occurrenceId;
    private final String description;
    private final String billingModel;
    private final int quantity;
    private final int unitPriceMinor;
    private final int amountMinor;
    private final long createdAt;

    public InvoiceItem(@NonNull String id, @NonNull String accountId, @NonNull String invoiceId,
                       @Nullable String scheduleGroupId, @Nullable String occurrenceId,
                       @NonNull String description, @NonNull String billingModel,
                       int quantity, int unitPriceMinor, int amountMinor, long createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.scheduleGroupId = scheduleGroupId;
        this.occurrenceId = occurrenceId;
        this.description = description;
        this.billingModel = billingModel;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.amountMinor = amountMinor;
        this.createdAt = createdAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getInvoiceId() { return invoiceId; }
    @Nullable public String getScheduleGroupId() { return scheduleGroupId; }
    @Nullable public String getOccurrenceId() { return occurrenceId; }
    @NonNull public String getDescription() { return description; }
    @NonNull public String getBillingModel() { return billingModel; }
    public int getQuantity() { return quantity; }
    public int getUnitPriceMinor() { return unitPriceMinor; }
    public int getAmountMinor() { return amountMinor; }
    public long getCreatedAt() { return createdAt; }
}
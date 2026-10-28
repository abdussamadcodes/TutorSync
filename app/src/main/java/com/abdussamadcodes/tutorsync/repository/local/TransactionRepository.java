package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TransactionRepository {

    private String generateNextTransactionId(String accountId, SQLiteDatabase db) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = String.format(Locale.US, "TXN-%04d-", year);
        int nextNumber = 1;

        try (Cursor c = db.rawQuery(
                "SELECT " + TransactionEntry.COLUMN_TXN_ID_DISPLAY +
                        " FROM " + TransactionEntry.TABLE_NAME +
                        " WHERE " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        TransactionEntry.COLUMN_TXN_ID_DISPLAY + " LIKE ? " +
                        " ORDER BY CAST(SUBSTR(" + TransactionEntry.COLUMN_TXN_ID_DISPLAY + ", 10) AS INTEGER) DESC LIMIT 1",
                new String[]{accountId, prefix + "%"})) {
            if (c.moveToFirst()) {
                try {
                    nextNumber = Integer.parseInt(c.getString(0).substring(9)) + 1;
                } catch (Exception ignored) {}
            }
        }
        return prefix + String.format(Locale.US, "%06d", nextNumber);
    }

    /**
     * Records a payment and allocates it across one or more invoices (FIFO or explicit map).
     * Automatically synchronizes legacy mirror columns on invoices while keeping payment_allocations authoritative.
     */
    @Nullable
    public FinancialTransaction recordPaymentWithAllocations(
            @NonNull String accountId,
            @NonNull String studentId,
            int totalPaymentMinor,
            @NonNull String method,
            @Nullable String referenceNo,
            @Nullable String notes,
            @NonNull Map<String, Integer> invoiceAllocationsMinor) {

        if (totalPaymentMinor <= 0 || invoiceAllocationsMinor.isEmpty()) return null;

        int sumAllocated = 0;
        for (int alloc : invoiceAllocationsMinor.values()) {
            if (alloc <= 0) return null;
            sumAllocated += alloc;
        }
        if (sumAllocated != totalPaymentMinor) return null;

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            String txnId = UUID.randomUUID().toString();
            String displayId = generateNextTransactionId(accountId, db);

            ContentValues tv = new ContentValues();
            tv.put(TransactionEntry.COLUMN_ID, txnId);
            tv.put(TransactionEntry.COLUMN_ACCOUNT_ID, accountId);
            tv.put(TransactionEntry.COLUMN_STUDENT_ID, studentId);
            tv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, displayId);
            tv.put(TransactionEntry.COLUMN_TYPE, FinancialTransaction.TYPE_PAYMENT);
            tv.put(TransactionEntry.COLUMN_AMOUNT_MINOR, totalPaymentMinor);
            tv.put(TransactionEntry.COLUMN_TIMESTAMP, now);
            tv.put(TransactionEntry.COLUMN_METHOD, method);
            tv.put(TransactionEntry.COLUMN_REFERENCE_NO, referenceNo);
            tv.put(TransactionEntry.COLUMN_NOTES, notes);
            tv.put(TransactionEntry.COLUMN_IS_REVERSED, 0);
            tv.put(TransactionEntry.COLUMN_CREATED_AT, now);
            db.insertOrThrow(TransactionEntry.TABLE_NAME, null, tv);

            FinancialTransaction created = new FinancialTransaction(
                    txnId, accountId, studentId, displayId, FinancialTransaction.TYPE_PAYMENT,
                    totalPaymentMinor, now, method, referenceNo, notes, null, false, now
            );

            for (Map.Entry<String, Integer> entry : invoiceAllocationsMinor.entrySet()) {
                String invoiceId = entry.getKey();
                int allocMinor = entry.getValue();

                ContentValues av = new ContentValues();
                av.put(PaymentAllocationEntry.COLUMN_ID, UUID.randomUUID().toString());
                av.put(PaymentAllocationEntry.COLUMN_ACCOUNT_ID, accountId);
                av.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, txnId);
                av.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invoiceId);
                av.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, allocMinor);
                av.put(PaymentAllocationEntry.COLUMN_CREATED_AT, now);
                db.insertOrThrow(PaymentAllocationEntry.TABLE_NAME, null, av);

                syncInvoiceMirrorColumns(db, invoiceId);
            }

            InvoiceRepository.logAudit(db, accountId, studentId, "TRANSACTION", txnId, "PAYMENT_RECEIVED",
                    "Received " + displayId + " (৳" + String.format(Locale.US, "%.2f", totalPaymentMinor / 100.0) + ") via " + method);

            db.setTransactionSuccessful();
            return created;
        } catch (Exception e) {
            return null;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    /**
     * Allocates a lump-sum student payment automatically across their oldest unpaid/partially-paid invoices (FIFO).
     */
    @Nullable
    public FinancialTransaction recordStudentLumpSumPaymentFIFO(
            @NonNull String accountId,
            @NonNull String studentId,
            int paymentAmountMinor,
            @NonNull String method,
            @Nullable String referenceNo,
            @Nullable String notes,
            @NonNull List<Invoice> openStudentInvoicesChronological) {

        Map<String, Integer> allocations = new LinkedHashMap<>();
        int remaining = paymentAmountMinor;

        for (Invoice inv : openStudentInvoicesChronological) {
            if (remaining <= 0) break;
            int due = inv.getOutstandingBalance();
            if (due <= 0) continue;
            int alloc = Math.min(remaining, due);
            allocations.put(inv.getId(), alloc);
            remaining -= alloc;
        }
        if (remaining > 0 || allocations.isEmpty()) {
            return null; // Rejects overpayment exceeding total outstanding student dues
        }
        return recordPaymentWithAllocations(accountId, studentId, paymentAmountMinor, method, referenceNo, notes, allocations);
    }

    /**
     * Records a refund or payment reversal with negative payment_allocations and a permanent audit trail.
     * Prevents refunding reversed transactions, refunding non-payment transactions, or exceeding remaining refundable balance.
     */
    public boolean recordRefundForTransaction(
            @NonNull String accountId,
            @NonNull String originalTransactionId,
            int refundAmountMinor,
            @NonNull String reasonNotes) {

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            String studentId = null;
            String method = "Cash";
            String origType = null;
            boolean origReversed = false;
            int origAmount = 0;

            try (Cursor c = db.query(TransactionEntry.TABLE_NAME, null,
                    TransactionEntry.COLUMN_ID + " = ? AND " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ?",
                    new String[]{originalTransactionId, accountId}, null, null, null)) {
                if (!c.moveToFirst()) return false;
                studentId = c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_STUDENT_ID));
                method = c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_METHOD));
                origType = c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_TYPE));
                origReversed = c.getInt(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_IS_REVERSED)) == 1;
                origAmount = c.getInt(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_AMOUNT_MINOR));
            }

            if (!FinancialTransaction.TYPE_PAYMENT.equals(origType) || origReversed) {
                return false;
            }

            int alreadyRefundedMinor = 0;
            try (Cursor rc = db.rawQuery(
                    "SELECT COALESCE(SUM(" + TransactionEntry.COLUMN_AMOUNT_MINOR + "), 0) " +
                            "FROM " + TransactionEntry.TABLE_NAME +
                            " WHERE " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ? " +
                            " AND " + TransactionEntry.COLUMN_PARENT_TXN_ID + " = ? " +
                            " AND " + TransactionEntry.COLUMN_TYPE + " = ? " +
                            " AND " + TransactionEntry.COLUMN_IS_REVERSED + " = 0",
                    new String[]{accountId, originalTransactionId, FinancialTransaction.TYPE_REFUND})) {
                if (rc.moveToFirst()) {
                    alreadyRefundedMinor = Math.max(0, rc.getInt(0));
                }
            }

            int remainingRefundableMinor = origAmount - alreadyRefundedMinor;
            if (refundAmountMinor <= 0 || refundAmountMinor > remainingRefundableMinor) {
                return false;
            }

            long now = System.currentTimeMillis();
            String refundTxnId = UUID.randomUUID().toString();
            String refundDisplayId = generateNextTransactionId(accountId, db);

            ContentValues tv = new ContentValues();
            tv.put(TransactionEntry.COLUMN_ID, refundTxnId);
            tv.put(TransactionEntry.COLUMN_ACCOUNT_ID, accountId);
            tv.put(TransactionEntry.COLUMN_STUDENT_ID, studentId);
            tv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, refundDisplayId);
            tv.put(TransactionEntry.COLUMN_TYPE, FinancialTransaction.TYPE_REFUND);
            tv.put(TransactionEntry.COLUMN_AMOUNT_MINOR, refundAmountMinor);
            tv.put(TransactionEntry.COLUMN_TIMESTAMP, now);
            tv.put(TransactionEntry.COLUMN_METHOD, method);
            tv.put(TransactionEntry.COLUMN_NOTES, reasonNotes);
            tv.put(TransactionEntry.COLUMN_PARENT_TXN_ID, originalTransactionId);
            tv.put(TransactionEntry.COLUMN_IS_REVERSED, 0);
            tv.put(TransactionEntry.COLUMN_CREATED_AT, now);
            db.insertOrThrow(TransactionEntry.TABLE_NAME, null, tv);

            // Compute net remaining allocation per invoice for this original transaction
            int remainingToDeallocate = refundAmountMinor;
            String netAllocSql =
                    "SELECT pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + ", " +
                            "pa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + " + COALESCE((" +
                            "   SELECT SUM(rpa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + ") " +
                            "   FROM " + PaymentAllocationEntry.TABLE_NAME + " rpa " +
                            "   INNER JOIN " + TransactionEntry.TABLE_NAME + " rt ON rpa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = rt." + TransactionEntry.COLUMN_ID + " " +
                            "   WHERE rt." + TransactionEntry.COLUMN_PARENT_TXN_ID + " = ? " +
                            "   AND rt." + TransactionEntry.COLUMN_IS_REVERSED + " = 0 " +
                            "   AND rpa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID +
                            "), 0) AS net_alloc_minor " +
                            "FROM " + PaymentAllocationEntry.TABLE_NAME + " pa " +
                            "WHERE pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ? " +
                            "ORDER BY pa." + PaymentAllocationEntry.COLUMN_CREATED_AT + " DESC";

            try (Cursor ac = db.rawQuery(netAllocSql, new String[]{originalTransactionId, originalTransactionId})) {
                while (ac.moveToNext() && remainingToDeallocate > 0) {
                    String invId = ac.getString(0);
                    int netInvoiceAlloc = Math.max(0, ac.getInt(1));
                    if (netInvoiceAlloc <= 0) continue;

                    int dealloc = Math.min(remainingToDeallocate, netInvoiceAlloc);

                    ContentValues revAlloc = new ContentValues();
                    revAlloc.put(PaymentAllocationEntry.COLUMN_ID, UUID.randomUUID().toString());
                    revAlloc.put(PaymentAllocationEntry.COLUMN_ACCOUNT_ID, accountId);
                    revAlloc.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, refundTxnId);
                    revAlloc.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invId);
                    revAlloc.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, -dealloc);
                    revAlloc.put(PaymentAllocationEntry.COLUMN_CREATED_AT, now);
                    db.insertOrThrow(PaymentAllocationEntry.TABLE_NAME, null, revAlloc);

                    syncInvoiceMirrorColumns(db, invId);
                    remainingToDeallocate -= dealloc;
                }
            }

            InvoiceRepository.logAudit(db, accountId, studentId, "TRANSACTION", refundTxnId, "REFUND_ISSUED",
                    "Refunded ৳" + String.format(Locale.US, "%.2f", refundAmountMinor / 100.0) + " (" + reasonNotes + ")");

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private void syncInvoiceMirrorColumns(SQLiteDatabase db, String invoiceId) {
        int derivedPaid = 0;
        int totalAmount = 0;
        long dueDate = 0L;
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(pa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + "), 0) " +
                        "FROM " + PaymentAllocationEntry.TABLE_NAME + " pa " +
                        "INNER JOIN " + TransactionEntry.TABLE_NAME + " t ON pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = t." + TransactionEntry.COLUMN_ID + " " +
                        "WHERE pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ? " +
                        "AND t." + TransactionEntry.COLUMN_IS_REVERSED + " = 0",
                new String[]{invoiceId})) {
            if (c.moveToFirst()) derivedPaid = Math.max(0, c.getInt(0));
        }
        try (Cursor c = db.query(InvoiceEntry.TABLE_NAME,
                new String[]{InvoiceEntry.COLUMN_TOTAL_AMOUNT, InvoiceEntry.COLUMN_DUE_DATE},
                InvoiceEntry.COLUMN_ID + " = ?", new String[]{invoiceId}, null, null, null)) {
            if (c.moveToFirst()) {
                totalAmount = c.getInt(0);
                dueDate = c.getLong(1);
            }
        }

        long now = System.currentTimeMillis();
        String status = Invoice.STATUS_UNPAID;
        if (derivedPaid >= totalAmount && totalAmount > 0) {
            status = Invoice.STATUS_PAID;
        } else if (totalAmount - derivedPaid > 0 && dueDate > 0 && now > dueDate) {
            status = Invoice.STATUS_OVERDUE;
        } else if (derivedPaid > 0) {
            status = Invoice.STATUS_PARTIAL;
        }

        ContentValues iv = new ContentValues();
        iv.put(InvoiceEntry.COLUMN_PAID_AMOUNT, derivedPaid);
        iv.put(InvoiceEntry.COLUMN_STATUS, status);
        iv.put(InvoiceEntry.COLUMN_UPDATED_AT, now);
        db.update(InvoiceEntry.TABLE_NAME, iv, InvoiceEntry.COLUMN_ID + " = ?", new String[]{invoiceId});
    }

    @NonNull
    public List<FinancialTransaction> getTransactionsForStudent(@NonNull String accountId, @NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<FinancialTransaction> list = new ArrayList<>();
        try (Cursor c = db.query(TransactionEntry.TABLE_NAME, null,
                TransactionEntry.COLUMN_ACCOUNT_ID + " = ? AND " + TransactionEntry.COLUMN_STUDENT_ID + " = ?",
                new String[]{accountId, studentId}, null, null, TransactionEntry.COLUMN_TIMESTAMP + " DESC")) {
            while (c.moveToNext()) {
                FinancialTransaction txn = mapCursorToTransaction(c);
                populateRelatedInvoiceDisplayIds(db, txn);
                list.add(txn);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return list;
    }

    @NonNull
    public List<FinancialTransaction> getTransactionsForInvoice(@NonNull String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<FinancialTransaction> list = new ArrayList<>();
        String sql = "SELECT DISTINCT t.* FROM " + TransactionEntry.TABLE_NAME + " t " +
                "INNER JOIN " + PaymentAllocationEntry.TABLE_NAME + " pa ON t." + TransactionEntry.COLUMN_ID + " = pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " " +
                "WHERE pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ? " +
                "ORDER BY t." + TransactionEntry.COLUMN_TIMESTAMP + " DESC";
        try (Cursor c = db.rawQuery(sql, new String[]{invoiceId})) {
            while (c.moveToNext()) {
                FinancialTransaction txn = mapCursorToTransaction(c);
                populateRelatedInvoiceDisplayIds(db, txn);
                list.add(txn);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return list;
    }

    private void populateRelatedInvoiceDisplayIds(SQLiteDatabase db, FinancialTransaction txn) {
        String sql = "SELECT DISTINCT i." + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY +
                " FROM " + PaymentAllocationEntry.TABLE_NAME + " pa " +
                " INNER JOIN " + InvoiceEntry.TABLE_NAME + " i ON pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = i." + InvoiceEntry.COLUMN_ID +
                " WHERE pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ?";
        try (Cursor c = db.rawQuery(sql, new String[]{txn.getId()})) {
            while (c.moveToNext()) {
                String disp = c.getString(0);
                if (disp != null) txn.getRelatedInvoiceDisplayIds().add(disp);
            }
        }
    }

    private FinancialTransaction mapCursorToTransaction(Cursor c) {
        return new FinancialTransaction(
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_ID)),
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_ACCOUNT_ID)),
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_STUDENT_ID)),
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_TXN_ID_DISPLAY)),
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_TYPE)),
                c.getInt(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_AMOUNT_MINOR)),
                c.getLong(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_TIMESTAMP)),
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_METHOD)),
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_REFERENCE_NO)),
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_NOTES)),
                c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_PARENT_TXN_ID)),
                c.getInt(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_IS_REVERSED)) == 1,
                c.getLong(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_CREATED_AT))
        );
    }
}
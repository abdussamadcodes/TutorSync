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
            int origAmount = 0;

            try (Cursor c = db.query(TransactionEntry.TABLE_NAME, null,
                    TransactionEntry.COLUMN_ID + " = ? AND " + TransactionEntry.COLUMN_ACCOUNT_ID + " = ?",
                    new String[]{originalTransactionId, accountId}, null, null, null)) {
                if (!c.moveToFirst()) return false;
                studentId = c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_STUDENT_ID));
                method = c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_METHOD));
                origAmount = c.getInt(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_AMOUNT_MINOR));
            }

            if (refundAmountMinor <= 0 || refundAmountMinor > origAmount) return false;

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

            // De-allocate proportionally across the original transaction's invoice allocations
            int remainingToDeallocate = refundAmountMinor;
            try (Cursor ac = db.query(PaymentAllocationEntry.TABLE_NAME,
                    new String[]{PaymentAllocationEntry.COLUMN_INVOICE_ID, PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR},
                    PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ?",
                    new String[]{originalTransactionId}, null, null, PaymentAllocationEntry.COLUMN_CREATED_AT + " DESC")) {
                while (ac.moveToNext() && remainingToDeallocate > 0) {
                    String invId = ac.getString(0);
                    int origAlloc = ac.getInt(1);
                    int dealloc = Math.min(remainingToDeallocate, origAlloc);

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
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(" + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + "), 0) " +
                        "FROM " + PaymentAllocationEntry.TABLE_NAME + " WHERE " + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ?",
                new String[]{invoiceId})) {
            if (c.moveToFirst()) derivedPaid = Math.max(0, c.getInt(0));
        }
        try (Cursor c = db.query(InvoiceEntry.TABLE_NAME, new String[]{InvoiceEntry.COLUMN_TOTAL_AMOUNT},
                InvoiceEntry.COLUMN_ID + " = ?", new String[]{invoiceId}, null, null, null)) {
            if (c.moveToFirst()) totalAmount = c.getInt(0);
        }

        String status = Invoice.STATUS_UNPAID;
        if (derivedPaid >= totalAmount && totalAmount > 0) status = Invoice.STATUS_PAID;
        else if (derivedPaid > 0) status = Invoice.STATUS_PARTIAL;

        ContentValues iv = new ContentValues();
        iv.put(InvoiceEntry.COLUMN_PAID_AMOUNT, derivedPaid);
        iv.put(InvoiceEntry.COLUMN_STATUS, status);
        iv.put(InvoiceEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());
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
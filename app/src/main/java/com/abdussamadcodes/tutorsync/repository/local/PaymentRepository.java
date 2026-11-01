package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentEntry;
import com.abdussamadcodes.tutorsync.model.Payment;
import java.util.ArrayList;
import java.util.List;

public class PaymentRepository {

    public boolean insertPayment(@NonNull Payment payment) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long ts = payment.getUpdatedAt() > 0 ? payment.getUpdatedAt()
                    : (payment.getPaymentDate() > 0 ? payment.getPaymentDate() : System.currentTimeMillis());
            ContentValues values = new ContentValues();
            values.put(PaymentEntry.COLUMN_ID, payment.getId());
            values.put(PaymentEntry.COLUMN_ACCOUNT_ID, payment.getAccountId());
            values.put(PaymentEntry.COLUMN_INVOICE_ID, payment.getInvoiceId());
            values.put(PaymentEntry.COLUMN_AMOUNT, payment.getAmount());
            values.put(PaymentEntry.COLUMN_PAYMENT_DATE, payment.getPaymentDate());
            values.put(PaymentEntry.COLUMN_METHOD, payment.getMethod());
            values.put(PaymentEntry.COLUMN_CREATED_AT, payment.getPaymentDate() > 0 ? payment.getPaymentDate() : ts);
            values.put(PaymentEntry.COLUMN_UPDATED_AT, ts);
            values.put(PaymentEntry.COLUMN_LAST_MODIFIED, ts);

            return db.insertWithOnConflict(
                    PaymentEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<Payment> getPaymentsForInvoice(@NonNull String accountId, @NonNull String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Payment> payments = new ArrayList<>();
        try (Cursor cursor = db.query(PaymentEntry.TABLE_NAME, null,
                PaymentEntry.COLUMN_ACCOUNT_ID + " = ? AND " + PaymentEntry.COLUMN_INVOICE_ID + " = ?",
                new String[]{accountId, invoiceId}, null, null, PaymentEntry.COLUMN_PAYMENT_DATE + " DESC")) {
            while (cursor.moveToNext()) {
                payments.add(mapCursorToPayment(cursor));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return payments;
    }

    @NonNull
    public List<Payment> getPaymentsForInvoice(@NonNull String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Payment> payments = new ArrayList<>();
        try (Cursor cursor = db.query(PaymentEntry.TABLE_NAME, null,
                PaymentEntry.COLUMN_INVOICE_ID + " = ?", new String[]{invoiceId}, null, null, PaymentEntry.COLUMN_PAYMENT_DATE + " DESC")) {
            while (cursor.moveToNext()) {
                payments.add(mapCursorToPayment(cursor));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return payments;
    }

    @NonNull
    private Payment mapCursorToPayment(@NonNull Cursor cursor) {
        long updatedAt = 0L;
        int lastModIdx = cursor.getColumnIndex(PaymentEntry.COLUMN_LAST_MODIFIED);
        if (lastModIdx != -1 && !cursor.isNull(lastModIdx)) {
            updatedAt = cursor.getLong(lastModIdx);
        }
        if (updatedAt <= 0L) {
            int upIdx = cursor.getColumnIndex(PaymentEntry.COLUMN_UPDATED_AT);
            if (upIdx != -1 && !cursor.isNull(upIdx)) {
                updatedAt = cursor.getLong(upIdx);
            }
        }
        return new Payment(
                cursor.getString(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_ACCOUNT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_INVOICE_ID)),
                cursor.getInt(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_AMOUNT)),
                cursor.getLong(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_PAYMENT_DATE)),
                cursor.getString(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_METHOD)),
                updatedAt
        );
    }
}
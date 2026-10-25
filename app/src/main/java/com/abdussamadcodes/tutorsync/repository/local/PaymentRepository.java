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
            ContentValues values = new ContentValues();
            values.put(PaymentEntry.COLUMN_ID, payment.getId());
            values.put(PaymentEntry.COLUMN_ACCOUNT_ID, payment.getAccountId());
            values.put(PaymentEntry.COLUMN_INVOICE_ID, payment.getInvoiceId());
            values.put(PaymentEntry.COLUMN_AMOUNT, payment.getAmount());
            values.put(PaymentEntry.COLUMN_PAYMENT_DATE, payment.getPaymentDate());
            values.put(PaymentEntry.COLUMN_METHOD, payment.getMethod());

            return db.insert(PaymentEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<Payment> getPaymentsForInvoice(@NonNull String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Payment> payments = new ArrayList<>();
        try (Cursor cursor = db.query(PaymentEntry.TABLE_NAME, null,
                PaymentEntry.COLUMN_INVOICE_ID + " = ?", new String[]{invoiceId}, null, null, PaymentEntry.COLUMN_PAYMENT_DATE + " DESC")) {
            while (cursor.moveToNext()) {
                payments.add(new Payment(
                        cursor.getString(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_INVOICE_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_AMOUNT)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_PAYMENT_DATE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(PaymentEntry.COLUMN_METHOD))
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return payments;
    }
}
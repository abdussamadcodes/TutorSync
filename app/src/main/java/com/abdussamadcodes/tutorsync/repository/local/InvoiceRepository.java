package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceEntry;
import com.abdussamadcodes.tutorsync.model.Invoice;
import java.util.ArrayList;
import java.util.List;

public class InvoiceRepository {

    // --- NEW: Migration Logic ---
    public void migrateLegacyInvoiceIds(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            String selection = InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                    InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " IS NULL OR " +
                    InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " = '')";

            List<String> needsMigration = new ArrayList<>();
            try (Cursor cursor = db.query(InvoiceEntry.TABLE_NAME, new String[]{InvoiceEntry.COLUMN_ID}, selection, new String[]{accountId}, null, null, InvoiceEntry.COLUMN_CREATED_AT + " ASC")) {
                while (cursor.moveToNext()) {
                    needsMigration.add(cursor.getString(0));
                }
            }

            for (String internalId : needsMigration) {
                String newDisplayId = generateNextInvoiceId(accountId, db);
                ContentValues values = new ContentValues();
                values.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, newDisplayId);
                db.update(InvoiceEntry.TABLE_NAME, values, InvoiceEntry.COLUMN_ID + " = ?", new String[]{internalId});
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private String generateNextInvoiceId(String accountId, SQLiteDatabase db) {
        String prefix = "#INV-";
        int nextNumber = 1001; // Start at 1001 for a professional look

        try (Cursor cursor = db.rawQuery(
                "SELECT " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY +
                        " FROM " + InvoiceEntry.TABLE_NAME +
                        " WHERE " + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? " +
                        " AND " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " LIKE '#INV-%'" +
                        " ORDER BY CAST(SUBSTR(" + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + ", 6) AS INTEGER) DESC LIMIT 1",
                new String[]{accountId})) {

            if (cursor.moveToFirst()) {
                String lastId = cursor.getString(0);
                try {
                    int lastNumber = Integer.parseInt(lastId.substring(5));
                    nextNumber = lastNumber + 1;
                } catch (NumberFormatException ignored) {}
            }
        }
        return prefix + String.format(java.util.Locale.US, "%04d", nextNumber);
    }
    // ---------------------------

    public boolean insertInvoice(@NonNull Invoice invoice) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            // Generate ID if null
            String displayId = invoice.getInvoiceIdDisplay();
            if (displayId == null || displayId.trim().isEmpty()) {
                displayId = generateNextInvoiceId(invoice.getAccountId(), db);
            }

            ContentValues values = new ContentValues();
            values.put(InvoiceEntry.COLUMN_ID, invoice.getId());
            values.put(InvoiceEntry.COLUMN_ACCOUNT_ID, invoice.getAccountId());
            values.put(InvoiceEntry.COLUMN_STUDENT_ID, invoice.getStudentId());
            values.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, displayId); // NEW
            values.put(InvoiceEntry.COLUMN_BILLING_PERIOD, invoice.getBillingPeriod());
            values.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, invoice.getTotalAmount());
            values.put(InvoiceEntry.COLUMN_PAID_AMOUNT, invoice.getPaidAmount());
            values.put(InvoiceEntry.COLUMN_STATUS, invoice.getStatus());
            values.put(InvoiceEntry.COLUMN_DUE_DATE, invoice.getDueDate());
            values.put(InvoiceEntry.COLUMN_CREATED_AT, invoice.getCreatedAt());

            return db.insert(InvoiceEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean updateInvoicePayment(@NonNull String invoiceId, int newPaidAmount, @NonNull String newStatus) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(InvoiceEntry.COLUMN_PAID_AMOUNT, newPaidAmount);
            values.put(InvoiceEntry.COLUMN_STATUS, newStatus);
            return db.update(InvoiceEntry.TABLE_NAME, values, InvoiceEntry.COLUMN_ID + " = ?", new String[]{invoiceId}) > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @NonNull
    public List<Invoice> getInvoicesForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        List<Invoice> invoices = new ArrayList<>();
        try (Cursor cursor = db.query(InvoiceEntry.TABLE_NAME, null,
                InvoiceEntry.COLUMN_ACCOUNT_ID + " = ?", new String[]{accountId}, null, null, InvoiceEntry.COLUMN_CREATED_AT + " DESC")) {
            while (cursor.moveToNext()) {
                invoices.add(new Invoice(
                        cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_STUDENT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY)), // NEW
                        cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_BILLING_PERIOD)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_TOTAL_AMOUNT)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_PAID_AMOUNT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_STATUS)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_DUE_DATE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(InvoiceEntry.COLUMN_CREATED_AT))
                ));
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return invoices;
    }

    public int getTotalRevenueForAccount(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        int totalRevenue = 0;
        try (Cursor cursor = db.rawQuery("SELECT SUM(" + InvoiceEntry.COLUMN_PAID_AMOUNT + ") FROM " + InvoiceEntry.TABLE_NAME + " WHERE " + InvoiceEntry.COLUMN_ACCOUNT_ID + " = ?", new String[]{accountId})) {
            if (cursor.moveToFirst()) {
                totalRevenue = cursor.getInt(0);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return totalRevenue;
    }
}
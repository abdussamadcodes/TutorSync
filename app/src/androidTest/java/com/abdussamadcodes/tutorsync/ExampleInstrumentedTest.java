package com.abdussamadcodes.tutorsync;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.TransactionRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Test
    public void useAppContext() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.abdussamadcodes.tutorsync", appContext.getPackageName());
    }

    @Test
    public void localAccountStore_mapsAllProfileFieldsCorrectly() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalAccountStore store = new LocalAccountStore(context);
        String accountId = "test-acc-" + UUID.randomUUID();
        String username = "tutor" + System.currentTimeMillis();

        try {
            Account account = new Account(
                    accountId, "Test Tutor", username, "hash123", "salt123",
                    "01700000000", "tutor@example.com", "Rajshahi", "2000-01-01", "/tmp/pic.jpg"
            );
            assertTrue(store.saveAccount(account));

            Account loaded = store.getAccountById(accountId);
            assertNotNull(loaded);
            assertEquals("Test Tutor", loaded.getFullName());
            assertEquals("01700000000", loaded.getPhone());
            assertEquals("tutor@example.com", loaded.getEmail());
            assertEquals("Rajshahi", loaded.getAddress());
            assertEquals("2000-01-01", loaded.getDob());
            assertEquals("/tmp/pic.jpg", loaded.getProfilePicPath());
        } finally {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                db.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " = ?", new String[]{accountId});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }
    }

    @Test
    public void transactionRepository_preventsDuplicateRefundAndUpdatesInvoiceBalance() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalAccountStore accountStore = new LocalAccountStore(context);
        StudentRepository studentRepo = new StudentRepository();
        InvoiceRepository invoiceRepo = new InvoiceRepository();
        TransactionRepository txnRepo = new TransactionRepository();

        String accountId = "test-fin-" + UUID.randomUUID();
        String studentId = "test-stu-" + UUID.randomUUID();
        String invoiceId = "test-inv-" + UUID.randomUUID();

        try {
            accountStore.saveAccount(new Account(accountId, "Fin Tutor", "fin" + System.currentTimeMillis(), "h", "s"));
            studentRepo.insertStudent(new Student(studentId, accountId, "STU-9999", "Student A", "10", null, null, null, null, true));

            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                long now = System.currentTimeMillis();
                ContentValues iv = new ContentValues();
                iv.put(InvoiceEntry.COLUMN_ID, invoiceId);
                iv.put(InvoiceEntry.COLUMN_ACCOUNT_ID, accountId);
                iv.put(InvoiceEntry.COLUMN_STUDENT_ID, studentId);
                iv.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, "TS-2026-999999");
                iv.put(InvoiceEntry.COLUMN_BILLING_PERIOD, "Oct 2026");
                iv.put(InvoiceEntry.COLUMN_DUE_DATE, now + 86400000L);
                iv.put(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, 100000);
                iv.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, 100000);
                iv.put(InvoiceEntry.COLUMN_PAID_AMOUNT, 0);
                iv.put(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_UNPAID);
                iv.put(InvoiceEntry.COLUMN_CREATED_AT, now);
                db.insertOrThrow(InvoiceEntry.TABLE_NAME, null, iv);
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }

            // 1. Record full payment of 1000.00 BDT (100,000 minor units)
            FinancialTransaction payment = txnRepo.recordPaymentWithAllocations(
                    accountId, studentId, 100000, "Cash", null, null,
                    Collections.singletonMap(invoiceId, 100000)
            );
            assertNotNull(payment);

            Invoice afterPay = invoiceRepo.getInvoiceById(invoiceId);
            assertNotNull(afterPay);
            assertEquals(100000, afterPay.getPaidAmount());
            assertEquals(0, afterPay.getOutstandingBalance());
            assertEquals(Invoice.STATUS_PAID, afterPay.getStatus());

            // 2. Issue partial refund of 400.00 BDT (40,000 minor units)
            assertTrue(txnRepo.recordRefundForTransaction(accountId, payment.getId(), 40000, "Partial refund"));

            Invoice afterPartialRefund = invoiceRepo.getInvoiceById(invoiceId);
            assertNotNull(afterPartialRefund);
            assertEquals(60000, afterPartialRefund.getPaidAmount());
            assertEquals(40000, afterPartialRefund.getOutstandingBalance());
            assertEquals(Invoice.STATUS_PARTIAL, afterPartialRefund.getStatus());

            // 3. Attempting to refund 1000.00 BDT again must fail (only 600.00 BDT remains refundable)
            assertFalse(txnRepo.recordRefundForTransaction(accountId, payment.getId(), 100000, "Over-refund attempt"));

            // 4. Refunding the remaining 600.00 BDT succeeds
            assertTrue(txnRepo.recordRefundForTransaction(accountId, payment.getId(), 60000, "Remaining refund"));

            Invoice afterFullRefund = invoiceRepo.getInvoiceById(invoiceId);
            assertNotNull(afterFullRefund);
            assertEquals(0, afterFullRefund.getPaidAmount());
            assertEquals(100000, afterFullRefund.getOutstandingBalance());
            assertEquals(Invoice.STATUS_UNPAID, afterFullRefund.getStatus());
        } finally {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                db.delete(StudentEntry.TABLE_NAME, StudentEntry.COLUMN_ID + " = ?", new String[]{studentId});
                db.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " = ?", new String[]{accountId});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }
    }
}
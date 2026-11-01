package com.abdussamadcodes.tutorsync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AuditLogEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentAllocationEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.TransactionEntry;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Payment;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository;
import com.abdussamadcodes.tutorsync.repository.local.EnrollmentRepository;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.PaymentRepository;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.repository.local.TransactionRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.util.BackupRestoreManager;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    public void backupRestore_preservesUuids_handlesRepeatedAndCrossAccountImports_andIsolatesAccounts() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalAccountStore accountStore = new LocalAccountStore(context);
        StudentRepository studentRepo = new StudentRepository();
        SubjectRepository subjectRepo = new SubjectRepository();
        EnrollmentRepository enrollmentRepo = new EnrollmentRepository();
        ScheduleRepository scheduleRepo = new ScheduleRepository();
        OccurrenceRepository occurrenceRepo = new OccurrenceRepository();
        AttendanceRepository attendanceRepo = new AttendanceRepository();
        InvoiceRepository invoiceRepo = new InvoiceRepository();
        TransactionRepository txnRepo = new TransactionRepository();

        String accountA = UUID.randomUUID().toString();
        String accountB = UUID.randomUUID().toString();
        String studentUuid = UUID.randomUUID().toString();
        String subjectUuid = UUID.randomUUID().toString();
        String scheduleUuid = UUID.randomUUID().toString();
        String groupUuid = UUID.randomUUID().toString();
        String occurrenceUuid = UUID.randomUUID().toString();
        String attendanceUuid = UUID.randomUUID().toString();

        try {
            assertTrue(accountStore.saveAccount(new Account(accountA, "Tutor A", "usera" + System.currentTimeMillis(), "hashA", "saltA")));
            assertTrue(accountStore.saveAccount(new Account(accountB, "Tutor B", "userb" + System.currentTimeMillis(), "hashB", "saltB")));

            long now = System.currentTimeMillis();
            assertEquals(1, studentRepo.insertStudent(new Student(studentUuid, accountA, "STU-0001", "Rahim", "9", "Zilla School", "Karim", "01711111111", "Dhaka", true, now)));
            assertTrue(subjectRepo.insertSubject(new Subject(subjectUuid, accountA, "Physics", "SSC", true, now)));
            assertTrue(enrollmentRepo.enrollStudentInSubject(accountA, studentUuid, subjectUuid));

            RecurringSchedule schedule = new RecurringSchedule(
                    scheduleUuid, accountA, studentUuid, subjectUuid, groupUuid,
                    2, 960, 60, now - 86400000L, now + 864000000L,
                    RecurringSchedule.MODEL_PER_CLASS, 50000, 1, now
            );
            assertTrue(scheduleRepo.insertScheduleWithFee(schedule, groupUuid, RecurringSchedule.MODEL_PER_CLASS, 50000, 1));

            ClassOccurrence occ = new ClassOccurrence(occurrenceUuid, accountA, studentUuid, subjectUuid, scheduleUuid, now, 960, 60, now);
            assertTrue(occurrenceRepo.insertOccurrenceWithGroup(occ, groupUuid));

            AttendanceRecord att = new AttendanceRecord(attendanceUuid, accountA, occurrenceUuid, AttendanceRecord.STATUS_PRESENT,
                    AttendanceRecord.DECISION_CHARGE, 50000, false, null, null, now);
            assertEquals(1, attendanceRepo.recordAttendanceWithBilling(att, false, -1L, 0));

            assertEquals(1, invoiceRepo.generateDueInvoicesForStudent(accountA, studentUuid));
            List<Invoice> invoicesA = invoiceRepo.getInvoicesForAccount(accountA);
            assertEquals(1, invoicesA.size());
            String invoiceUuid = invoicesA.get(0).getId();

            FinancialTransaction payA = txnRepo.recordPaymentWithAllocations(
                    accountA, studentUuid, 30000, "bKash", "REF123", "Partial pay",
                    Collections.singletonMap(invoiceUuid, 30000)
            );
            assertNotNull(payA);

            String exportedJson;
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                JSONObject backup = BackupRestoreManager.exportAccountBackup(context, db, accountA);
                exportedJson = backup.toString();

                // Repeated import into the same account must be idempotent (0 inserted, 0 updated, 0 conflicts)
                BackupRestoreManager.ImportResult repeatResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountA, exportedJson);
                assertEquals(0, repeatResult.insertedCount);
                assertEquals(0, repeatResult.updatedCount);
                assertEquals(0, repeatResult.conflictCount);
                assertTrue(repeatResult.skippedCount > 0);

                // Cross-account import into Account B must preserve record UUIDs while scoping to Account B
                BackupRestoreManager.ImportResult crossResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountB, exportedJson);
                assertTrue(crossResult.insertedCount > 0);
                assertEquals(0, crossResult.conflictCount);
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }

            Student stuB = studentRepo.getStudentById(accountB, studentUuid);
            assertNotNull(stuB);
            assertEquals("Rahim", stuB.getName());

            Invoice invB = invoiceRepo.getInvoiceById(accountB, invoiceUuid);
            assertNotNull(invB);
            assertEquals(30000, invB.getPaidAmount());
            assertEquals(20000, invB.getOutstandingBalance());
        } finally {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                db.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " = ?", new String[]{accountA});
                db.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " = ?", new String[]{accountB});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }
    }

    @Test
    public void backupRestore_resolvesTimestampConflicts_validatesRelationships_andPreservesSecuritySettings() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalAccountStore accountStore = new LocalAccountStore(context);
        SessionManager sessionManager = new SessionManager(context);
        StudentRepository studentRepo = new StudentRepository();

        String accountId = UUID.randomUUID().toString();
        String studentId = UUID.randomUUID().toString();

        try {
            assertTrue(accountStore.saveAccount(new Account(accountId, "Original Tutor", "sectutor" + System.currentTimeMillis(), "origHash", "origSalt")));
            sessionManager.login(accountId);
            sessionManager.savePinSetup("pinHashSecret", true);

            long tBase = 1_700_000_000_000L;
            assertEquals(1, studentRepo.insertStudent(new Student(studentId, accountId, "STU-0001", "Initial Name", "8", null, null, null, null, true, tBase)));

            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                JSONObject backup = BackupRestoreManager.exportAccountBackup(context, db, accountId);
                JSONObject studentJson = backup.getJSONObject("tables")
                        .getJSONArray(StudentEntry.TABLE_NAME)
                        .getJSONObject(0);

                // 1. Equal timestamp (tBase == tBase) -> keep existing record ("Initial Name")
                studentJson.put(StudentEntry.COLUMN_NAME, "Equal Timestamp Name");
                studentJson.put(StudentEntry.COLUMN_LAST_MODIFIED, tBase);
                studentJson.put(StudentEntry.COLUMN_UPDATED_AT, tBase);
                BackupRestoreManager.ImportResult eqResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountId, backup.toString());
                assertEquals(0, eqResult.updatedCount);
                Student afterEq = studentRepo.getStudentById(accountId, studentId);
                assertNotNull(afterEq);
                assertEquals("Initial Name", afterEq.getName());

                // 2. Older timestamp (tBase - 5000 < tBase) -> keep existing record ("Initial Name")
                studentJson.put(StudentEntry.COLUMN_NAME, "Older Timestamp Name");
                studentJson.put(StudentEntry.COLUMN_LAST_MODIFIED, tBase - 5000L);
                studentJson.put(StudentEntry.COLUMN_UPDATED_AT, tBase - 5000L);
                BackupRestoreManager.ImportResult oldResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountId, backup.toString());
                assertEquals(0, oldResult.updatedCount);
                Student afterOld = studentRepo.getStudentById(accountId, studentId);
                assertNotNull(afterOld);
                assertEquals("Initial Name", afterOld.getName());

                // 3. Newer timestamp (tBase + 5000 > tBase) -> update existing record ("Newer Timestamp Name")
                studentJson.put(StudentEntry.COLUMN_NAME, "Newer Timestamp Name");
                studentJson.put(StudentEntry.COLUMN_LAST_MODIFIED, tBase + 5000L);
                studentJson.put(StudentEntry.COLUMN_UPDATED_AT, tBase + 5000L);
                BackupRestoreManager.ImportResult newResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountId, backup.toString());
                assertEquals(1, newResult.updatedCount);
                Student afterNew = studentRepo.getStudentById(accountId, studentId);
                assertNotNull(afterNew);
                assertEquals("Newer Timestamp Name", afterNew.getName());

                // 4. Verify security settings (password_hash, salt, PIN, biometric) were never overwritten
                Account reloadedAcc = accountStore.getAccountById(accountId);
                assertNotNull(reloadedAcc);
                assertEquals("origHash", reloadedAcc.getPasswordHash());
                assertEquals("origSalt", reloadedAcc.getSalt());
                assertEquals("pinHashSecret", sessionManager.getSavedPinHash());
                assertTrue(sessionManager.isBiometricEnabled());

                // 5. Broken foreign key relationship must fail pre-commit validation and roll back atomically
                studentJson.put(StudentEntry.COLUMN_NAME, "Should Rollback Name");
                studentJson.put(StudentEntry.COLUMN_LAST_MODIFIED, tBase + 10000L);

                JSONObject orphanInvoice = new JSONObject();
                orphanInvoice.put(InvoiceEntry.COLUMN_ID, UUID.randomUUID().toString());
                orphanInvoice.put(InvoiceEntry.COLUMN_STUDENT_ID, UUID.randomUUID().toString()); // non-existent student
                orphanInvoice.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, 50000);
                backup.getJSONObject("tables").getJSONArray(InvoiceEntry.TABLE_NAME).put(orphanInvoice);

                try {
                    BackupRestoreManager.importAccountBackupFromJson(context, db, accountId, backup.toString());
                    fail("Expected IllegalArgumentException for missing student_id foreign key");
                } catch (IllegalArgumentException expected) {
                    // Expected pre-commit validation failure
                }

                Student afterRollback = studentRepo.getStudentById(accountId, studentId);
                assertNotNull(afterRollback);
                assertEquals("Newer Timestamp Name", afterRollback.getName());
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        } finally {
            sessionManager.logout();
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                db.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " = ?", new String[]{accountId});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }
    }

    @Test
    public void backupRestore_neverOverwritesExistingFinancialRecords_andReportsConflicts() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalAccountStore accountStore = new LocalAccountStore(context);
        StudentRepository studentRepo = new StudentRepository();
        InvoiceRepository invoiceRepo = new InvoiceRepository();
        TransactionRepository txnRepo = new TransactionRepository();

        String accountId = UUID.randomUUID().toString();
        String studentId = UUID.randomUUID().toString();
        String invoiceId = UUID.randomUUID().toString();

        try {
            assertTrue(accountStore.saveAccount(new Account(accountId, "Fin Guard Tutor", "finguard" + System.currentTimeMillis(), "h", "s")));
            long now = System.currentTimeMillis();
            assertEquals(1, studentRepo.insertStudent(new Student(studentId, accountId, "STU-0001", "Nabil", "10", null, null, null, null, true, now)));

            SQLiteDatabase setupDb = DatabaseManager.getInstance().openDatabase();
            try {
                ContentValues iv = new ContentValues();
                iv.put(InvoiceEntry.COLUMN_ID, invoiceId);
                iv.put(InvoiceEntry.COLUMN_ACCOUNT_ID, accountId);
                iv.put(InvoiceEntry.COLUMN_STUDENT_ID, studentId);
                iv.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, "TS-2026-000001");
                iv.put(InvoiceEntry.COLUMN_BILLING_PERIOD, "Nov 2026");
                iv.put(InvoiceEntry.COLUMN_DUE_DATE, now + 86400000L);
                iv.put(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, 100000);
                iv.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, 100000);
                iv.put(InvoiceEntry.COLUMN_PAID_AMOUNT, 0);
                iv.put(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_UNPAID);
                iv.put(InvoiceEntry.COLUMN_CREATED_AT, now);
                iv.put(InvoiceEntry.COLUMN_UPDATED_AT, now);
                iv.put(InvoiceEntry.COLUMN_LAST_MODIFIED, now);
                setupDb.insertOrThrow(InvoiceEntry.TABLE_NAME, null, iv);
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }

            // Record initial payment of 400.00 BDT (40,000 minor units)
            FinancialTransaction originalPay = txnRepo.recordPaymentWithAllocations(
                    accountId, studentId, 40000, "bKash", "REF-ORIG", "Original payment",
                    Collections.singletonMap(invoiceId, 40000)
            );
            assertNotNull(originalPay);

            SQLiteDatabase testDb = DatabaseManager.getInstance().openDatabase();
            try {
                JSONObject backup = BackupRestoreManager.exportAccountBackup(context, testDb, accountId);
                JSONObject tables = backup.getJSONObject("tables");

                // Attempt to tamper with the existing transaction and allocation using a newer last_modified timestamp
                JSONObject txnJson = tables.getJSONArray(TransactionEntry.TABLE_NAME).getJSONObject(0);
                txnJson.put(TransactionEntry.COLUMN_AMOUNT_MINOR, 90000);
                txnJson.put(TransactionEntry.COLUMN_METHOD, "TamperedMethod");
                txnJson.put(TransactionEntry.COLUMN_LAST_MODIFIED, now + 500000L);
                txnJson.put(TransactionEntry.COLUMN_UPDATED_AT, now + 500000L);

                JSONObject allocJson = tables.getJSONArray(PaymentAllocationEntry.TABLE_NAME).getJSONObject(0);
                allocJson.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, 90000);
                allocJson.put(PaymentAllocationEntry.COLUMN_LAST_MODIFIED, now + 500000L);
                allocJson.put(PaymentAllocationEntry.COLUMN_UPDATED_AT, now + 500000L);

                // Also include a genuinely new payment of 250.00 BDT (25,000 minor units) in the same backup
                String newTxnId = UUID.randomUUID().toString();
                String newAllocId = UUID.randomUUID().toString();
                JSONObject newTxn = new JSONObject();
                newTxn.put(TransactionEntry.COLUMN_ID, newTxnId);
                newTxn.put(TransactionEntry.COLUMN_STUDENT_ID, studentId);
                newTxn.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, "TXN-2026-000099");
                newTxn.put(TransactionEntry.COLUMN_TYPE, FinancialTransaction.TYPE_PAYMENT);
                newTxn.put(TransactionEntry.COLUMN_AMOUNT_MINOR, 25000);
                newTxn.put(TransactionEntry.COLUMN_TIMESTAMP, now + 1000L);
                newTxn.put(TransactionEntry.COLUMN_METHOD, "Cash");
                newTxn.put(TransactionEntry.COLUMN_IS_REVERSED, 0);
                newTxn.put(TransactionEntry.COLUMN_CREATED_AT, now + 1000L);
                newTxn.put(TransactionEntry.COLUMN_LAST_MODIFIED, now + 1000L);
                tables.getJSONArray(TransactionEntry.TABLE_NAME).put(newTxn);

                JSONObject newAlloc = new JSONObject();
                newAlloc.put(PaymentAllocationEntry.COLUMN_ID, newAllocId);
                newAlloc.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, newTxnId);
                newAlloc.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invoiceId);
                newAlloc.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, 25000);
                newAlloc.put(PaymentAllocationEntry.COLUMN_CREATED_AT, now + 1000L);
                newAlloc.put(PaymentAllocationEntry.COLUMN_LAST_MODIFIED, now + 1000L);
                tables.getJSONArray(PaymentAllocationEntry.TABLE_NAME).put(newAlloc);

                BackupRestoreManager.ImportResult result =
                        BackupRestoreManager.importAccountBackupFromJson(context, testDb, accountId, backup.toString());

                // Existing records must NOT be updated; conflicts must be reported; new records must be inserted
                assertEquals(2, result.insertedCount);
                assertEquals(0, result.updatedCount);
                assertEquals(2, result.conflictCount);
                assertTrue(result.hasConflicts());
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }

            // Verify original payment (40,000) was preserved and new payment (25,000) was added -> total 65,000
            Invoice updatedInvoice = invoiceRepo.getInvoiceById(accountId, invoiceId);
            assertNotNull(updatedInvoice);
            assertEquals(65000, updatedInvoice.getPaidAmount());
            assertEquals(35000, updatedInvoice.getOutstandingBalance());
            assertEquals(Invoice.STATUS_PARTIAL, updatedInvoice.getStatus());
            assertEquals(65000, invoiceRepo.getTotalRevenueForAccount(accountId));
        } finally {
            SQLiteDatabase cleanDb = DatabaseManager.getInstance().openDatabase();
            try {
                cleanDb.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " = ?", new String[]{accountId});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }
    }

    @Test
    public void reconciliation_neverInventsSyntheticPaymentsWhenAllocationsMissing_andBridgesLegacyPaymentsSafely() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalAccountStore accountStore = new LocalAccountStore(context);
        StudentRepository studentRepo = new StudentRepository();
        InvoiceRepository invoiceRepo = new InvoiceRepository();
        PaymentRepository paymentRepo = new PaymentRepository();

        String accountId = UUID.randomUUID().toString();
        String studentId = UUID.randomUUID().toString();
        String unbackedInvoiceId = UUID.randomUUID().toString();
        String legacyInvoiceId = UUID.randomUUID().toString();
        String legacyPaymentId = UUID.randomUUID().toString();

        try {
            assertTrue(accountStore.saveAccount(new Account(accountId, "Recon Tutor", "recon" + System.currentTimeMillis(), "h", "s")));
            long now = System.currentTimeMillis();
            assertEquals(1, studentRepo.insertStudent(new Student(studentId, accountId, "STU-0002", "Tamim", "9", null, null, null, null, true, now)));

            SQLiteDatabase setupDb = DatabaseManager.getInstance().openDatabase();
            try {
                // 1. Insert an invoice with paid_amount = 50000 and status = PAID, but ZERO allocations and ZERO payments
                ContentValues iv1 = new ContentValues();
                iv1.put(InvoiceEntry.COLUMN_ID, unbackedInvoiceId);
                iv1.put(InvoiceEntry.COLUMN_ACCOUNT_ID, accountId);
                iv1.put(InvoiceEntry.COLUMN_STUDENT_ID, studentId);
                iv1.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, "TS-2026-000010");
                iv1.put(InvoiceEntry.COLUMN_BILLING_PERIOD, "Nov 2026");
                iv1.put(InvoiceEntry.COLUMN_DUE_DATE, now + 86400000L);
                iv1.put(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, 50000);
                iv1.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, 50000);
                iv1.put(InvoiceEntry.COLUMN_PAID_AMOUNT, 50000);
                iv1.put(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_PAID);
                iv1.put(InvoiceEntry.COLUMN_CREATED_AT, now);
                iv1.put(InvoiceEntry.COLUMN_UPDATED_AT, now);
                iv1.put(InvoiceEntry.COLUMN_LAST_MODIFIED, now);
                setupDb.insertOrThrow(InvoiceEntry.TABLE_NAME, null, iv1);

                // 2. Insert a second invoice backed by a genuine legacy payment in `payments`
                ContentValues iv2 = new ContentValues();
                iv2.put(InvoiceEntry.COLUMN_ID, legacyInvoiceId);
                iv2.put(InvoiceEntry.COLUMN_ACCOUNT_ID, accountId);
                iv2.put(InvoiceEntry.COLUMN_STUDENT_ID, studentId);
                iv2.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, "TS-2026-000011");
                iv2.put(InvoiceEntry.COLUMN_BILLING_PERIOD, "Nov 2026");
                iv2.put(InvoiceEntry.COLUMN_DUE_DATE, now + 86400000L);
                iv2.put(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, 40000);
                iv2.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, 40000);
                iv2.put(InvoiceEntry.COLUMN_PAID_AMOUNT, 25000);
                iv2.put(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_PARTIAL);
                iv2.put(InvoiceEntry.COLUMN_CREATED_AT, now);
                iv2.put(InvoiceEntry.COLUMN_UPDATED_AT, now);
                iv2.put(InvoiceEntry.COLUMN_LAST_MODIFIED, now);
                setupDb.insertOrThrow(InvoiceEntry.TABLE_NAME, null, iv2);
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }

            assertTrue(paymentRepo.insertPayment(new Payment(legacyPaymentId, accountId, legacyInvoiceId, 25000, now, "Cash", now)));

            // Before reconciliation: unbacked invoice must derive paidAmount = 0 and UNPAID; legacy invoice derives 25000
            Invoice unbackedBefore = invoiceRepo.getInvoiceById(accountId, unbackedInvoiceId);
            assertNotNull(unbackedBefore);
            assertEquals(0, unbackedBefore.getPaidAmount());
            assertEquals(Invoice.STATUS_UNPAID, unbackedBefore.getStatus());

            Invoice legacyBefore = invoiceRepo.getInvoiceById(accountId, legacyInvoiceId);
            assertNotNull(legacyBefore);
            assertEquals(25000, legacyBefore.getPaidAmount());
            assertEquals(Invoice.STATUS_PARTIAL, legacyBefore.getStatus());

            // Run reconciliation multiple times: must never create phantom payments for unbackedInvoiceId or duplicate legacyPaymentId
            invoiceRepo.reconcileAccountFinancialData(accountId);
            invoiceRepo.reconcileAccountFinancialData(accountId);

            assertEquals(0, countAllocationsForInvoice(accountId, unbackedInvoiceId));
            assertEquals(1, countAllocationsForInvoice(accountId, legacyInvoiceId));
            assertEquals(25000, invoiceRepo.getTotalRevenueForAccount(accountId));

            Invoice unbackedAfter = invoiceRepo.getInvoiceById(accountId, unbackedInvoiceId);
            assertNotNull(unbackedAfter);
            assertEquals(0, unbackedAfter.getPaidAmount());
            assertEquals(Invoice.STATUS_UNPAID, unbackedAfter.getStatus());
        } finally {
            SQLiteDatabase cleanDb = DatabaseManager.getInstance().openDatabase();
            try {
                cleanDb.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " = ?", new String[]{accountId});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }
    }

    private int countAllocationsForInvoice(String accountId, String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + PaymentAllocationEntry.TABLE_NAME +
                        " WHERE " + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                        PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ?",
                new String[]{accountId, invoiceId})) {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            return 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }
}
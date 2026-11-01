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
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AttendanceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.LessonEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Lesson;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository;
import com.abdussamadcodes.tutorsync.repository.local.EnrollmentRepository;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.LessonRepository;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.repository.local.TransactionRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.util.BackupRestoreManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
                iv.put(InvoiceEntry.COLUMN_UPDATED_AT, now);
                iv.put(InvoiceEntry.COLUMN_LAST_MODIFIED, now);
                db.insertOrThrow(InvoiceEntry.TABLE_NAME, null, iv);
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }

            FinancialTransaction payment = txnRepo.recordPaymentWithAllocations(
                    accountId, studentId, 100000, "Cash", null, null,
                    Collections.singletonMap(invoiceId, 100000)
            );
            assertNotNull(payment);

            Invoice afterPay = invoiceRepo.getInvoiceById(accountId, invoiceId);
            assertNotNull(afterPay);
            assertEquals(100000, afterPay.getPaidAmount());
            assertEquals(0, afterPay.getOutstandingBalance());
            assertEquals(Invoice.STATUS_PAID, afterPay.getStatus());

            assertTrue(txnRepo.recordRefundForTransaction(accountId, payment.getId(), 40000, "Partial refund"));

            Invoice afterPartialRefund = invoiceRepo.getInvoiceById(accountId, invoiceId);
            assertNotNull(afterPartialRefund);
            assertEquals(60000, afterPartialRefund.getPaidAmount());
            assertEquals(40000, afterPartialRefund.getOutstandingBalance());
            assertEquals(Invoice.STATUS_PARTIAL, afterPartialRefund.getStatus());

            assertFalse(txnRepo.recordRefundForTransaction(accountId, payment.getId(), 100000, "Over-refund attempt"));

            assertTrue(txnRepo.recordRefundForTransaction(accountId, payment.getId(), 60000, "Remaining refund"));

            Invoice afterFullRefund = invoiceRepo.getInvoiceById(accountId, invoiceId);
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

                BackupRestoreManager.ImportResult repeatResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountA, exportedJson);
                assertEquals(0, repeatResult.insertedCount);
                assertEquals(0, repeatResult.updatedCount);
                assertTrue(repeatResult.skippedCount > 0);

                BackupRestoreManager.ImportResult crossResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountB, exportedJson);
                assertTrue(crossResult.insertedCount > 0);
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }

            Student stuB = studentRepo.getStudentById(accountB, studentUuid);
            assertNotNull(stuB);
            assertEquals(studentUuid, stuB.getId());
            assertEquals("Rahim", stuB.getName());

            Subject subB = subjectRepo.getSubjectById(accountB, subjectUuid);
            assertNotNull(subB);
            assertEquals(subjectUuid, subB.getId());

            List<String> enrolledSubB = enrollmentRepo.getEnrolledSubjectIdsForStudent(accountB, studentUuid);
            assertEquals(1, enrolledSubB.size());
            assertEquals(subjectUuid, enrolledSubB.get(0));

            Invoice invB = invoiceRepo.getInvoiceById(accountB, invoiceUuid);
            assertNotNull(invB);
            assertEquals(invoiceUuid, invB.getId());
            assertEquals(50000, invB.getTotalAmount());
            assertEquals(30000, invB.getPaidAmount());
            assertEquals(20000, invB.getOutstandingBalance());

            FinancialTransaction payB = txnRepo.recordPaymentWithAllocations(
                    accountB, studentUuid, 20000, "Cash", null, "Pay rest in B",
                    Collections.singletonMap(invoiceUuid, 20000)
            );
            assertNotNull(payB);

            Invoice invBAfter = invoiceRepo.getInvoiceById(accountB, invoiceUuid);
            Invoice invAAfter = invoiceRepo.getInvoiceById(accountA, invoiceUuid);
            assertNotNull(invBAfter);
            assertNotNull(invAAfter);
            assertEquals(50000, invBAfter.getPaidAmount());
            assertEquals(Invoice.STATUS_PAID, invBAfter.getStatus());
            assertEquals(30000, invAAfter.getPaidAmount());
            assertEquals(Invoice.STATUS_PARTIAL, invAAfter.getStatus());
            assertEquals(30000, invoiceRepo.getTotalRevenueForAccount(accountA));
            assertEquals(50000, invoiceRepo.getTotalRevenueForAccount(accountB));
        } finally {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                db.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " IN (?, ?)", new String[]{accountA, accountB});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }
    }

    @Test
    public void backupRestore_resolvesTimestampConflicts_validatesRelationships_andPreservesSecuritySettings() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalAccountStore accountStore = new LocalAccountStore(context);
        StudentRepository studentRepo = new StudentRepository();
        SessionManager sessionManager = new SessionManager(context);

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

                studentJson.put(StudentEntry.COLUMN_NAME, "Equal Timestamp Name");
                studentJson.put(StudentEntry.COLUMN_LAST_MODIFIED, tBase);
                studentJson.put(StudentEntry.COLUMN_UPDATED_AT, tBase);
                BackupRestoreManager.ImportResult eqResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountId, backup.toString());
                assertEquals(0, eqResult.updatedCount);
                Student afterEq = studentRepo.getStudentById(accountId, studentId);
                assertNotNull(afterEq);
                assertEquals("Initial Name", afterEq.getName());

                studentJson.put(StudentEntry.COLUMN_NAME, "Older Timestamp Name");
                studentJson.put(StudentEntry.COLUMN_LAST_MODIFIED, tBase - 5000L);
                studentJson.put(StudentEntry.COLUMN_UPDATED_AT, tBase - 5000L);
                BackupRestoreManager.ImportResult oldResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountId, backup.toString());
                assertEquals(0, oldResult.updatedCount);
                Student afterOld = studentRepo.getStudentById(accountId, studentId);
                assertNotNull(afterOld);
                assertEquals("Initial Name", afterOld.getName());

                studentJson.put(StudentEntry.COLUMN_NAME, "Newer Timestamp Name");
                studentJson.put(StudentEntry.COLUMN_LAST_MODIFIED, tBase + 5000L);
                studentJson.put(StudentEntry.COLUMN_UPDATED_AT, tBase + 5000L);
                BackupRestoreManager.ImportResult newResult =
                        BackupRestoreManager.importAccountBackupFromJson(context, db, accountId, backup.toString());
                assertEquals(1, newResult.updatedCount);
                Student afterNew = studentRepo.getStudentById(accountId, studentId);
                assertNotNull(afterNew);
                assertEquals("Newer Timestamp Name", afterNew.getName());

                Account reloadedAcc = accountStore.getAccountById(accountId);
                assertNotNull(reloadedAcc);
                assertEquals("origHash", reloadedAcc.getPasswordHash());
                assertEquals("origSalt", reloadedAcc.getSalt());
                assertEquals("pinHashSecret", sessionManager.getSavedPinHash());
                assertTrue(sessionManager.isBiometricEnabled());

                studentJson.put(StudentEntry.COLUMN_NAME, "Should Rollback Name");
                studentJson.put(StudentEntry.COLUMN_LAST_MODIFIED, tBase + 99999L);

                JSONObject badInvoice = new JSONObject();
                badInvoice.put(InvoiceEntry.COLUMN_ID, UUID.randomUUID().toString());
                badInvoice.put(InvoiceEntry.COLUMN_STUDENT_ID, "non-existent-student-uuid");
                badInvoice.put(InvoiceEntry.COLUMN_BILLING_PERIOD, "Nov 2026");
                badInvoice.put(InvoiceEntry.COLUMN_DUE_DATE, tBase + 86400000L);
                badInvoice.put(InvoiceEntry.COLUMN_TOTAL_AMOUNT, 50000);
                badInvoice.put(InvoiceEntry.COLUMN_PAID_AMOUNT, 0);
                badInvoice.put(InvoiceEntry.COLUMN_STATUS, Invoice.STATUS_UNPAID);
                badInvoice.put(InvoiceEntry.COLUMN_CREATED_AT, tBase);
                badInvoice.put(InvoiceEntry.COLUMN_LAST_MODIFIED, tBase);

                JSONArray invArray = new JSONArray();
                invArray.put(badInvoice);
                backup.getJSONObject("tables").put(InvoiceEntry.TABLE_NAME, invArray);

                boolean threwValidationException = false;
                try {
                    BackupRestoreManager.importAccountBackupFromJson(context, db, accountId, backup.toString());
                } catch (IllegalArgumentException expected) {
                    threwValidationException = true;
                }
                assertTrue("Import with broken FK must throw IllegalArgumentException", threwValidationException);

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
    public void scheduleEditAndDelete_preservesUuids_rollsBackOnValidationFailure_andProtectsHistoricalData() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalAccountStore accountStore = new LocalAccountStore(context);
        StudentRepository studentRepo = new StudentRepository();
        SubjectRepository subjectRepo = new SubjectRepository();
        EnrollmentRepository enrollmentRepo = new EnrollmentRepository();
        ScheduleRepository scheduleRepo = new ScheduleRepository();
        OccurrenceRepository occurrenceRepo = new OccurrenceRepository();
        AttendanceRepository attendanceRepo = new AttendanceRepository();
        LessonRepository lessonRepo = new LessonRepository();

        String accountId = UUID.randomUUID().toString();
        String studentId = UUID.randomUUID().toString();
        String subjectId = UUID.randomUUID().toString();
        String scheduleId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        String recordedOccId = UUID.randomUUID().toString();
        String attendanceId = UUID.randomUUID().toString();
        String lessonId = UUID.randomUUID().toString();

        try {
            assertTrue(accountStore.saveAccount(new Account(accountId, "Schedule Tutor", "sched" + System.currentTimeMillis(), "h", "s")));
            long now = System.currentTimeMillis();
            assertEquals(1, studentRepo.insertStudent(new Student(studentId, accountId, "STU-0100", "Karim", "10", "Ideal School", null, null, null, true, now)));
            assertTrue(subjectRepo.insertSubject(new Subject(subjectId, accountId, "Mathematics", "SSC", true, now)));
            assertTrue(enrollmentRepo.enrollStudentInSubject(accountId, studentId, subjectId));

            // 1. Insert initial recurring schedule on Monday (dayOfWeek = 2)
            RecurringSchedule initial = new RecurringSchedule(
                    scheduleId, accountId, studentId, subjectId, groupId,
                    2, 960, 60, now - 86400000L, now + 31536000000L,
                    RecurringSchedule.MODEL_PER_CLASS, 40000, 1, now
            );
            assertTrue(scheduleRepo.insertScheduleWithFee(initial, groupId, RecurringSchedule.MODEL_PER_CLASS, 40000, 1));

            // 2. Insert a future occurrence with recorded attendance and lesson notes
            long futureRecordedDate = now + (2L * 86400000L);
            ClassOccurrence recOcc = new ClassOccurrence(recordedOccId, accountId, studentId, subjectId, scheduleId, futureRecordedDate, 960, 60, now);
            assertTrue(occurrenceRepo.insertOccurrenceWithGroup(recOcc, groupId));

            AttendanceRecord att = new AttendanceRecord(attendanceId, accountId, recordedOccId, AttendanceRecord.STATUS_PRESENT,
                    AttendanceRecord.DECISION_CHARGE, 40000, false, null, null, now);
            assertEquals(1, attendanceRepo.recordAttendanceWithBilling(att, false, -1L, 0));

            Lesson lesson = new Lesson(lessonId, accountId, studentId, recordedOccId, "Calculus", "Ex 4.1", "Did well", now);
            assertTrue(lessonRepo.saveLesson(lesson));

            // 3. Invalid edit (empty days list) must fail validation BEFORE deleting existing schedule
            boolean invalidEditResult = scheduleRepo.saveScheduleFormAtomically(
                    accountId, true, groupId, Collections.singletonList(scheduleId),
                    Collections.singletonList(studentId), Collections.singletonList(subjectId),
                    true, Collections.emptyList(), -1L, 1020, 90,
                    RecurringSchedule.MODEL_PER_CLASS, 50000, 1
            );
            assertFalse(invalidEditResult);

            List<RecurringSchedule> afterInvalidEdit = scheduleRepo.getSchedulesForAccount(accountId);
            assertEquals(1, afterInvalidEdit.size());
            assertEquals(scheduleId, afterInvalidEdit.get(0).getId());
            assertEquals(960, afterInvalidEdit.get(0).getStartTimeMinutes());

            // 4. Valid edit (change time to 1020, duration to 90, add Wednesday=4) must preserve existing scheduleId for Monday=2
            boolean validEditResult = scheduleRepo.saveScheduleFormAtomically(
                    accountId, true, groupId, Collections.singletonList(scheduleId),
                    Collections.singletonList(studentId), Collections.singletonList(subjectId),
                    true, Arrays.asList(2, 4), -1L, 1020, 90,
                    RecurringSchedule.MODEL_PER_CLASS, 50000, 1
            );
            assertTrue(validEditResult);

            List<RecurringSchedule> afterValidEdit = scheduleRepo.getSchedulesForAccount(accountId);
            assertEquals(2, afterValidEdit.size());
            boolean foundOriginalUuid = false;
            for (RecurringSchedule rs : afterValidEdit) {
                if (rs.getDayOfWeek() == 2) {
                    assertEquals(scheduleId, rs.getId());
                    assertEquals(1020, rs.getStartTimeMinutes());
                    assertEquals(90, rs.getDurationMinutes());
                    assertEquals(50000, rs.getFeeAmountMinor());
                    foundOriginalUuid = true;
                }
            }
            assertTrue("Existing schedule UUID must be preserved on edit", foundOriginalUuid);

            // 5. Verify recorded occurrence, attendance, and lesson were NOT destroyed by edit or delete
            assertFalse("Recorded occurrence must not be deleted", occurrenceRepo.deleteOccurrence(accountId, recordedOccId));

            List<String> currentSchedIds = Arrays.asList(afterValidEdit.get(0).getId(), afterValidEdit.get(1).getId());
            assertTrue(scheduleRepo.deleteScheduleGroupSafely(accountId, currentSchedIds));
            assertTrue(scheduleRepo.getSchedulesForAccount(accountId).isEmpty());

            // 6. Deleting Subject & Student with historical records soft-deletes them and keeps attendance/lesson intact
            assertTrue(subjectRepo.deleteSubject(accountId, subjectId));
            assertTrue(studentRepo.deleteStudent(accountId, studentId));

            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                try (Cursor oc = db.query(OccurrenceEntry.TABLE_NAME, new String[]{OccurrenceEntry.COLUMN_ID},
                        OccurrenceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + OccurrenceEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, recordedOccId}, null, null, null)) {
                    assertTrue("Historical occurrence must survive schedule, subject, and student deletion", oc.moveToFirst());
                }
                try (Cursor ac = db.query(AttendanceEntry.TABLE_NAME, new String[]{AttendanceEntry.COLUMN_ID},
                        AttendanceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + AttendanceEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, attendanceId}, null, null, null)) {
                    assertTrue("Historical attendance must survive", ac.moveToFirst());
                }
                try (Cursor lc = db.query(LessonEntry.TABLE_NAME, new String[]{LessonEntry.COLUMN_ID},
                        LessonEntry.COLUMN_ACCOUNT_ID + " = ? AND " + LessonEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, lessonId}, null, null, null)) {
                    assertTrue("Historical lesson must survive", lc.moveToFirst());
                }
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        } finally {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                db.delete(AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ID + " = ?", new String[]{accountId});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }
    }
}
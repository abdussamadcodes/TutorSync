package com.abdussamadcodes.tutorsync.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;

public class TutorSyncDbHelper extends SQLiteOpenHelper {

    private static volatile boolean sSchemaVerifiedThisProcess = false;

    private static final String SQL_CREATE_ACCOUNTS =
            "CREATE TABLE IF NOT EXISTS " + AccountEntry.TABLE_NAME + " (" +
                    AccountEntry.COLUMN_ID + " TEXT PRIMARY KEY NOT NULL, " +
                    AccountEntry.COLUMN_FULL_NAME + " TEXT NOT NULL, " +
                    AccountEntry.COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, " +
                    AccountEntry.COLUMN_PASSWORD_HASH + " TEXT NOT NULL, " +
                    AccountEntry.COLUMN_SALT + " TEXT NOT NULL, " +
                    AccountEntry.COLUMN_PHONE + " TEXT, " +
                    AccountEntry.COLUMN_EMAIL + " TEXT, " +
                    AccountEntry.COLUMN_ADDRESS + " TEXT, " +
                    AccountEntry.COLUMN_DOB + " TEXT, " +
                    AccountEntry.COLUMN_PROFILE_PIC_PATH + " TEXT, " +
                    AccountEntry.COLUMN_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'LOCAL_ONLY', " +
                    AccountEntry.COLUMN_SYNC_VERSION + " INTEGER NOT NULL DEFAULT 1, " +
                    AccountEntry.COLUMN_IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                    AccountEntry.COLUMN_IS_DEMO + " INTEGER NOT NULL DEFAULT 0, " +
                    AccountEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                    AccountEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                    AccountEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0)";

    private static String buildCreateStudentsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                StudentEntry.COLUMN_ID + " TEXT NOT NULL, " +
                StudentEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " TEXT, " +
                StudentEntry.COLUMN_NAME + " TEXT NOT NULL, " +
                StudentEntry.COLUMN_GRADE + " TEXT, " +
                StudentEntry.COLUMN_SCHOOL + " TEXT, " +
                StudentEntry.COLUMN_GUARDIAN_NAME + " TEXT, " +
                StudentEntry.COLUMN_GUARDIAN_PHONE + " TEXT, " +
                StudentEntry.COLUMN_ADDRESS + " TEXT, " +
                StudentEntry.COLUMN_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, " +
                StudentEntry.COLUMN_IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                StudentEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                StudentEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                StudentEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + StudentEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "UNIQUE(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + "))";
    }

    private static String buildCreateSubjectsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                SubjectEntry.COLUMN_ID + " TEXT NOT NULL, " +
                SubjectEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                SubjectEntry.COLUMN_TITLE + " TEXT NOT NULL, " +
                SubjectEntry.COLUMN_LEVEL + " TEXT, " +
                SubjectEntry.COLUMN_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, " +
                SubjectEntry.COLUMN_IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                SubjectEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                SubjectEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                SubjectEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + SubjectEntry.COLUMN_ACCOUNT_ID + ", " + SubjectEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + SubjectEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreateStudentSubjectsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                StudentSubjectEntry.COLUMN_ID + " TEXT NOT NULL, " +
                StudentSubjectEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                StudentSubjectEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                StudentSubjectEntry.COLUMN_SUBJECT_ID + " TEXT NOT NULL, " +
                StudentSubjectEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                StudentSubjectEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                StudentSubjectEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + StudentSubjectEntry.COLUMN_ACCOUNT_ID + ", " + StudentSubjectEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + StudentSubjectEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + StudentSubjectEntry.COLUMN_ACCOUNT_ID + ", " + StudentSubjectEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + StudentSubjectEntry.COLUMN_ACCOUNT_ID + ", " + StudentSubjectEntry.COLUMN_SUBJECT_ID + ") REFERENCES " +
                SubjectEntry.TABLE_NAME + "(" + SubjectEntry.COLUMN_ACCOUNT_ID + ", " + SubjectEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "UNIQUE(" + StudentSubjectEntry.COLUMN_ACCOUNT_ID + ", " + StudentSubjectEntry.COLUMN_STUDENT_ID + ", " + StudentSubjectEntry.COLUMN_SUBJECT_ID + "))";
    }

    private static String buildCreateScheduleFeeHistorySql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                ScheduleFeeHistoryEntry.COLUMN_ID + " TEXT NOT NULL, " +
                ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                ScheduleFeeHistoryEntry.COLUMN_STUDENT_ID + " TEXT, " +
                ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " TEXT, " +
                ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL + " TEXT NOT NULL, " +
                ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                ScheduleFeeHistoryEntry.COLUMN_ANCHOR_DAY + " INTEGER, " +
                ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " INTEGER NOT NULL, " +
                ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " INTEGER, " +
                ScheduleFeeHistoryEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                ScheduleFeeHistoryEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                ScheduleFeeHistoryEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + ", " + ScheduleFeeHistoryEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreateSchedulesSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                ScheduleEntry.COLUMN_ID + " TEXT NOT NULL, " +
                ScheduleEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                ScheduleEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                ScheduleEntry.COLUMN_SUBJECT_ID + " TEXT NOT NULL, " +
                ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + " TEXT, " +
                ScheduleEntry.COLUMN_DAY_OF_WEEK + " INTEGER NOT NULL, " +
                ScheduleEntry.COLUMN_START_TIME_MINUTES + " INTEGER NOT NULL, " +
                ScheduleEntry.COLUMN_DURATION_MINUTES + " INTEGER NOT NULL, " +
                ScheduleEntry.COLUMN_START_DATE + " INTEGER NOT NULL, " +
                ScheduleEntry.COLUMN_END_DATE + " INTEGER NOT NULL, " +
                ScheduleEntry.COLUMN_BILLING_MODEL + " TEXT NOT NULL DEFAULT 'MONTHLY', " +
                ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR + " INTEGER NOT NULL DEFAULT 0, " +
                ScheduleEntry.COLUMN_ANCHOR_DAY + " INTEGER NOT NULL DEFAULT 1, " +
                ScheduleEntry.COLUMN_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, " +
                ScheduleEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                ScheduleEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                ScheduleEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + ScheduleEntry.COLUMN_ACCOUNT_ID + ", " + ScheduleEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + ScheduleEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + ScheduleEntry.COLUMN_ACCOUNT_ID + ", " + ScheduleEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + ScheduleEntry.COLUMN_ACCOUNT_ID + ", " + ScheduleEntry.COLUMN_SUBJECT_ID + ") REFERENCES " +
                SubjectEntry.TABLE_NAME + "(" + SubjectEntry.COLUMN_ACCOUNT_ID + ", " + SubjectEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreateOccurrencesSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                OccurrenceEntry.COLUMN_ID + " TEXT NOT NULL, " +
                OccurrenceEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                OccurrenceEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                OccurrenceEntry.COLUMN_SUBJECT_ID + " TEXT, " +
                OccurrenceEntry.COLUMN_SCHEDULE_ID + " TEXT, " +
                OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID + " TEXT, " +
                OccurrenceEntry.COLUMN_DATE + " INTEGER NOT NULL, " +
                OccurrenceEntry.COLUMN_START_TIME_MINUTES + " INTEGER NOT NULL, " +
                OccurrenceEntry.COLUMN_DURATION_MINUTES + " INTEGER NOT NULL, " +
                OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID + " TEXT, " +
                OccurrenceEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                OccurrenceEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                OccurrenceEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ", " + OccurrenceEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ", " + OccurrenceEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ", " + OccurrenceEntry.COLUMN_SCHEDULE_ID + ") REFERENCES " +
                ScheduleEntry.TABLE_NAME + "(" + ScheduleEntry.COLUMN_ACCOUNT_ID + ", " + ScheduleEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreateAttendanceSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                AttendanceEntry.COLUMN_ID + " TEXT NOT NULL, " +
                AttendanceEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                AttendanceEntry.COLUMN_OCCURRENCE_ID + " TEXT NOT NULL, " +
                AttendanceEntry.COLUMN_STATUS + " TEXT NOT NULL, " +
                AttendanceEntry.COLUMN_CHARGE_DECISION + " TEXT NOT NULL DEFAULT 'CHARGE', " +
                AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + " INTEGER NOT NULL DEFAULT 0, " +
                AttendanceEntry.COLUMN_NEEDS_RESCHEDULE + " INTEGER NOT NULL DEFAULT 0, " +
                AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID + " TEXT, " +
                AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " TEXT, " +
                AttendanceEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                AttendanceEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                AttendanceEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + AttendanceEntry.COLUMN_ACCOUNT_ID + ", " + AttendanceEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + AttendanceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + AttendanceEntry.COLUMN_ACCOUNT_ID + ", " + AttendanceEntry.COLUMN_OCCURRENCE_ID + ") REFERENCES " +
                OccurrenceEntry.TABLE_NAME + "(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ", " + OccurrenceEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "UNIQUE(" + AttendanceEntry.COLUMN_ACCOUNT_ID + ", " + AttendanceEntry.COLUMN_OCCURRENCE_ID + "))";
    }

    private static String buildCreateLessonsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                LessonEntry.COLUMN_ID + " TEXT NOT NULL, " +
                LessonEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                LessonEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                LessonEntry.COLUMN_OCCURRENCE_ID + " TEXT, " +
                LessonEntry.COLUMN_TOPIC_COVERED + " TEXT, " +
                LessonEntry.COLUMN_HOMEWORK + " TEXT, " +
                LessonEntry.COLUMN_TUTOR_NOTES + " TEXT, " +
                LessonEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                LessonEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                LessonEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + LessonEntry.COLUMN_ACCOUNT_ID + ", " + LessonEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + LessonEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + LessonEntry.COLUMN_ACCOUNT_ID + ", " + LessonEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + LessonEntry.COLUMN_ACCOUNT_ID + ", " + LessonEntry.COLUMN_OCCURRENCE_ID + ") REFERENCES " +
                OccurrenceEntry.TABLE_NAME + "(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ", " + OccurrenceEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreateFeeAgreementsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                FeeAgreementEntry.COLUMN_ID + " TEXT NOT NULL, " +
                FeeAgreementEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                FeeAgreementEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                FeeAgreementEntry.COLUMN_FEE_TYPE + " TEXT NOT NULL, " +
                FeeAgreementEntry.COLUMN_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                FeeAgreementEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                FeeAgreementEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                FeeAgreementEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + FeeAgreementEntry.COLUMN_ACCOUNT_ID + ", " + FeeAgreementEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + FeeAgreementEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + FeeAgreementEntry.COLUMN_ACCOUNT_ID + ", " + FeeAgreementEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "UNIQUE(" + FeeAgreementEntry.COLUMN_ACCOUNT_ID + ", " + FeeAgreementEntry.COLUMN_STUDENT_ID + "))";
    }

    private static String buildCreateInvoicesSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                InvoiceEntry.COLUMN_ID + " TEXT NOT NULL, " +
                InvoiceEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                InvoiceEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " TEXT, " +
                InvoiceEntry.COLUMN_BILLING_PERIOD + " TEXT NOT NULL, " +
                InvoiceEntry.COLUMN_PERIOD_START + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_PERIOD_END + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_ISSUE_DATE + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_DUE_DATE + " INTEGER NOT NULL, " +
                InvoiceEntry.COLUMN_SUBTOTAL_MINOR + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_DISCOUNT_MINOR + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_TOTAL_AMOUNT + " INTEGER NOT NULL, " +
                InvoiceEntry.COLUMN_PAID_AMOUNT + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_STATUS + " TEXT NOT NULL, " +
                InvoiceEntry.COLUMN_NOTES + " TEXT, " +
                InvoiceEntry.COLUMN_IS_VOID + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreateInvoiceItemsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                InvoiceItemEntry.COLUMN_ID + " TEXT NOT NULL, " +
                InvoiceItemEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                InvoiceItemEntry.COLUMN_INVOICE_ID + " TEXT NOT NULL, " +
                InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID + " TEXT, " +
                InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " TEXT, " +
                InvoiceItemEntry.COLUMN_DESCRIPTION + " TEXT NOT NULL, " +
                InvoiceItemEntry.COLUMN_BILLING_MODEL + " TEXT NOT NULL, " +
                InvoiceItemEntry.COLUMN_QUANTITY + " INTEGER NOT NULL, " +
                InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR + " INTEGER NOT NULL, " +
                InvoiceItemEntry.COLUMN_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                InvoiceItemEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceItemEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                InvoiceItemEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + InvoiceItemEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceItemEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + InvoiceItemEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + InvoiceItemEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceItemEntry.COLUMN_INVOICE_ID + ") REFERENCES " +
                InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreatePaymentsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                PaymentEntry.COLUMN_ID + " TEXT NOT NULL, " +
                PaymentEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                PaymentEntry.COLUMN_INVOICE_ID + " TEXT NOT NULL, " +
                PaymentEntry.COLUMN_AMOUNT + " INTEGER NOT NULL, " +
                PaymentEntry.COLUMN_PAYMENT_DATE + " INTEGER NOT NULL, " +
                PaymentEntry.COLUMN_METHOD + " TEXT NOT NULL, " +
                PaymentEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                PaymentEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                PaymentEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + PaymentEntry.COLUMN_ACCOUNT_ID + ", " + PaymentEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + PaymentEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + PaymentEntry.COLUMN_ACCOUNT_ID + ", " + PaymentEntry.COLUMN_INVOICE_ID + ") REFERENCES " +
                InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreateTransactionsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                TransactionEntry.COLUMN_ID + " TEXT NOT NULL, " +
                TransactionEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                TransactionEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                TransactionEntry.COLUMN_TXN_ID_DISPLAY + " TEXT, " +
                TransactionEntry.COLUMN_TYPE + " TEXT NOT NULL, " +
                TransactionEntry.COLUMN_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                TransactionEntry.COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                TransactionEntry.COLUMN_METHOD + " TEXT NOT NULL, " +
                TransactionEntry.COLUMN_REFERENCE_NO + " TEXT, " +
                TransactionEntry.COLUMN_NOTES + " TEXT, " +
                TransactionEntry.COLUMN_PARENT_TXN_ID + " TEXT, " +
                TransactionEntry.COLUMN_IS_REVERSED + " INTEGER NOT NULL DEFAULT 0, " +
                TransactionEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                TransactionEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                TransactionEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + TransactionEntry.COLUMN_ACCOUNT_ID + ", " + TransactionEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + TransactionEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + TransactionEntry.COLUMN_ACCOUNT_ID + ", " + TransactionEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    private static String buildCreatePaymentAllocationsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                PaymentAllocationEntry.COLUMN_ID + " TEXT NOT NULL, " +
                PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " TEXT NOT NULL, " +
                PaymentAllocationEntry.COLUMN_INVOICE_ID + " TEXT NOT NULL, " +
                PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + " INTEGER NOT NULL, " +
                PaymentAllocationEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                PaymentAllocationEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                PaymentAllocationEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ", " + PaymentAllocationEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ", " + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + ") REFERENCES " +
                TransactionEntry.TABLE_NAME + "(" + TransactionEntry.COLUMN_ACCOUNT_ID + ", " + TransactionEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ", " + PaymentAllocationEntry.COLUMN_INVOICE_ID + ") REFERENCES " +
                InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                "UNIQUE(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ", " + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + ", " + PaymentAllocationEntry.COLUMN_INVOICE_ID + "))";
    }

    private static String buildCreateAuditLogsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                AuditLogEntry.COLUMN_ID + " TEXT NOT NULL, " +
                AuditLogEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                AuditLogEntry.COLUMN_STUDENT_ID + " TEXT, " +
                AuditLogEntry.COLUMN_ENTITY_TYPE + " TEXT NOT NULL, " +
                AuditLogEntry.COLUMN_ENTITY_ID + " TEXT NOT NULL, " +
                AuditLogEntry.COLUMN_ACTION + " TEXT NOT NULL, " +
                AuditLogEntry.COLUMN_DETAILS + " TEXT, " +
                AuditLogEntry.COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                AuditLogEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                AuditLogEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                AuditLogEntry.COLUMN_LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(" + AuditLogEntry.COLUMN_ACCOUNT_ID + ", " + AuditLogEntry.COLUMN_ID + "), " +
                "FOREIGN KEY(" + AuditLogEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE)";
    }

    public TutorSyncDbHelper(Context context) {
        super(context, TutorSyncContract.DATABASE_NAME, null, TutorSyncContract.DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.enableWriteAheadLogging();
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAllTables(db);
        createIndexes(db);
        sSchemaVerifiedThisProcess = true;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureSchemaIntegrity(db);
        sSchemaVerifiedThisProcess = true;
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureSchemaIntegrity(db);
        sSchemaVerifiedThisProcess = true;
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly() && !sSchemaVerifiedThisProcess) {
            ensureSchemaIntegrity(db);
            sSchemaVerifiedThisProcess = true;
        }
    }

    private void createAllTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ACCOUNTS);
        db.execSQL(buildCreateStudentsSql(StudentEntry.TABLE_NAME));
        db.execSQL(buildCreateSubjectsSql(SubjectEntry.TABLE_NAME));
        db.execSQL(buildCreateStudentSubjectsSql(StudentSubjectEntry.TABLE_NAME));
        db.execSQL(buildCreateScheduleFeeHistorySql(ScheduleFeeHistoryEntry.TABLE_NAME));
        db.execSQL(buildCreateSchedulesSql(ScheduleEntry.TABLE_NAME));
        db.execSQL(buildCreateOccurrencesSql(OccurrenceEntry.TABLE_NAME));
        db.execSQL(buildCreateAttendanceSql(AttendanceEntry.TABLE_NAME));
        db.execSQL(buildCreateLessonsSql(LessonEntry.TABLE_NAME));
        db.execSQL(buildCreateFeeAgreementsSql(FeeAgreementEntry.TABLE_NAME));
        db.execSQL(buildCreateInvoicesSql(InvoiceEntry.TABLE_NAME));
        db.execSQL(buildCreateInvoiceItemsSql(InvoiceItemEntry.TABLE_NAME));
        db.execSQL(buildCreatePaymentsSql(PaymentEntry.TABLE_NAME));
        db.execSQL(buildCreateTransactionsSql(TransactionEntry.TABLE_NAME));
        db.execSQL(buildCreatePaymentAllocationsSql(PaymentAllocationEntry.TABLE_NAME));
        db.execSQL(buildCreateAuditLogsSql(AuditLogEntry.TABLE_NAME));
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_account ON " + StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_subjects_account ON " + SubjectEntry.TABLE_NAME + "(" + SubjectEntry.COLUMN_ACCOUNT_ID + ", " + SubjectEntry.COLUMN_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_student_subjects_lookup ON " + StudentSubjectEntry.TABLE_NAME + "(" + StudentSubjectEntry.COLUMN_ACCOUNT_ID + ", " + StudentSubjectEntry.COLUMN_STUDENT_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedules_account_student ON " + ScheduleEntry.TABLE_NAME + "(" + ScheduleEntry.COLUMN_ACCOUNT_ID + ", " + ScheduleEntry.COLUMN_STUDENT_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_occurrences_account_date ON " + OccurrenceEntry.TABLE_NAME + "(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ", " + OccurrenceEntry.COLUMN_DATE + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_account_occ ON " + AttendanceEntry.TABLE_NAME + "(" + AttendanceEntry.COLUMN_ACCOUNT_ID + ", " + AttendanceEntry.COLUMN_OCCURRENCE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoices_account_student ON " + InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceEntry.COLUMN_STUDENT_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON " + InvoiceItemEntry.TABLE_NAME + "(" + InvoiceItemEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceItemEntry.COLUMN_INVOICE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_account_student ON " + TransactionEntry.TABLE_NAME + "(" + TransactionEntry.COLUMN_ACCOUNT_ID + ", " + TransactionEntry.COLUMN_STUDENT_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_allocations_invoice ON " + PaymentAllocationEntry.TABLE_NAME + "(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ", " + PaymentAllocationEntry.COLUMN_INVOICE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_allocations_txn ON " + PaymentAllocationEntry.TABLE_NAME + "(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ", " + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + ")");
    }

    private void ensureSchemaIntegrity(SQLiteDatabase db) {
        createAllTables(db);
        ensurePreMigrationColumns(db);

        if (needsCompositeKeyMigration(db)) {
            migrateToCompositePrimaryKeys(db);
        }

        createIndexes(db);
    }

    private void ensurePreMigrationColumns(SQLiteDatabase db) {
        // Account columns
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_PHONE, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_EMAIL, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ADDRESS, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_DOB, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_PROFILE_PIC_PATH, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Student columns
        addColumnIfNotExists(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_STUDENT_ID_DISPLAY, "TEXT");
        addColumnIfNotExists(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Subject columns
        addColumnIfNotExists(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // StudentSubject columns
        addColumnIfNotExists(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_ACCOUNT_ID, "TEXT");
        addColumnIfNotExists(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Schedule Fee History columns
        addColumnIfNotExists(db, ScheduleFeeHistoryEntry.TABLE_NAME, ScheduleFeeHistoryEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleFeeHistoryEntry.TABLE_NAME, ScheduleFeeHistoryEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleFeeHistoryEntry.TABLE_NAME, ScheduleFeeHistoryEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Schedule columns
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID, "TEXT");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_BILLING_MODEL, "TEXT NOT NULL DEFAULT 'MONTHLY'");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_ANCHOR_DAY, "INTEGER NOT NULL DEFAULT 1");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_IS_ACTIVE, "INTEGER NOT NULL DEFAULT 1");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Occurrence columns
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_SUBJECT_ID, "TEXT");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, "TEXT");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID, "TEXT");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Attendance columns
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_CHARGE_DECISION, "TEXT NOT NULL DEFAULT 'CHARGE'");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_NEEDS_RESCHEDULE, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID, "TEXT");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID, "TEXT");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Lesson columns
        addColumnIfNotExists(db, LessonEntry.TABLE_NAME, LessonEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, LessonEntry.TABLE_NAME, LessonEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, LessonEntry.TABLE_NAME, LessonEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // FeeAgreement columns
        addColumnIfNotExists(db, FeeAgreementEntry.TABLE_NAME, FeeAgreementEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, FeeAgreementEntry.TABLE_NAME, FeeAgreementEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, FeeAgreementEntry.TABLE_NAME, FeeAgreementEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Invoice columns
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, "TEXT");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_PERIOD_START, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_PERIOD_END, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_ISSUE_DATE, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_SUBTOTAL_MINOR, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_DISCOUNT_MINOR, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_NOTES, "TEXT");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_IS_VOID, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // InvoiceItem columns
        addColumnIfNotExists(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Payment columns
        addColumnIfNotExists(db, PaymentEntry.TABLE_NAME, PaymentEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, PaymentEntry.TABLE_NAME, PaymentEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, PaymentEntry.TABLE_NAME, PaymentEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Transaction columns
        addColumnIfNotExists(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // PaymentAllocation columns
        addColumnIfNotExists(db, PaymentAllocationEntry.TABLE_NAME, PaymentAllocationEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, PaymentAllocationEntry.TABLE_NAME, PaymentAllocationEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, PaymentAllocationEntry.TABLE_NAME, PaymentAllocationEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // AuditLog columns
        addColumnIfNotExists(db, AuditLogEntry.TABLE_NAME, AuditLogEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AuditLogEntry.TABLE_NAME, AuditLogEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AuditLogEntry.TABLE_NAME, AuditLogEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        // Backfill student_subjects.account_id from students.account_id where missing
        db.execSQL("UPDATE " + StudentSubjectEntry.TABLE_NAME +
                " SET " + StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = (" +
                "SELECT s." + StudentEntry.COLUMN_ACCOUNT_ID + " FROM " + StudentEntry.TABLE_NAME + " s " +
                "WHERE s." + StudentEntry.COLUMN_ID + " = " + StudentSubjectEntry.TABLE_NAME + "." + StudentSubjectEntry.COLUMN_STUDENT_ID + " LIMIT 1) " +
                "WHERE " + StudentSubjectEntry.COLUMN_ACCOUNT_ID + " IS NULL OR " + StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = ''");

        // Normalize timestamps across tables where updated_at or last_modified was 0
        long now = System.currentTimeMillis();
        normalizeTimestamps(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_CREATED_AT, now);
        normalizeTimestamps(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_UPDATED_AT, now);
        normalizeTimestamps(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_UPDATED_AT, now);
        normalizeTimestamps(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_UPDATED_AT, now);
        normalizeTimestamps(db, ScheduleFeeHistoryEntry.TABLE_NAME, ScheduleFeeHistoryEntry.COLUMN_CREATED_AT, now);
        normalizeTimestamps(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_UPDATED_AT, now);
        normalizeTimestamps(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_DATE, now);
        normalizeTimestamps(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_UPDATED_AT, now);
        normalizeTimestamps(db, LessonEntry.TABLE_NAME, LessonEntry.COLUMN_UPDATED_AT, now);
        normalizeTimestamps(db, FeeAgreementEntry.TABLE_NAME, FeeAgreementEntry.COLUMN_UPDATED_AT, now);
        normalizeTimestamps(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_CREATED_AT, now);
        normalizeTimestamps(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_CREATED_AT, now);
        normalizeTimestamps(db, PaymentEntry.TABLE_NAME, PaymentEntry.COLUMN_PAYMENT_DATE, now);
        normalizeTimestamps(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_CREATED_AT, now);
        normalizeTimestamps(db, PaymentAllocationEntry.TABLE_NAME, PaymentAllocationEntry.COLUMN_CREATED_AT, now);
        normalizeTimestamps(db, AuditLogEntry.TABLE_NAME, AuditLogEntry.COLUMN_TIMESTAMP, now);
    }

    private void normalizeTimestamps(SQLiteDatabase db, String table, String fallbackCol, long defaultNow) {
        db.execSQL("UPDATE " + table + " SET " +
                "updated_at = CASE WHEN updated_at > 0 THEN updated_at WHEN last_modified > 0 THEN last_modified WHEN " + fallbackCol + " > 0 THEN " + fallbackCol + " ELSE " + defaultNow + " END, " +
                "last_modified = CASE WHEN last_modified > 0 THEN last_modified WHEN updated_at > 0 THEN updated_at WHEN " + fallbackCol + " > 0 THEN " + fallbackCol + " ELSE " + defaultNow + " END, " +
                "created_at = CASE WHEN created_at > 0 THEN created_at WHEN " + fallbackCol + " > 0 THEN " + fallbackCol + " WHEN updated_at > 0 THEN updated_at ELSE " + defaultNow + " END " +
                "WHERE updated_at <= 0 OR last_modified <= 0 OR created_at <= 0");
    }

    private boolean needsCompositeKeyMigration(SQLiteDatabase db) {
        String[] compositeTables = new String[]{
                StudentEntry.TABLE_NAME,
                SubjectEntry.TABLE_NAME,
                StudentSubjectEntry.TABLE_NAME,
                ScheduleFeeHistoryEntry.TABLE_NAME,
                ScheduleEntry.TABLE_NAME,
                OccurrenceEntry.TABLE_NAME,
                AttendanceEntry.TABLE_NAME,
                LessonEntry.TABLE_NAME,
                FeeAgreementEntry.TABLE_NAME,
                InvoiceEntry.TABLE_NAME,
                InvoiceItemEntry.TABLE_NAME,
                PaymentEntry.TABLE_NAME,
                TransactionEntry.TABLE_NAME,
                PaymentAllocationEntry.TABLE_NAME,
                AuditLogEntry.TABLE_NAME
        };

        for (String table : compositeTables) {
            int pkColumns = 0;
            try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
                int pkIdx = c.getColumnIndex("pk");
                while (c.moveToNext()) {
                    if (pkIdx >= 0 && c.getInt(pkIdx) > 0) {
                        pkColumns++;
                    }
                }
            }
            if (pkColumns < 2) {
                return true;
            }
        }
        return false;
    }

    private void migrateToCompositePrimaryKeys(SQLiteDatabase db) {
        boolean wasInTransaction = db.inTransaction();
        if (!wasInTransaction) {
            db.setForeignKeyConstraintsEnabled(false);
            db.beginTransaction();
        }
        try {
            // 1. Create staging v7 tables
            db.execSQL("DROP TABLE IF EXISTS students_v7");
            db.execSQL("DROP TABLE IF EXISTS subjects_v7");
            db.execSQL("DROP TABLE IF EXISTS student_subjects_v7");
            db.execSQL("DROP TABLE IF EXISTS schedule_fee_history_v7");
            db.execSQL("DROP TABLE IF EXISTS recurring_schedules_v7");
            db.execSQL("DROP TABLE IF EXISTS class_occurrences_v7");
            db.execSQL("DROP TABLE IF EXISTS attendance_records_v7");
            db.execSQL("DROP TABLE IF EXISTS lessons_v7");
            db.execSQL("DROP TABLE IF EXISTS fee_agreements_v7");
            db.execSQL("DROP TABLE IF EXISTS invoices_v7");
            db.execSQL("DROP TABLE IF EXISTS invoice_items_v7");
            db.execSQL("DROP TABLE IF EXISTS payments_v7");
            db.execSQL("DROP TABLE IF EXISTS financial_transactions_v7");
            db.execSQL("DROP TABLE IF EXISTS payment_allocations_v7");
            db.execSQL("DROP TABLE IF EXISTS audit_logs_v7");

            db.execSQL(buildCreateStudentsSql("students_v7"));
            db.execSQL(buildCreateSubjectsSql("subjects_v7"));
            db.execSQL(buildCreateStudentSubjectsSql("student_subjects_v7"));
            db.execSQL(buildCreateScheduleFeeHistorySql("schedule_fee_history_v7"));
            db.execSQL(buildCreateSchedulesSql("recurring_schedules_v7"));
            db.execSQL(buildCreateOccurrencesSql("class_occurrences_v7"));
            db.execSQL(buildCreateAttendanceSql("attendance_records_v7"));
            db.execSQL(buildCreateLessonsSql("lessons_v7"));
            db.execSQL(buildCreateFeeAgreementsSql("fee_agreements_v7"));
            db.execSQL(buildCreateInvoicesSql("invoices_v7"));
            db.execSQL(buildCreateInvoiceItemsSql("invoice_items_v7"));
            db.execSQL(buildCreatePaymentsSql("payments_v7"));
            db.execSQL(buildCreateTransactionsSql("financial_transactions_v7"));
            db.execSQL(buildCreatePaymentAllocationsSql("payment_allocations_v7"));
            db.execSQL(buildCreateAuditLogsSql("audit_logs_v7"));

            // 2. Copy all existing rows into staging tables
            db.execSQL("INSERT OR IGNORE INTO students_v7 (id, account_id, student_id_display, name, grade, school, guardian_name, guardian_phone, address, is_active, is_deleted, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id_display, name, grade, school, guardian_name, guardian_phone, address, is_active, is_deleted, created_at, updated_at, last_modified FROM " + StudentEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO subjects_v7 (id, account_id, title, level, is_active, is_deleted, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, title, level, is_active, is_deleted, created_at, updated_at, last_modified FROM " + SubjectEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO student_subjects_v7 (id, account_id, student_id, subject_id, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, subject_id, created_at, updated_at, last_modified FROM " + StudentSubjectEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO schedule_fee_history_v7 (id, account_id, student_id, schedule_group_id, billing_model, fee_amount_minor, anchor_day, effective_from, effective_to, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, schedule_group_id, billing_model, fee_amount_minor, anchor_day, effective_from, effective_to, created_at, updated_at, last_modified FROM " + ScheduleFeeHistoryEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO recurring_schedules_v7 (id, account_id, student_id, subject_id, schedule_group_id, day_of_week, start_time_minutes, duration_minutes, start_date, end_date, billing_model, fee_amount_minor, anchor_day, is_active, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, subject_id, schedule_group_id, day_of_week, start_time_minutes, duration_minutes, start_date, end_date, billing_model, fee_amount_minor, anchor_day, is_active, created_at, updated_at, last_modified FROM " + ScheduleEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO class_occurrences_v7 (id, account_id, student_id, subject_id, schedule_id, schedule_group_id, date, start_time_minutes, duration_minutes, rescheduled_from_id, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, subject_id, schedule_id, schedule_group_id, date, start_time_minutes, duration_minutes, rescheduled_from_id, created_at, updated_at, last_modified FROM " + OccurrenceEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO attendance_records_v7 (id, account_id, occurrence_id, status, charge_decision, applicable_fee_minor, needs_reschedule, makeup_occurrence_id, billed_invoice_item_id, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, occurrence_id, status, charge_decision, applicable_fee_minor, needs_reschedule, makeup_occurrence_id, billed_invoice_item_id, created_at, updated_at, last_modified FROM " + AttendanceEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO lessons_v7 (id, account_id, student_id, occurrence_id, topic_covered, homework, tutor_notes, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, occurrence_id, topic_covered, homework, tutor_notes, created_at, updated_at, last_modified FROM " + LessonEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO fee_agreements_v7 (id, account_id, student_id, fee_type, amount_minor_units, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, fee_type, amount_minor_units, created_at, updated_at, last_modified FROM " + FeeAgreementEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO invoices_v7 (id, account_id, student_id, invoice_id_display, billing_period, period_start, period_end, issue_date, due_date, subtotal_minor, discount_minor, total_amount, paid_amount, status, notes, is_void, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, invoice_id_display, billing_period, period_start, period_end, issue_date, due_date, subtotal_minor, discount_minor, total_amount, paid_amount, status, notes, is_void, created_at, updated_at, last_modified FROM " + InvoiceEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO invoice_items_v7 (id, account_id, invoice_id, schedule_group_id, occurrence_id, description, billing_model, quantity, unit_price_minor, amount_minor, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, invoice_id, schedule_group_id, occurrence_id, description, billing_model, quantity, unit_price_minor, amount_minor, created_at, updated_at, last_modified FROM " + InvoiceItemEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO payments_v7 (id, account_id, invoice_id, amount, payment_date, method, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, invoice_id, amount, payment_date, method, created_at, updated_at, last_modified FROM " + PaymentEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO financial_transactions_v7 (id, account_id, student_id, txn_id_display, type, amount_minor, timestamp, method, reference_no, notes, parent_txn_id, is_reversed, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, txn_id_display, type, amount_minor, timestamp, method, reference_no, notes, parent_txn_id, is_reversed, created_at, updated_at, last_modified FROM " + TransactionEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO payment_allocations_v7 (id, account_id, transaction_id, invoice_id, allocated_minor, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, transaction_id, invoice_id, allocated_minor, created_at, updated_at, last_modified FROM " + PaymentAllocationEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            db.execSQL("INSERT OR IGNORE INTO audit_logs_v7 (id, account_id, student_id, entity_type, entity_id, action, details, timestamp, created_at, updated_at, last_modified) " +
                    "SELECT id, account_id, student_id, entity_type, entity_id, action, details, timestamp, created_at, updated_at, last_modified FROM " + AuditLogEntry.TABLE_NAME +
                    " WHERE account_id IS NOT NULL AND account_id != '' AND id IS NOT NULL AND id != ''");

            // 3. Drop legacy tables in reverse dependency order
            db.execSQL("DROP TABLE IF EXISTS " + AuditLogEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + PaymentAllocationEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + TransactionEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + PaymentEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + InvoiceItemEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + InvoiceEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + FeeAgreementEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + LessonEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + AttendanceEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + OccurrenceEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ScheduleEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ScheduleFeeHistoryEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + StudentSubjectEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + SubjectEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + StudentEntry.TABLE_NAME);

            // 4. Rename v7 staging tables into place in parent-to-child order
            db.execSQL("ALTER TABLE students_v7 RENAME TO " + StudentEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE subjects_v7 RENAME TO " + SubjectEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE student_subjects_v7 RENAME TO " + StudentSubjectEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE schedule_fee_history_v7 RENAME TO " + ScheduleFeeHistoryEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE recurring_schedules_v7 RENAME TO " + ScheduleEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE class_occurrences_v7 RENAME TO " + OccurrenceEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE attendance_records_v7 RENAME TO " + AttendanceEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE lessons_v7 RENAME TO " + LessonEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE fee_agreements_v7 RENAME TO " + FeeAgreementEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE invoices_v7 RENAME TO " + InvoiceEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE invoice_items_v7 RENAME TO " + InvoiceItemEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE payments_v7 RENAME TO " + PaymentEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE financial_transactions_v7 RENAME TO " + TransactionEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE payment_allocations_v7 RENAME TO " + PaymentAllocationEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE audit_logs_v7 RENAME TO " + AuditLogEntry.TABLE_NAME);

            if (!wasInTransaction) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (!wasInTransaction) {
                db.endTransaction();
                db.setForeignKeyConstraintsEnabled(true);
            }
        }
    }

    private void addColumnIfNotExists(SQLiteDatabase db, String tableName, String columnName, String columnType) {
        boolean exists = false;
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && columnName.equalsIgnoreCase(cursor.getString(nameIndex))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
        }
    }
}
package com.abdussamadcodes.tutorsync.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TutorSyncDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "TutorSyncDbHelper";

    private static volatile boolean sSchemaVerifiedThisProcess = false;

    /** Suffix of the staging tables used while rebuilding tables with composite primary keys. */
    private static final String MIGRATION_SUFFIX = "_v7";

    /** Tables rebuilt by the composite-key migration, PARENTS FIRST (dropped in reverse order). */
    private static final String[] MIGRATION_TABLE_ORDER = new String[]{
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

    /**
     * Tables whose new schema has an extra UNIQUE constraint beyond the primary key and where a
     * legacy duplicate is a redundant row (not money). Collapsing such duplicates is logged, never fatal.
     * Every other table must copy every valid legacy row or the whole migration is rolled back.
     */
    private static final Set<String> DEDUPE_TOLERANT_TABLES = new HashSet<>(Arrays.asList(
            StudentSubjectEntry.TABLE_NAME,
            FeeAgreementEntry.TABLE_NAME,
            AttendanceEntry.TABLE_NAME
    ));

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
                ScheduleEntry.TABLE_NAME + "(" + ScheduleEntry.COLUMN_ACCOUNT_ID + ", " + ScheduleEntry.COLUMN_ID + ") ON DELETE NO ACTION)";
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
                InvoiceItemEntry.COLUMN_PERIOD_START + " INTEGER NOT NULL DEFAULT 0, " +
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
        // Foreign keys are deliberately OFF here. SQLiteOpenHelper runs onCreate/onUpgrade inside a
        // transaction, where "PRAGMA foreign_keys" is silently ignored, and the composite-key
        // migration drops and renames tables: with enforcement ON every DROP TABLE performs an
        // implicit DELETE that fires ON DELETE CASCADE into the already-copied rows (data loss) or
        // fails with "foreign key mismatch". Enforcement is switched on in onOpen() once the schema
        // has been verified, so normal app operation still runs with foreign keys enforced.
        db.setForeignKeyConstraintsEnabled(false);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAllTables(db);
        createIndexes(db);
        createTriggers(db);
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
        // Runs on EVERY open (not guarded by the per-process flag): each connection starts with
        // enforcement OFF (see onConfigure) and must be switched on after create/upgrade/migration.
        db.setForeignKeyConstraintsEnabled(true);
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_occurrences_account_schedule ON " + OccurrenceEntry.TABLE_NAME + "(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ", " + OccurrenceEntry.COLUMN_SCHEDULE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_account_occ ON " + AttendanceEntry.TABLE_NAME + "(" + AttendanceEntry.COLUMN_ACCOUNT_ID + ", " + AttendanceEntry.COLUMN_OCCURRENCE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoices_account_student ON " + InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceEntry.COLUMN_STUDENT_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON " + InvoiceItemEntry.TABLE_NAME + "(" + InvoiceItemEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceItemEntry.COLUMN_INVOICE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invoice_items_occ ON " + InvoiceItemEntry.TABLE_NAME + "(" + InvoiceItemEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceItemEntry.COLUMN_OCCURRENCE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_account_student ON " + TransactionEntry.TABLE_NAME + "(" + TransactionEntry.COLUMN_ACCOUNT_ID + ", " + TransactionEntry.COLUMN_STUDENT_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_allocations_invoice ON " + PaymentAllocationEntry.TABLE_NAME + "(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ", " + PaymentAllocationEntry.COLUMN_INVOICE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_allocations_txn ON " + PaymentAllocationEntry.TABLE_NAME + "(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ", " + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + ")");
    }

    private void createTriggers(SQLiteDatabase db) {
        // Trigger to intercept recurring_schedules deletion:
        // Detaches historical occurrences (attendance, lessons, billing) by setting schedule_id = NULL
        // Deletes only unrecorded future occurrences so ghost classes do not linger
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trg_recurring_schedules_cascade_guard " +
                "BEFORE DELETE ON " + ScheduleEntry.TABLE_NAME + " " +
                "FOR EACH ROW " +
                "BEGIN " +
                "  UPDATE " + OccurrenceEntry.TABLE_NAME + " " +
                "  SET " + OccurrenceEntry.COLUMN_SCHEDULE_ID + " = NULL, " +
                "      " + OccurrenceEntry.COLUMN_UPDATED_AT + " = (strftime('%s', 'now') * 1000), " +
                "      " + OccurrenceEntry.COLUMN_LAST_MODIFIED + " = (strftime('%s', 'now') * 1000) " +
                "  WHERE " + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = OLD." + ScheduleEntry.COLUMN_ACCOUNT_ID + " " +
                "    AND " + OccurrenceEntry.COLUMN_SCHEDULE_ID + " = OLD." + ScheduleEntry.COLUMN_ID + " " +
                "    AND (" +
                "        " + OccurrenceEntry.COLUMN_ID + " IN (SELECT " + AttendanceEntry.COLUMN_OCCURRENCE_ID + " FROM " + AttendanceEntry.TABLE_NAME + " WHERE " + AttendanceEntry.COLUMN_ACCOUNT_ID + " = OLD." + ScheduleEntry.COLUMN_ACCOUNT_ID + ") OR " +
                "        " + OccurrenceEntry.COLUMN_ID + " IN (SELECT " + LessonEntry.COLUMN_OCCURRENCE_ID + " FROM " + LessonEntry.TABLE_NAME + " WHERE " + LessonEntry.COLUMN_ACCOUNT_ID + " = OLD." + ScheduleEntry.COLUMN_ACCOUNT_ID + " AND " + LessonEntry.COLUMN_OCCURRENCE_ID + " IS NOT NULL) OR " +
                "        " + OccurrenceEntry.COLUMN_ID + " IN (SELECT " + InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " FROM " + InvoiceItemEntry.TABLE_NAME + " WHERE " + InvoiceItemEntry.COLUMN_ACCOUNT_ID + " = OLD." + ScheduleEntry.COLUMN_ACCOUNT_ID + " AND " + InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " IS NOT NULL)" +
                "    ); " +
                "  DELETE FROM " + OccurrenceEntry.TABLE_NAME + " " +
                "  WHERE " + OccurrenceEntry.COLUMN_ACCOUNT_ID + " = OLD." + ScheduleEntry.COLUMN_ACCOUNT_ID + " " +
                "    AND " + OccurrenceEntry.COLUMN_SCHEDULE_ID + " = OLD." + ScheduleEntry.COLUMN_ID + "; " +
                "END;");
    }

    private void ensureSchemaIntegrity(SQLiteDatabase db) {
        createAllTables(db);
        ensurePreMigrationColumns(db);

        if (needsCompositeKeyMigration(db)) {
            migrateToCompositePrimaryKeys(db);
        }

        createIndexes(db);
        createTriggers(db);
    }

    private void ensurePreMigrationColumns(SQLiteDatabase db) {
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_PHONE, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_EMAIL, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_ADDRESS, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_DOB, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_PROFILE_PIC_PATH, "TEXT");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_STUDENT_ID_DISPLAY, "TEXT");
        addColumnIfNotExists(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, StudentEntry.TABLE_NAME, StudentEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, SubjectEntry.TABLE_NAME, SubjectEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_ACCOUNT_ID, "TEXT");
        addColumnIfNotExists(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, StudentSubjectEntry.TABLE_NAME, StudentSubjectEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, ScheduleFeeHistoryEntry.TABLE_NAME, ScheduleFeeHistoryEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleFeeHistoryEntry.TABLE_NAME, ScheduleFeeHistoryEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleFeeHistoryEntry.TABLE_NAME, ScheduleFeeHistoryEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID, "TEXT");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_BILLING_MODEL, "TEXT NOT NULL DEFAULT 'MONTHLY'");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_ANCHOR_DAY, "INTEGER NOT NULL DEFAULT 1");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_IS_ACTIVE, "INTEGER NOT NULL DEFAULT 1");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_SUBJECT_ID, "TEXT");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, "TEXT");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID, "TEXT");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_CHARGE_DECISION, "TEXT NOT NULL DEFAULT 'CHARGE'");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_NEEDS_RESCHEDULE, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID, "TEXT");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID, "TEXT");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, LessonEntry.TABLE_NAME, LessonEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, LessonEntry.TABLE_NAME, LessonEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, LessonEntry.TABLE_NAME, LessonEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, FeeAgreementEntry.TABLE_NAME, FeeAgreementEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, FeeAgreementEntry.TABLE_NAME, FeeAgreementEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, FeeAgreementEntry.TABLE_NAME, FeeAgreementEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

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

        addColumnIfNotExists(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_PERIOD_START, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, InvoiceItemEntry.TABLE_NAME, InvoiceItemEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, PaymentEntry.TABLE_NAME, PaymentEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, PaymentEntry.TABLE_NAME, PaymentEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, PaymentEntry.TABLE_NAME, PaymentEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, PaymentAllocationEntry.TABLE_NAME, PaymentAllocationEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, PaymentAllocationEntry.TABLE_NAME, PaymentAllocationEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, PaymentAllocationEntry.TABLE_NAME, PaymentAllocationEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        addColumnIfNotExists(db, AuditLogEntry.TABLE_NAME, AuditLogEntry.COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AuditLogEntry.TABLE_NAME, AuditLogEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(db, AuditLogEntry.TABLE_NAME, AuditLogEntry.COLUMN_LAST_MODIFIED, "INTEGER NOT NULL DEFAULT 0");

        db.execSQL("UPDATE " + StudentSubjectEntry.TABLE_NAME +
                " SET " + StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = (" +
                "SELECT s." + StudentEntry.COLUMN_ACCOUNT_ID + " FROM " + StudentEntry.TABLE_NAME + " s " +
                "WHERE s." + StudentEntry.COLUMN_ID + " = " + StudentSubjectEntry.TABLE_NAME + "." + StudentSubjectEntry.COLUMN_STUDENT_ID + " LIMIT 1) " +
                "WHERE " + StudentSubjectEntry.COLUMN_ACCOUNT_ID + " IS NULL OR " + StudentSubjectEntry.COLUMN_ACCOUNT_ID + " = ''");

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
        final boolean ownTransaction = !db.inTransaction();

        // DROP TABLE performs an implicit DELETE which, with foreign keys enforced, fires
        // ON DELETE CASCADE into the rows already copied to the staging tables (silent data loss)
        // or fails with "foreign key mismatch" while parents still have a single-column key.
        // PRAGMA foreign_keys is a no-op inside a transaction, so it can only be changed here
        // when we are the ones opening the transaction. Normal callers (onUpgrade/onOpen) always
        // arrive with enforcement already OFF - see onConfigure().
        if (isForeignKeyEnforcementOn(db)) {
            if (!ownTransaction) {
                throw new IllegalStateException(
                        "Composite-key migration requires foreign keys to be disabled before the transaction starts.");
            }
            db.setForeignKeyConstraintsEnabled(false);
        }

        if (ownTransaction) {
            db.beginTransaction();
        }
        try {
            // 1. Remove leftovers of an earlier attempt, then create empty staging tables (parents first).
            for (String table : MIGRATION_TABLE_ORDER) {
                db.execSQL("DROP TABLE IF EXISTS " + table + MIGRATION_SUFFIX);
            }
            for (String table : MIGRATION_TABLE_ORDER) {
                db.execSQL(buildCreateSqlFor(table, table + MIGRATION_SUFFIX));
            }

            // 2. Copy every valid legacy row, verifying nothing is lost on the way.
            for (String table : MIGRATION_TABLE_ORDER) {
                copyLegacyRows(db, table, table + MIGRATION_SUFFIX);
            }

            // 3. Swap: drop legacy tables (children first), then rename staging tables into place.
            for (int i = MIGRATION_TABLE_ORDER.length - 1; i >= 0; i--) {
                db.execSQL("DROP TABLE IF EXISTS " + MIGRATION_TABLE_ORDER[i]);
            }
            for (String table : MIGRATION_TABLE_ORDER) {
                db.execSQL("ALTER TABLE " + table + MIGRATION_SUFFIX + " RENAME TO " + table);
            }

            logForeignKeyViolations(db);

            if (ownTransaction) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (ownTransaction) {
                db.endTransaction();
            }
            // Foreign keys are intentionally NOT re-enabled here; onOpen() does it after the schema is verified.
        }
    }

    private boolean isForeignKeyEnforcementOn(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("PRAGMA foreign_keys", null)) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }

    private static String buildCreateSqlFor(String table, String physicalName) {
        switch (table) {
            case StudentEntry.TABLE_NAME: return buildCreateStudentsSql(physicalName);
            case SubjectEntry.TABLE_NAME: return buildCreateSubjectsSql(physicalName);
            case StudentSubjectEntry.TABLE_NAME: return buildCreateStudentSubjectsSql(physicalName);
            case ScheduleFeeHistoryEntry.TABLE_NAME: return buildCreateScheduleFeeHistorySql(physicalName);
            case ScheduleEntry.TABLE_NAME: return buildCreateSchedulesSql(physicalName);
            case OccurrenceEntry.TABLE_NAME: return buildCreateOccurrencesSql(physicalName);
            case AttendanceEntry.TABLE_NAME: return buildCreateAttendanceSql(physicalName);
            case LessonEntry.TABLE_NAME: return buildCreateLessonsSql(physicalName);
            case FeeAgreementEntry.TABLE_NAME: return buildCreateFeeAgreementsSql(physicalName);
            case InvoiceEntry.TABLE_NAME: return buildCreateInvoicesSql(physicalName);
            case InvoiceItemEntry.TABLE_NAME: return buildCreateInvoiceItemsSql(physicalName);
            case PaymentEntry.TABLE_NAME: return buildCreatePaymentsSql(physicalName);
            case TransactionEntry.TABLE_NAME: return buildCreateTransactionsSql(physicalName);
            case PaymentAllocationEntry.TABLE_NAME: return buildCreatePaymentAllocationsSql(physicalName);
            case AuditLogEntry.TABLE_NAME: return buildCreateAuditLogsSql(physicalName);
            default: throw new IllegalArgumentException("Unknown table for migration: " + table);
        }
    }

    /** Column metadata read from PRAGMA table_info. */
    private static final class ColumnInfo {
        final String name;
        final String type;
        final boolean notNull;
        final String defaultValue; // raw SQL text of the DEFAULT clause, or null

        ColumnInfo(String name, String type, boolean notNull, String defaultValue) {
            this.name = name;
            this.type = type;
            this.notNull = notNull;
            this.defaultValue = defaultValue;
        }

        boolean isNumeric() {
            String t = type.toUpperCase(Locale.ROOT);
            return t.contains("INT") || t.contains("REAL") || t.contains("FLOA") || t.contains("DOUB") || t.contains("NUM");
        }
    }

    private List<ColumnInfo> readTableColumns(SQLiteDatabase db, String table) {
        List<ColumnInfo> columns = new ArrayList<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIdx = c.getColumnIndex("name");
            int typeIdx = c.getColumnIndex("type");
            int notNullIdx = c.getColumnIndex("notnull");
            int defaultIdx = c.getColumnIndex("dflt_value");
            while (c.moveToNext()) {
                String type = (typeIdx >= 0 && !c.isNull(typeIdx)) ? c.getString(typeIdx) : "";
                String dflt = (defaultIdx >= 0 && !c.isNull(defaultIdx)) ? c.getString(defaultIdx) : null;
                columns.add(new ColumnInfo(c.getString(nameIdx), type, notNullIdx >= 0 && c.getInt(notNullIdx) != 0, dflt));
            }
        }
        return columns;
    }

    private long queryCount(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    /**
     * Copies a legacy (single-key) table into its composite-key staging table.
     * The copy is driven by the columns that actually exist on both sides, so legacy databases that
     * pre-date a column no longer fail with "no such column", NULLs in columns that are NOT NULL in the
     * new schema no longer make INSERT OR IGNORE silently skip the row, and duplicates that would
     * violate a new UNIQUE constraint are resolved deterministically instead of dropping arbitrary rows.
     */
    private void copyLegacyRows(SQLiteDatabase db, String sourceTable, String targetTable) {
        List<ColumnInfo> targetColumns = readTableColumns(db, targetTable);
        Set<String> sourceColumns = new HashSet<>();
        for (ColumnInfo col : readTableColumns(db, sourceTable)) {
            sourceColumns.add(col.name.toLowerCase(Locale.ROOT));
        }
        if (!sourceColumns.contains("id") || !sourceColumns.contains("account_id")) {
            // Without the key columns rows cannot be attributed to an account; abort (and roll back)
            // rather than silently discarding the table's data.
            throw new IllegalStateException("Cannot migrate legacy table '" + sourceTable
                    + "': it has no id/account_id column.");
        }

        StringBuilder insertColumns = new StringBuilder();
        StringBuilder selectExpressions = new StringBuilder();
        for (ColumnInfo col : targetColumns) {
            if (insertColumns.length() > 0) {
                insertColumns.append(", ");
                selectExpressions.append(", ");
            }
            insertColumns.append(col.name);
            selectExpressions.append(buildMigrationSelectExpression(sourceTable, sourceColumns, col));
        }

        final String validKey = sourceTable + ".account_id IS NOT NULL AND " + sourceTable + ".account_id != '' AND "
                + sourceTable + ".id IS NOT NULL AND " + sourceTable + ".id != ''";

        String orderBy = "";
        if (AttendanceEntry.TABLE_NAME.equals(sourceTable)
                && sourceColumns.contains(AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID)
                && sourceColumns.contains("updated_at")) {
            // If duplicates must be collapsed, keep the record that is already billed.
            orderBy = " ORDER BY (" + sourceTable + "." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID
                    + " IS NOT NULL) DESC, " + sourceTable + ".updated_at DESC";
        } else if (sourceColumns.contains("updated_at")) {
            orderBy = " ORDER BY " + sourceTable + ".updated_at DESC";
        }

        db.execSQL("INSERT OR IGNORE INTO " + targetTable + " (" + insertColumns + ") SELECT "
                + selectExpressions + " FROM " + sourceTable + " WHERE " + validKey + orderBy);

        // ---- verification: nothing valid may disappear silently ----
        long totalRows = queryCount(db, "SELECT COUNT(*) FROM " + sourceTable);
        long expectedRows = queryCount(db, "SELECT COUNT(*) FROM (SELECT 1 FROM " + sourceTable
                + " WHERE " + validKey + " GROUP BY " + sourceTable + ".account_id, " + sourceTable + ".id)");
        long copiedRows = queryCount(db, "SELECT COUNT(*) FROM " + targetTable);

        long unattributable = totalRows - queryCount(db, "SELECT COUNT(*) FROM " + sourceTable + " WHERE " + validKey);
        if (unattributable > 0) {
            Log.w(TAG, "Migration: " + unattributable + " row(s) in '" + sourceTable
                    + "' have no account_id/id and cannot be attributed; they were not migrated.");
        }
        if (copiedRows < expectedRows) {
            String message = "Migration of '" + sourceTable + "' copied " + copiedRows + " of " + expectedRows + " rows.";
            if (DEDUPE_TOLERANT_TABLES.contains(sourceTable)) {
                Log.w(TAG, message + " The remainder were redundant duplicates of a UNIQUE key.");
            } else {
                // Throwing rolls the whole migration back, leaving the original database untouched.
                throw new IllegalStateException(message + " Aborting to avoid data loss.");
            }
        }
    }

    private String buildMigrationSelectExpression(String sourceTable, Set<String> sourceColumns, ColumnInfo target) {
        String fallback;
        if (target.defaultValue != null) {
            fallback = target.defaultValue;
        } else if (target.notNull) {
            fallback = target.isNumeric() ? "0" : "''";
        } else {
            fallback = "NULL";
        }

        if (!sourceColumns.contains(target.name.toLowerCase(Locale.ROOT))) {
            return fallback; // column did not exist in the legacy table
        }

        String ref = sourceTable + "." + target.name;

        if (StudentEntry.TABLE_NAME.equals(sourceTable)
                && StudentEntry.COLUMN_STUDENT_ID_DISPLAY.equals(target.name)) {
            // UNIQUE(account_id, student_id_display): blank/duplicate display ids become NULL instead of
            // causing the whole student row to be skipped. StudentRepository.migrateLegacyStudentIds()
            // regenerates missing display ids on next use.
            return "CASE WHEN " + ref + " IS NULL OR " + ref + " = '' THEN NULL "
                    + "WHEN EXISTS (SELECT 1 FROM " + sourceTable + " dup WHERE dup.account_id = " + sourceTable + ".account_id "
                    + "AND dup." + target.name + " = " + ref + " AND dup.rowid < " + sourceTable + ".rowid) THEN NULL "
                    + "ELSE " + ref + " END";
        }

        return target.notNull ? "COALESCE(" + ref + ", " + fallback + ")" : ref;
    }

    private void logForeignKeyViolations(SQLiteDatabase db) {
        long violations = 0;
        try (Cursor c = db.rawQuery("PRAGMA foreign_key_check", null)) {
            while (c.moveToNext()) {
                violations++;
            }
        }
        if (violations > 0) {
            Log.w(TAG, "Migration finished with " + violations + " pre-existing orphan reference(s). "
                    + "Rows were preserved, not deleted.");
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
package com.abdussamadcodes.tutorsync.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.*;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

public class TutorSyncDbHelper extends SQLiteOpenHelper {

    private static final String SQL_CREATE_ACCOUNTS =
            "CREATE TABLE " + AccountEntry.TABLE_NAME + " (" +
                    AccountEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    AccountEntry.COLUMN_FULL_NAME + " TEXT NOT NULL, " +
                    AccountEntry.COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, " +
                    AccountEntry.COLUMN_PASSWORD_HASH + " TEXT NOT NULL, " +
                    AccountEntry.COLUMN_SALT + " TEXT NOT NULL, " +
                    AccountEntry.COLUMN_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'LOCAL_ONLY', " +
                    AccountEntry.COLUMN_SYNC_VERSION + " INTEGER NOT NULL DEFAULT 1, " +
                    AccountEntry.COLUMN_IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                    AccountEntry.COLUMN_IS_DEMO + " INTEGER NOT NULL DEFAULT 0, " +
                    AccountEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    AccountEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL)";

    private static final String SQL_CREATE_STUDENTS =
            "CREATE TABLE " + StudentEntry.TABLE_NAME + " (" +
                    StudentEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
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
                    StudentEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + StudentEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "UNIQUE(" + StudentEntry.COLUMN_ACCOUNT_ID + ", " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + "))";

    private static final String SQL_CREATE_SUBJECTS =
            "CREATE TABLE " + SubjectEntry.TABLE_NAME + " (" +
                    SubjectEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    SubjectEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    SubjectEntry.COLUMN_TITLE + " TEXT NOT NULL, " +
                    SubjectEntry.COLUMN_LEVEL + " TEXT, " +
                    SubjectEntry.COLUMN_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, " +
                    SubjectEntry.COLUMN_IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                    SubjectEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + SubjectEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_STUDENT_SUBJECTS =
            "CREATE TABLE " + StudentSubjectEntry.TABLE_NAME + " (" +
                    StudentSubjectEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    StudentSubjectEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    StudentSubjectEntry.COLUMN_SUBJECT_ID + " TEXT NOT NULL, " +
                    "FOREIGN KEY(" + StudentSubjectEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + StudentSubjectEntry.COLUMN_SUBJECT_ID + ") REFERENCES " +
                    SubjectEntry.TABLE_NAME + "(" + SubjectEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "UNIQUE(" + StudentSubjectEntry.COLUMN_STUDENT_ID + ", " + StudentSubjectEntry.COLUMN_SUBJECT_ID + "))";

    private static final String SQL_CREATE_SCHEDULES =
            "CREATE TABLE " + ScheduleEntry.TABLE_NAME + " (" +
                    ScheduleEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
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
                    ScheduleEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + ScheduleEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + ScheduleEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + ScheduleEntry.COLUMN_SUBJECT_ID + ") REFERENCES " +
                    SubjectEntry.TABLE_NAME + "(" + SubjectEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_SCHEDULE_FEE_HISTORY =
            "CREATE TABLE IF NOT EXISTS " + ScheduleFeeHistoryEntry.TABLE_NAME + " (" +
                    ScheduleFeeHistoryEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID + " TEXT NOT NULL, " +
                    ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL + " TEXT NOT NULL, " +
                    ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                    ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM + " INTEGER NOT NULL, " +
                    ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO + " INTEGER, " +
                    ScheduleFeeHistoryEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_OCCURRENCES =
            "CREATE TABLE " + OccurrenceEntry.TABLE_NAME + " (" +
                    OccurrenceEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    OccurrenceEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    OccurrenceEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    OccurrenceEntry.COLUMN_SUBJECT_ID + " TEXT, " +
                    OccurrenceEntry.COLUMN_SCHEDULE_ID + " TEXT, " +
                    OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID + " TEXT, " +
                    OccurrenceEntry.COLUMN_DATE + " INTEGER NOT NULL, " +
                    OccurrenceEntry.COLUMN_START_TIME_MINUTES + " INTEGER NOT NULL, " +
                    OccurrenceEntry.COLUMN_DURATION_MINUTES + " INTEGER NOT NULL, " +
                    OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID + " TEXT, " +
                    "FOREIGN KEY(" + OccurrenceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + OccurrenceEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + OccurrenceEntry.COLUMN_SCHEDULE_ID + ") REFERENCES " +
                    ScheduleEntry.TABLE_NAME + "(" + ScheduleEntry.COLUMN_ID + ") ON DELETE SET NULL)";

    private static final String SQL_CREATE_ATTENDANCE =
            "CREATE TABLE " + AttendanceEntry.TABLE_NAME + " (" +
                    AttendanceEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    AttendanceEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    AttendanceEntry.COLUMN_OCCURRENCE_ID + " TEXT NOT NULL, " +
                    AttendanceEntry.COLUMN_STATUS + " TEXT NOT NULL, " +
                    AttendanceEntry.COLUMN_CHARGE_DECISION + " TEXT NOT NULL DEFAULT 'PENDING', " +
                    AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR + " INTEGER NOT NULL DEFAULT 0, " +
                    AttendanceEntry.COLUMN_NEEDS_RESCHEDULE + " INTEGER NOT NULL DEFAULT 0, " +
                    AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID + " TEXT, " +
                    AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " TEXT, " +
                    AttendanceEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + AttendanceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + AttendanceEntry.COLUMN_OCCURRENCE_ID + ") REFERENCES " +
                    OccurrenceEntry.TABLE_NAME + "(" + OccurrenceEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "UNIQUE(" + AttendanceEntry.COLUMN_OCCURRENCE_ID + "))";

    private static final String SQL_CREATE_LESSONS =
            "CREATE TABLE " + LessonEntry.TABLE_NAME + " (" +
                    LessonEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    LessonEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    LessonEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    LessonEntry.COLUMN_OCCURRENCE_ID + " TEXT, " +
                    LessonEntry.COLUMN_TOPIC_COVERED + " TEXT, " +
                    LessonEntry.COLUMN_HOMEWORK + " TEXT, " +
                    LessonEntry.COLUMN_TUTOR_NOTES + " TEXT, " +
                    LessonEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + LessonEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + LessonEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + LessonEntry.COLUMN_OCCURRENCE_ID + ") REFERENCES " +
                    OccurrenceEntry.TABLE_NAME + "(" + OccurrenceEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_FEE_AGREEMENTS =
            "CREATE TABLE IF NOT EXISTS " + FeeAgreementEntry.TABLE_NAME + " (" +
                    FeeAgreementEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    FeeAgreementEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    FeeAgreementEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    FeeAgreementEntry.COLUMN_FEE_TYPE + " TEXT NOT NULL, " +
                    FeeAgreementEntry.COLUMN_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                    FeeAgreementEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + FeeAgreementEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + FeeAgreementEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "UNIQUE(" + FeeAgreementEntry.COLUMN_STUDENT_ID + "))";

    private static final String SQL_CREATE_INVOICES =
            "CREATE TABLE IF NOT EXISTS " + InvoiceEntry.TABLE_NAME + " (" +
                    InvoiceEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
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
                    InvoiceEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    InvoiceEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + InvoiceEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_INVOICE_ITEMS =
            "CREATE TABLE IF NOT EXISTS " + InvoiceItemEntry.TABLE_NAME + " (" +
                    InvoiceItemEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    InvoiceItemEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    InvoiceItemEntry.COLUMN_INVOICE_ID + " TEXT NOT NULL, " +
                    InvoiceItemEntry.COLUMN_SCHEDULE_GROUP_ID + " TEXT, " +
                    InvoiceItemEntry.COLUMN_OCCURRENCE_ID + " TEXT, " +
                    InvoiceItemEntry.COLUMN_DESCRIPTION + " TEXT NOT NULL, " +
                    InvoiceItemEntry.COLUMN_BILLING_MODEL + " TEXT NOT NULL, " +
                    InvoiceItemEntry.COLUMN_QUANTITY + " INTEGER NOT NULL DEFAULT 1, " +
                    InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR + " INTEGER NOT NULL, " +
                    InvoiceItemEntry.COLUMN_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                    InvoiceItemEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + InvoiceItemEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + InvoiceItemEntry.COLUMN_INVOICE_ID + ") REFERENCES " +
                    InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_TRANSACTIONS =
            "CREATE TABLE IF NOT EXISTS " + TransactionEntry.TABLE_NAME + " (" +
                    TransactionEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TransactionEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TransactionEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    TransactionEntry.COLUMN_TXN_ID_DISPLAY + " TEXT NOT NULL, " +
                    TransactionEntry.COLUMN_TYPE + " TEXT NOT NULL, " +
                    TransactionEntry.COLUMN_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                    TransactionEntry.COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    TransactionEntry.COLUMN_METHOD + " TEXT NOT NULL, " +
                    TransactionEntry.COLUMN_REFERENCE_NO + " TEXT, " +
                    TransactionEntry.COLUMN_NOTES + " TEXT, " +
                    TransactionEntry.COLUMN_PARENT_TXN_ID + " TEXT, " +
                    TransactionEntry.COLUMN_IS_REVERSED + " INTEGER NOT NULL DEFAULT 0, " +
                    TransactionEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TransactionEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TransactionEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    StudentEntry.TABLE_NAME + "(" + StudentEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_PAYMENT_ALLOCATIONS =
            "CREATE TABLE IF NOT EXISTS " + PaymentAllocationEntry.TABLE_NAME + " (" +
                    PaymentAllocationEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    PaymentAllocationEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " TEXT NOT NULL, " +
                    PaymentAllocationEntry.COLUMN_INVOICE_ID + " TEXT NOT NULL, " +
                    PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR + " INTEGER NOT NULL, " +
                    PaymentAllocationEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + PaymentAllocationEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + ") REFERENCES " +
                    TransactionEntry.TABLE_NAME + "(" + TransactionEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + PaymentAllocationEntry.COLUMN_INVOICE_ID + ") REFERENCES " +
                    InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_PAYMENTS =
            "CREATE TABLE IF NOT EXISTS " + PaymentEntry.TABLE_NAME + " (" +
                    PaymentEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    PaymentEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    PaymentEntry.COLUMN_INVOICE_ID + " TEXT NOT NULL, " +
                    PaymentEntry.COLUMN_AMOUNT + " INTEGER NOT NULL, " +
                    PaymentEntry.COLUMN_PAYMENT_DATE + " INTEGER NOT NULL, " +
                    PaymentEntry.COLUMN_METHOD + " TEXT NOT NULL, " +
                    "FOREIGN KEY(" + PaymentEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + PaymentEntry.COLUMN_INVOICE_ID + ") REFERENCES " +
                    InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_AUDIT_LOGS =
            "CREATE TABLE IF NOT EXISTS " + AuditLogEntry.TABLE_NAME + " (" +
                    AuditLogEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    AuditLogEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    AuditLogEntry.COLUMN_STUDENT_ID + " TEXT, " +
                    AuditLogEntry.COLUMN_ENTITY_TYPE + " TEXT NOT NULL, " +
                    AuditLogEntry.COLUMN_ENTITY_ID + " TEXT NOT NULL, " +
                    AuditLogEntry.COLUMN_ACTION + " TEXT NOT NULL, " +
                    AuditLogEntry.COLUMN_DETAILS + " TEXT NOT NULL, " +
                    AuditLogEntry.COLUMN_TIMESTAMP + " INTEGER NOT NULL)";

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
        db.execSQL(SQL_CREATE_ACCOUNTS);
        db.execSQL(SQL_CREATE_STUDENTS);
        db.execSQL(SQL_CREATE_SUBJECTS);
        db.execSQL(SQL_CREATE_STUDENT_SUBJECTS);
        db.execSQL(SQL_CREATE_SCHEDULES);
        db.execSQL(SQL_CREATE_SCHEDULE_FEE_HISTORY);
        db.execSQL(SQL_CREATE_OCCURRENCES);
        db.execSQL(SQL_CREATE_ATTENDANCE);
        db.execSQL(SQL_CREATE_LESSONS);
        db.execSQL(SQL_CREATE_FEE_AGREEMENTS);
        db.execSQL(SQL_CREATE_INVOICES);
        db.execSQL(SQL_CREATE_INVOICE_ITEMS);
        db.execSQL(SQL_CREATE_TRANSACTIONS);
        db.execSQL(SQL_CREATE_PAYMENT_ALLOCATIONS);
        db.execSQL(SQL_CREATE_PAYMENTS);
        db.execSQL(SQL_CREATE_AUDIT_LOGS);
        createIndexes(db);
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sched_group ON " + ScheduleEntry.TABLE_NAME + "(" + ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inv_account_student ON " + InvoiceEntry.TABLE_NAME + "(" + InvoiceEntry.COLUMN_ACCOUNT_ID + ", " + InvoiceEntry.COLUMN_STUDENT_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_alloc_invoice ON " + PaymentAllocationEntry.TABLE_NAME + "(" + PaymentAllocationEntry.COLUMN_INVOICE_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_txn_student ON " + TransactionEntry.TABLE_NAME + "(" + TransactionEntry.COLUMN_ACCOUNT_ID + ", " + TransactionEntry.COLUMN_STUDENT_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            safeAddColumn(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_SUBJECT_ID, "TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL(SQL_CREATE_FEE_AGREEMENTS);
            db.execSQL(SQL_CREATE_INVOICES);
            db.execSQL(SQL_CREATE_PAYMENTS);
        }
        if (oldVersion < 4) {
            safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, "TEXT");
        }
        if (oldVersion < 5) {
            upgradeToVersion5(db);
        }
    }

    private void safeAddColumn(SQLiteDatabase db, String table, String column, String typeDef) {
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c.moveToNext()) {
                if (column.equalsIgnoreCase(c.getString(c.getColumnIndexOrThrow("name")))) {
                    return; // Column already exists
                }
            }
        }
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + typeDef);
    }

    private void upgradeToVersion5(SQLiteDatabase db) {
        // 1. Extend recurring_schedules
        safeAddColumn(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID, "TEXT");
        safeAddColumn(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_BILLING_MODEL, "TEXT NOT NULL DEFAULT 'MONTHLY'");
        safeAddColumn(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_ANCHOR_DAY, "INTEGER NOT NULL DEFAULT 1");
        safeAddColumn(db, ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_IS_ACTIVE, "INTEGER NOT NULL DEFAULT 1");

        // 2. Extend class_occurrences & attendance_records
        safeAddColumn(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, "TEXT");
        safeAddColumn(db, OccurrenceEntry.TABLE_NAME, OccurrenceEntry.COLUMN_RESCHEDULED_FROM_ID, "TEXT");

        safeAddColumn(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_CHARGE_DECISION, "TEXT NOT NULL DEFAULT 'PENDING'");
        safeAddColumn(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_APPLICABLE_FEE_MINOR, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_NEEDS_RESCHEDULE, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_MAKEUP_OCCURRENCE_ID, "TEXT");
        safeAddColumn(db, AttendanceEntry.TABLE_NAME, AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID, "TEXT");

        // 3. Extend invoices
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, "TEXT");
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_PERIOD_START, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_PERIOD_END, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_ISSUE_DATE, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_SUBTOTAL_MINOR, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_DISCOUNT_MINOR, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_NOTES, "TEXT");
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_IS_VOID, "INTEGER NOT NULL DEFAULT 0");
        safeAddColumn(db, InvoiceEntry.TABLE_NAME, InvoiceEntry.COLUMN_UPDATED_AT, "INTEGER NOT NULL DEFAULT 0");

        // 4. Create new tables
        db.execSQL(SQL_CREATE_SCHEDULE_FEE_HISTORY);
        db.execSQL(SQL_CREATE_INVOICE_ITEMS);
        db.execSQL(SQL_CREATE_TRANSACTIONS);
        db.execSQL(SQL_CREATE_PAYMENT_ALLOCATIONS);
        db.execSQL(SQL_CREATE_AUDIT_LOGS);
        createIndexes(db);

        // 5. Migrate existing schedule groups and copy legacy student fee_agreements to schedules
        migrateSchedulesAndLegacyFees(db);

        // 6. Migrate legacy invoices and payments into invoice_items, financial_transactions, and payment_allocations
        migrateLegacyInvoicesAndPayments(db);
    }

    private void migrateSchedulesAndLegacyFees(SQLiteDatabase db) {
        // Group existing schedules by (account_id, student_id, start_time_minutes, duration_minutes)
        try (Cursor c = db.rawQuery(
                "SELECT DISTINCT " + ScheduleEntry.COLUMN_ACCOUNT_ID + ", " +
                        ScheduleEntry.COLUMN_STUDENT_ID + ", " +
                        ScheduleEntry.COLUMN_START_TIME_MINUTES + ", " +
                        ScheduleEntry.COLUMN_DURATION_MINUTES + ", " +
                        "MIN(" + ScheduleEntry.COLUMN_START_DATE + ") " +
                        "FROM " + ScheduleEntry.TABLE_NAME +
                        " WHERE " + ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID + " IS NULL " +
                        "GROUP BY " + ScheduleEntry.COLUMN_ACCOUNT_ID + ", " +
                        ScheduleEntry.COLUMN_STUDENT_ID + ", " +
                        ScheduleEntry.COLUMN_START_TIME_MINUTES + ", " +
                        ScheduleEntry.COLUMN_DURATION_MINUTES, null)) {
            while (c.moveToNext()) {
                String accountId = c.getString(0);
                String studentId = c.getString(1);
                int startTime = c.getInt(2);
                int duration = c.getInt(3);
                long startDate = c.getLong(4);

                String groupId = UUID.randomUUID().toString();
                String billingModel = "MONTHLY";
                int feeMinor = 0;

                try (Cursor fc = db.query(FeeAgreementEntry.TABLE_NAME,
                        new String[]{FeeAgreementEntry.COLUMN_FEE_TYPE, FeeAgreementEntry.COLUMN_AMOUNT_MINOR},
                        FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?", new String[]{studentId}, null, null, null)) {
                    if (fc.moveToFirst()) {
                        billingModel = fc.getString(0);
                        feeMinor = fc.getInt(1);
                    }
                }

                Calendar cal = Calendar.getInstance();
                if (startDate > 0) cal.setTimeInMillis(startDate);
                int anchorDay = cal.get(Calendar.DAY_OF_MONTH);

                ContentValues sv = new ContentValues();
                sv.put(ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
                sv.put(ScheduleEntry.COLUMN_BILLING_MODEL, billingModel);
                sv.put(ScheduleEntry.COLUMN_FEE_AMOUNT_MINOR, feeMinor);
                sv.put(ScheduleEntry.COLUMN_ANCHOR_DAY, anchorDay);

                db.update(ScheduleEntry.TABLE_NAME, sv,
                        ScheduleEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                                ScheduleEntry.COLUMN_STUDENT_ID + " = ? AND " +
                                ScheduleEntry.COLUMN_START_TIME_MINUTES + " = ? AND " +
                                ScheduleEntry.COLUMN_DURATION_MINUTES + " = ?",
                        new String[]{accountId, studentId, String.valueOf(startTime), String.valueOf(duration)});

                if (feeMinor > 0) {
                    ContentValues hv = new ContentValues();
                    hv.put(ScheduleFeeHistoryEntry.COLUMN_ID, UUID.randomUUID().toString());
                    hv.put(ScheduleFeeHistoryEntry.COLUMN_ACCOUNT_ID, accountId);
                    hv.put(ScheduleFeeHistoryEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
                    hv.put(ScheduleFeeHistoryEntry.COLUMN_BILLING_MODEL, billingModel);
                    hv.put(ScheduleFeeHistoryEntry.COLUMN_FEE_AMOUNT_MINOR, feeMinor);
                    hv.put(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_FROM, startDate > 0 ? startDate : System.currentTimeMillis());
                    hv.putNull(ScheduleFeeHistoryEntry.COLUMN_EFFECTIVE_TO);
                    hv.put(ScheduleFeeHistoryEntry.COLUMN_CREATED_AT, System.currentTimeMillis());
                    db.insert(ScheduleFeeHistoryEntry.TABLE_NAME, null, hv);
                }
            }
        }

        // Backfill schedule_group_id on class_occurrences from their linked schedule
        db.execSQL("UPDATE " + OccurrenceEntry.TABLE_NAME +
                " SET " + OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID + " = (" +
                "SELECT s." + ScheduleEntry.COLUMN_SCHEDULE_GROUP_ID +
                " FROM " + ScheduleEntry.TABLE_NAME + " s " +
                " WHERE s." + ScheduleEntry.COLUMN_ID + " = " + OccurrenceEntry.TABLE_NAME + "." + OccurrenceEntry.COLUMN_SCHEDULE_ID + ")" +
                " WHERE " + OccurrenceEntry.COLUMN_SCHEDULE_ID + " IS NOT NULL");

        // Mark existing PRESENT attendance records as CHARGE so historical per-class records remain consistent
        db.execSQL("UPDATE " + AttendanceEntry.TABLE_NAME +
                " SET " + AttendanceEntry.COLUMN_CHARGE_DECISION + " = 'CHARGE' " +
                " WHERE " + AttendanceEntry.COLUMN_STATUS + " = 'PRESENT'");
    }

    private void migrateLegacyInvoicesAndPayments(SQLiteDatabase db) {
        int seq = 1;
        int year = Calendar.getInstance().get(Calendar.YEAR);
        try (Cursor c = db.query(InvoiceEntry.TABLE_NAME, null, null, null, null, null, InvoiceEntry.COLUMN_CREATED_AT + " ASC")) {
            while (c.moveToNext()) {
                String invId = c.getString(c.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ID));
                String accId = c.getString(c.getColumnIndexOrThrow(InvoiceEntry.COLUMN_ACCOUNT_ID));
                String stuId = c.getString(c.getColumnIndexOrThrow(InvoiceEntry.COLUMN_STUDENT_ID));
                String dispId = c.getString(c.getColumnIndexOrThrow(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY));
                String period = c.getString(c.getColumnIndexOrThrow(InvoiceEntry.COLUMN_BILLING_PERIOD));
                int total = c.getInt(c.getColumnIndexOrThrow(InvoiceEntry.COLUMN_TOTAL_AMOUNT));
                int paid = c.getInt(c.getColumnIndexOrThrow(InvoiceEntry.COLUMN_PAID_AMOUNT));
                long created = c.getLong(c.getColumnIndexOrThrow(InvoiceEntry.COLUMN_CREATED_AT));

                ContentValues up = new ContentValues();
                if (dispId == null || dispId.trim().isEmpty() || dispId.startsWith("#INV-")) {
                    up.put(InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, String.format(Locale.US, "TS-%04d-%06d", year, seq++));
                }
                up.put(InvoiceEntry.COLUMN_ISSUE_DATE, created);
                up.put(InvoiceEntry.COLUMN_PERIOD_START, created);
                up.put(InvoiceEntry.COLUMN_PERIOD_END, created);
                up.put(InvoiceEntry.COLUMN_SUBTOTAL_MINOR, total);
                up.put(InvoiceEntry.COLUMN_UPDATED_AT, created);
                db.update(InvoiceEntry.TABLE_NAME, up, InvoiceEntry.COLUMN_ID + " = ?", new String[]{invId});

                // Ensure at least one snapshot line item exists for legacy invoice
                try (Cursor ic = db.query(InvoiceItemEntry.TABLE_NAME, new String[]{InvoiceItemEntry.COLUMN_ID},
                        InvoiceItemEntry.COLUMN_INVOICE_ID + " = ?", new String[]{invId}, null, null, null)) {
                    if (!ic.moveToFirst()) {
                        ContentValues itemVal = new ContentValues();
                        itemVal.put(InvoiceItemEntry.COLUMN_ID, UUID.randomUUID().toString());
                        itemVal.put(InvoiceItemEntry.COLUMN_ACCOUNT_ID, accId);
                        itemVal.put(InvoiceItemEntry.COLUMN_INVOICE_ID, invId);
                        itemVal.put(InvoiceItemEntry.COLUMN_DESCRIPTION, "Tuition Fee (" + period + ")");
                        itemVal.put(InvoiceItemEntry.COLUMN_BILLING_MODEL, "MONTHLY");
                        itemVal.put(InvoiceItemEntry.COLUMN_QUANTITY, 1);
                        itemVal.put(InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR, total);
                        itemVal.put(InvoiceItemEntry.COLUMN_AMOUNT_MINOR, total);
                        itemVal.put(InvoiceItemEntry.COLUMN_CREATED_AT, created);
                        db.insert(InvoiceItemEntry.TABLE_NAME, null, itemVal);
                    }
                }

                // Migrate legacy payments table rows or paid_amount into financial_transactions + payment_allocations
                int migratedPaidSum = 0;
                try (Cursor pc = db.query(PaymentEntry.TABLE_NAME, null,
                        PaymentEntry.COLUMN_INVOICE_ID + " = ?", new String[]{invId}, null, null, PaymentEntry.COLUMN_PAYMENT_DATE + " ASC")) {
                    int pSeq = 1;
                    while (pc.moveToNext()) {
                        String payId = pc.getString(pc.getColumnIndexOrThrow(PaymentEntry.COLUMN_ID));
                        int amt = pc.getInt(pc.getColumnIndexOrThrow(PaymentEntry.COLUMN_AMOUNT));
                        long pDate = pc.getLong(pc.getColumnIndexOrThrow(PaymentEntry.COLUMN_PAYMENT_DATE));
                        String method = pc.getString(pc.getColumnIndexOrThrow(PaymentEntry.COLUMN_METHOD));
                        migratedPaidSum += amt;

                        ContentValues tv = new ContentValues();
                        tv.put(TransactionEntry.COLUMN_ID, payId);
                        tv.put(TransactionEntry.COLUMN_ACCOUNT_ID, accId);
                        tv.put(TransactionEntry.COLUMN_STUDENT_ID, stuId);
                        tv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, String.format(Locale.US, "TXN-%04d-%06d", year, (seq * 100) + (pSeq++)));
                        tv.put(TransactionEntry.COLUMN_TYPE, "PAYMENT");
                        tv.put(TransactionEntry.COLUMN_AMOUNT_MINOR, amt);
                        tv.put(TransactionEntry.COLUMN_TIMESTAMP, pDate);
                        tv.put(TransactionEntry.COLUMN_METHOD, method);
                        tv.put(TransactionEntry.COLUMN_NOTES, "Migrated payment");
                        tv.put(TransactionEntry.COLUMN_CREATED_AT, pDate);
                        db.insertWithOnConflict(TransactionEntry.TABLE_NAME, null, tv, SQLiteDatabase.CONFLICT_IGNORE);

                        ContentValues av = new ContentValues();
                        av.put(PaymentAllocationEntry.COLUMN_ID, UUID.randomUUID().toString());
                        av.put(PaymentAllocationEntry.COLUMN_ACCOUNT_ID, accId);
                        av.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, payId);
                        av.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invId);
                        av.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, amt);
                        av.put(PaymentAllocationEntry.COLUMN_CREATED_AT, pDate);
                        db.insert(PaymentAllocationEntry.TABLE_NAME, null, av);
                    }
                }

                // If legacy invoice had paid_amount > migratedPaidSum (recorded without a payments row), synthesize a transaction
                if (paid > migratedPaidSum) {
                    int diff = paid - migratedPaidSum;
                    String txnId = UUID.randomUUID().toString();
                    ContentValues tv = new ContentValues();
                    tv.put(TransactionEntry.COLUMN_ID, txnId);
                    tv.put(TransactionEntry.COLUMN_ACCOUNT_ID, accId);
                    tv.put(TransactionEntry.COLUMN_STUDENT_ID, stuId);
                    tv.put(TransactionEntry.COLUMN_TXN_ID_DISPLAY, String.format(Locale.US, "TXN-%04d-%06d", year, (seq * 100) + 99));
                    tv.put(TransactionEntry.COLUMN_TYPE, "PAYMENT");
                    tv.put(TransactionEntry.COLUMN_AMOUNT_MINOR, diff);
                    tv.put(TransactionEntry.COLUMN_TIMESTAMP, created);
                    tv.put(TransactionEntry.COLUMN_METHOD, "Cash");
                    tv.put(TransactionEntry.COLUMN_NOTES, "Legacy balance migration");
                    tv.put(TransactionEntry.COLUMN_CREATED_AT, created);
                    db.insert(TransactionEntry.TABLE_NAME, null, tv);

                    ContentValues av = new ContentValues();
                    av.put(PaymentAllocationEntry.COLUMN_ID, UUID.randomUUID().toString());
                    av.put(PaymentAllocationEntry.COLUMN_ACCOUNT_ID, accId);
                    av.put(PaymentAllocationEntry.COLUMN_TRANSACTION_ID, txnId);
                    av.put(PaymentAllocationEntry.COLUMN_INVOICE_ID, invId);
                    av.put(PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR, diff);
                    av.put(PaymentAllocationEntry.COLUMN_CREATED_AT, created);
                    db.insert(PaymentAllocationEntry.TABLE_NAME, null, av);
                }
            }
        }
    }
}
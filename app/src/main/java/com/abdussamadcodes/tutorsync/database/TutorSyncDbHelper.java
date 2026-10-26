package com.abdussamadcodes.tutorsync.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class TutorSyncDbHelper extends SQLiteOpenHelper {

    private static final String SQL_CREATE_ACCOUNTS =
            "CREATE TABLE " + TutorSyncContract.AccountEntry.TABLE_NAME + " (" +
                    TutorSyncContract.AccountEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.AccountEntry.COLUMN_FULL_NAME + " TEXT NOT NULL, " +
                    TutorSyncContract.AccountEntry.COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, " +
                    TutorSyncContract.AccountEntry.COLUMN_PASSWORD_HASH + " TEXT NOT NULL, " +
                    TutorSyncContract.AccountEntry.COLUMN_SALT + " TEXT NOT NULL, " +
                    TutorSyncContract.AccountEntry.COLUMN_PHONE + " TEXT, " +
                    TutorSyncContract.AccountEntry.COLUMN_EMAIL + " TEXT, " +
                    TutorSyncContract.AccountEntry.COLUMN_ADDRESS + " TEXT, " +
                    TutorSyncContract.AccountEntry.COLUMN_DOB + " TEXT, " +
                    TutorSyncContract.AccountEntry.COLUMN_PROFILE_PIC_PATH + " TEXT, " +
                    TutorSyncContract.AccountEntry.COLUMN_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'LOCAL_ONLY', " +
                    TutorSyncContract.AccountEntry.COLUMN_SYNC_VERSION + " INTEGER NOT NULL DEFAULT 1, " +
                    TutorSyncContract.AccountEntry.COLUMN_IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                    TutorSyncContract.AccountEntry.COLUMN_IS_DEMO + " INTEGER NOT NULL DEFAULT 0, " +
                    TutorSyncContract.AccountEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    TutorSyncContract.AccountEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL)";

    private static final String SQL_CREATE_STUDENTS =
            "CREATE TABLE " + TutorSyncContract.StudentEntry.TABLE_NAME + " (" +
                    TutorSyncContract.StudentEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.StudentEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " TEXT, " +
                    TutorSyncContract.StudentEntry.COLUMN_NAME + " TEXT NOT NULL, " +
                    TutorSyncContract.StudentEntry.COLUMN_GRADE + " TEXT, " +
                    TutorSyncContract.StudentEntry.COLUMN_SCHOOL + " TEXT, " +
                    TutorSyncContract.StudentEntry.COLUMN_GUARDIAN_NAME + " TEXT, " +
                    TutorSyncContract.StudentEntry.COLUMN_GUARDIAN_PHONE + " TEXT, " +
                    TutorSyncContract.StudentEntry.COLUMN_ADDRESS + " TEXT, " +
                    TutorSyncContract.StudentEntry.COLUMN_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, " +
                    TutorSyncContract.StudentEntry.COLUMN_IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                    TutorSyncContract.StudentEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.StudentEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "UNIQUE(" + TutorSyncContract.StudentEntry.COLUMN_ACCOUNT_ID + ", " + TutorSyncContract.StudentEntry.COLUMN_STUDENT_ID_DISPLAY + "))";

    private static final String SQL_CREATE_SUBJECTS =
            "CREATE TABLE " + TutorSyncContract.SubjectEntry.TABLE_NAME + " (" +
                    TutorSyncContract.SubjectEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.SubjectEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.SubjectEntry.COLUMN_TITLE + " TEXT NOT NULL, " +
                    TutorSyncContract.SubjectEntry.COLUMN_LEVEL + " TEXT, " +
                    TutorSyncContract.SubjectEntry.COLUMN_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, " +
                    TutorSyncContract.SubjectEntry.COLUMN_IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                    TutorSyncContract.SubjectEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.SubjectEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_STUDENT_SUBJECTS =
            "CREATE TABLE " + TutorSyncContract.StudentSubjectEntry.TABLE_NAME + " (" +
                    TutorSyncContract.StudentSubjectEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.StudentSubjectEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.StudentSubjectEntry.COLUMN_SUBJECT_ID + " TEXT NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.StudentSubjectEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    TutorSyncContract.StudentEntry.TABLE_NAME + "(" + TutorSyncContract.StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.StudentSubjectEntry.COLUMN_SUBJECT_ID + ") REFERENCES " +
                    TutorSyncContract.SubjectEntry.TABLE_NAME + "(" + TutorSyncContract.SubjectEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "UNIQUE(" + TutorSyncContract.StudentSubjectEntry.COLUMN_STUDENT_ID + ", " + TutorSyncContract.StudentSubjectEntry.COLUMN_SUBJECT_ID + "))";

    private static final String SQL_CREATE_SCHEDULES =
            "CREATE TABLE " + TutorSyncContract.ScheduleEntry.TABLE_NAME + " (" +
                    TutorSyncContract.ScheduleEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_SUBJECT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_DAY_OF_WEEK + " INTEGER NOT NULL, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_START_TIME_MINUTES + " INTEGER NOT NULL, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_DURATION_MINUTES + " INTEGER NOT NULL, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_START_DATE + " INTEGER NOT NULL, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_END_DATE + " INTEGER NOT NULL, " +
                    TutorSyncContract.ScheduleEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.ScheduleEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.ScheduleEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    TutorSyncContract.StudentEntry.TABLE_NAME + "(" + TutorSyncContract.StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.ScheduleEntry.COLUMN_SUBJECT_ID + ") REFERENCES " +
                    TutorSyncContract.SubjectEntry.TABLE_NAME + "(" + TutorSyncContract.SubjectEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_OCCURRENCES =
            "CREATE TABLE " + TutorSyncContract.OccurrenceEntry.TABLE_NAME + " (" +
                    TutorSyncContract.OccurrenceEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.OccurrenceEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.OccurrenceEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.OccurrenceEntry.COLUMN_SUBJECT_ID + " TEXT, " +
                    TutorSyncContract.OccurrenceEntry.COLUMN_SCHEDULE_ID + " TEXT, " +
                    TutorSyncContract.OccurrenceEntry.COLUMN_DATE + " INTEGER NOT NULL, " +
                    TutorSyncContract.OccurrenceEntry.COLUMN_START_TIME_MINUTES + " INTEGER NOT NULL, " +
                    TutorSyncContract.OccurrenceEntry.COLUMN_DURATION_MINUTES + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.OccurrenceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.OccurrenceEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    TutorSyncContract.StudentEntry.TABLE_NAME + "(" + TutorSyncContract.StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.OccurrenceEntry.COLUMN_SCHEDULE_ID + ") REFERENCES " +
                    TutorSyncContract.ScheduleEntry.TABLE_NAME + "(" + TutorSyncContract.ScheduleEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_ATTENDANCE =
            "CREATE TABLE " + TutorSyncContract.AttendanceEntry.TABLE_NAME + " (" +
                    TutorSyncContract.AttendanceEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.AttendanceEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.AttendanceEntry.COLUMN_OCCURRENCE_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.AttendanceEntry.COLUMN_STATUS + " TEXT NOT NULL, " +
                    TutorSyncContract.AttendanceEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.AttendanceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.AttendanceEntry.COLUMN_OCCURRENCE_ID + ") REFERENCES " +
                    TutorSyncContract.OccurrenceEntry.TABLE_NAME + "(" + TutorSyncContract.OccurrenceEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "UNIQUE(" + TutorSyncContract.AttendanceEntry.COLUMN_OCCURRENCE_ID + "))";

    private static final String SQL_CREATE_LESSONS =
            "CREATE TABLE " + TutorSyncContract.LessonEntry.TABLE_NAME + " (" +
                    TutorSyncContract.LessonEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.LessonEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.LessonEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.LessonEntry.COLUMN_OCCURRENCE_ID + " TEXT, " +
                    TutorSyncContract.LessonEntry.COLUMN_TOPIC_COVERED + " TEXT, " +
                    TutorSyncContract.LessonEntry.COLUMN_HOMEWORK + " TEXT, " +
                    TutorSyncContract.LessonEntry.COLUMN_TUTOR_NOTES + " TEXT, " +
                    TutorSyncContract.LessonEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.LessonEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.LessonEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    TutorSyncContract.StudentEntry.TABLE_NAME + "(" + TutorSyncContract.StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.LessonEntry.COLUMN_OCCURRENCE_ID + ") REFERENCES " +
                    TutorSyncContract.OccurrenceEntry.TABLE_NAME + "(" + TutorSyncContract.OccurrenceEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_FEE_AGREEMENTS =
            "CREATE TABLE " + TutorSyncContract.FeeAgreementEntry.TABLE_NAME + " (" +
                    TutorSyncContract.FeeAgreementEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.FeeAgreementEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.FeeAgreementEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.FeeAgreementEntry.COLUMN_FEE_TYPE + " TEXT NOT NULL, " +
                    TutorSyncContract.FeeAgreementEntry.COLUMN_AMOUNT_MINOR + " INTEGER NOT NULL, " +
                    TutorSyncContract.FeeAgreementEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.FeeAgreementEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.FeeAgreementEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    TutorSyncContract.StudentEntry.TABLE_NAME + "(" + TutorSyncContract.StudentEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "UNIQUE(" + TutorSyncContract.FeeAgreementEntry.COLUMN_STUDENT_ID + "))";

    private static final String SQL_CREATE_INVOICES =
            "CREATE TABLE " + TutorSyncContract.InvoiceEntry.TABLE_NAME + " (" +
                    TutorSyncContract.InvoiceEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_STUDENT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " TEXT, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_BILLING_PERIOD + " TEXT NOT NULL, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_TOTAL_AMOUNT + " INTEGER NOT NULL, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_PAID_AMOUNT + " INTEGER NOT NULL DEFAULT 0, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_STATUS + " TEXT NOT NULL, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_DUE_DATE + " INTEGER NOT NULL, " +
                    TutorSyncContract.InvoiceEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.InvoiceEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.InvoiceEntry.COLUMN_STUDENT_ID + ") REFERENCES " +
                    TutorSyncContract.StudentEntry.TABLE_NAME + "(" + TutorSyncContract.StudentEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_PAYMENTS =
            "CREATE TABLE " + TutorSyncContract.PaymentEntry.TABLE_NAME + " (" +
                    TutorSyncContract.PaymentEntry.COLUMN_ID + " TEXT PRIMARY KEY, " +
                    TutorSyncContract.PaymentEntry.COLUMN_ACCOUNT_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.PaymentEntry.COLUMN_INVOICE_ID + " TEXT NOT NULL, " +
                    TutorSyncContract.PaymentEntry.COLUMN_AMOUNT + " INTEGER NOT NULL, " +
                    TutorSyncContract.PaymentEntry.COLUMN_PAYMENT_DATE + " INTEGER NOT NULL, " +
                    TutorSyncContract.PaymentEntry.COLUMN_METHOD + " TEXT NOT NULL, " +
                    "FOREIGN KEY(" + TutorSyncContract.PaymentEntry.COLUMN_ACCOUNT_ID + ") REFERENCES " +
                    TutorSyncContract.AccountEntry.TABLE_NAME + "(" + TutorSyncContract.AccountEntry.COLUMN_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + TutorSyncContract.PaymentEntry.COLUMN_INVOICE_ID + ") REFERENCES " +
                    TutorSyncContract.InvoiceEntry.TABLE_NAME + "(" + TutorSyncContract.InvoiceEntry.COLUMN_ID + ") ON DELETE CASCADE)";

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
        db.execSQL(SQL_CREATE_OCCURRENCES);
        db.execSQL(SQL_CREATE_ATTENDANCE);
        db.execSQL(SQL_CREATE_LESSONS);
        db.execSQL(SQL_CREATE_FEE_AGREEMENTS);
        db.execSQL(SQL_CREATE_INVOICES);
        db.execSQL(SQL_CREATE_PAYMENTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumnIfNotExists(db, TutorSyncContract.OccurrenceEntry.TABLE_NAME,
                    TutorSyncContract.OccurrenceEntry.COLUMN_SUBJECT_ID, "TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL(SQL_CREATE_FEE_AGREEMENTS);
            db.execSQL(SQL_CREATE_INVOICES);
            db.execSQL(SQL_CREATE_PAYMENTS);
        }
        if (oldVersion < 4) {
            addColumnIfNotExists(db, TutorSyncContract.InvoiceEntry.TABLE_NAME,
                    TutorSyncContract.InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY, "TEXT");
        }
        if (oldVersion < 5) {
            addColumnIfNotExists(db, TutorSyncContract.AccountEntry.TABLE_NAME,
                    TutorSyncContract.AccountEntry.COLUMN_PHONE, "TEXT");
            addColumnIfNotExists(db, TutorSyncContract.AccountEntry.TABLE_NAME,
                    TutorSyncContract.AccountEntry.COLUMN_EMAIL, "TEXT");
            addColumnIfNotExists(db, TutorSyncContract.AccountEntry.TABLE_NAME,
                    TutorSyncContract.AccountEntry.COLUMN_ADDRESS, "TEXT");
            addColumnIfNotExists(db, TutorSyncContract.AccountEntry.TABLE_NAME,
                    TutorSyncContract.AccountEntry.COLUMN_DOB, "TEXT");
            addColumnIfNotExists(db, TutorSyncContract.AccountEntry.TABLE_NAME,
                    TutorSyncContract.AccountEntry.COLUMN_PROFILE_PIC_PATH, "TEXT");
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
package com.abdussamadcodes.tutorsync.database;

import android.content.Context;
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
                    TutorSyncContract.SubjectEntry.COLUMN_COLOR_TAG + " TEXT, " +
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migrations will be handled here if version increments
    }
}
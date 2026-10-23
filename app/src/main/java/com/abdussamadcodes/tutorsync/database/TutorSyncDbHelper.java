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

    // Phase 4+ table creation strings (Students, Subjects, etc.) will go here.

    public TutorSyncDbHelper(Context context) {
        super(context, TutorSyncContract.DATABASE_NAME, null, TutorSyncContract.DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // Enable Write-Ahead Logging for better concurrent read/write performance
        db.enableWriteAheadLogging();
        // Enable foreign key constraints
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ACCOUNTS);
        // Phase 4+ table executions will go here.
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Implement database migration strategies here when incrementing DATABASE_VERSION
    }
}
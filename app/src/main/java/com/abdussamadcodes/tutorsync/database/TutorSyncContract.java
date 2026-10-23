package com.abdussamadcodes.tutorsync.database;

import android.provider.BaseColumns;

public final class TutorSyncContract {
    private TutorSyncContract() {}

    public static final String DATABASE_NAME = "tutorsync.db";
    public static final int DATABASE_VERSION = 1;

    public static class AccountEntry implements BaseColumns {
        public static final String TABLE_NAME = "accounts";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_FULL_NAME = "full_name";
        public static final String COLUMN_USERNAME = "username_normalized";
        public static final String COLUMN_PASSWORD_HASH = "password_hash";
        public static final String COLUMN_SALT = "salt";
        public static final String COLUMN_SYNC_STATUS = "sync_status";
        public static final String COLUMN_SYNC_VERSION = "sync_version";
        public static final String COLUMN_IS_DELETED = "is_deleted";
        public static final String COLUMN_IS_DEMO = "is_demo";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
    }

    // Other tables (students, subjects, etc.) will be added here in Phase 4.
}
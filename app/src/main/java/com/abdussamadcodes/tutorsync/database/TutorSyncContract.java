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

    public static class StudentEntry implements BaseColumns {
        public static final String TABLE_NAME = "students";
        public static final String COLUMN_ID = "id"; // The hidden UUID
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID_DISPLAY = "student_id_display"; // The visible unique ID
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_GRADE = "grade";
        public static final String COLUMN_SCHOOL = "school";
        public static final String COLUMN_GUARDIAN_NAME = "guardian_name";
        public static final String COLUMN_GUARDIAN_PHONE = "guardian_phone";
        public static final String COLUMN_ADDRESS = "address";
        public static final String COLUMN_IS_ACTIVE = "is_active";
        public static final String COLUMN_IS_DELETED = "is_deleted";
        public static final String COLUMN_UPDATED_AT = "updated_at";
    }

    public static class SubjectEntry implements BaseColumns {
        public static final String TABLE_NAME = "subjects";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_LEVEL = "level";
        public static final String COLUMN_COLOR_TAG = "color_tag";
        public static final String COLUMN_IS_ACTIVE = "is_active";
        public static final String COLUMN_IS_DELETED = "is_deleted";
        public static final String COLUMN_UPDATED_AT = "updated_at";
    }

    public static class StudentSubjectEntry implements BaseColumns {
        public static final String TABLE_NAME = "student_subjects";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_SUBJECT_ID = "subject_id";
    }
}
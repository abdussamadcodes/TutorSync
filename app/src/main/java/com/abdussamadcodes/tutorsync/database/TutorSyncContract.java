package com.abdussamadcodes.tutorsync.database;

import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.UUID;

public final class TutorSyncContract {
    private TutorSyncContract() {}

    public static final String DATABASE_NAME = "tutorsync.db";
    public static final int DATABASE_VERSION = 7;

    public static final String COLUMN_SYNC_CREATED_AT = "created_at";
    public static final String COLUMN_SYNC_UPDATED_AT = "updated_at";
    public static final String COLUMN_SYNC_LAST_MODIFIED = "last_modified";

    @NonNull
    public static String ensureValidUuid(@Nullable String candidateId) {
        if (candidateId != null) {
            String trimmed = candidateId.trim();
            if (!trimmed.isEmpty()) {
                try {
                    return UUID.fromString(trimmed).toString();
                } catch (IllegalArgumentException ignored) {
                    // Preserve existing non-empty deterministic or legacy IDs without generating new ones
                    return trimmed;
                }
            }
        }
        return UUID.randomUUID().toString();
    }

    public static boolean isValidRecordId(@Nullable String id) {
        return id != null && !id.trim().isEmpty();
    }

    public static class ScheduleFeeHistoryEntry implements BaseColumns {
        public static final String TABLE_NAME = "schedule_fee_history";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_SCHEDULE_GROUP_ID = "schedule_group_id";
        public static final String COLUMN_BILLING_MODEL = "billing_model";
        public static final String COLUMN_FEE_AMOUNT_MINOR = "fee_amount_minor";
        public static final String COLUMN_ANCHOR_DAY = "anchor_day";
        public static final String COLUMN_EFFECTIVE_FROM = "effective_from";
        public static final String COLUMN_EFFECTIVE_TO = "effective_to";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class AccountEntry implements BaseColumns {
        public static final String TABLE_NAME = "accounts";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_FULL_NAME = "full_name";
        public static final String COLUMN_USERNAME = "username_normalized";
        public static final String COLUMN_PASSWORD_HASH = "password_hash";
        public static final String COLUMN_SALT = "salt";
        public static final String COLUMN_PHONE = "phone";
        public static final String COLUMN_EMAIL = "email";
        public static final String COLUMN_ADDRESS = "address";
        public static final String COLUMN_DOB = "dob";
        public static final String COLUMN_PROFILE_PIC_PATH = "profile_pic_path";
        public static final String COLUMN_SYNC_STATUS = "sync_status";
        public static final String COLUMN_SYNC_VERSION = "sync_version";
        public static final String COLUMN_IS_DELETED = "is_deleted";
        public static final String COLUMN_IS_DEMO = "is_demo";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class StudentEntry implements BaseColumns {
        public static final String TABLE_NAME = "students";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID_DISPLAY = "student_id_display";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_GRADE = "grade";
        public static final String COLUMN_SCHOOL = "school";
        public static final String COLUMN_GUARDIAN_NAME = "guardian_name";
        public static final String COLUMN_GUARDIAN_PHONE = "guardian_phone";
        public static final String COLUMN_ADDRESS = "address";
        public static final String COLUMN_IS_ACTIVE = "is_active";
        public static final String COLUMN_IS_DELETED = "is_deleted";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class SubjectEntry implements BaseColumns {
        public static final String TABLE_NAME = "subjects";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_LEVEL = "level";
        public static final String COLUMN_IS_ACTIVE = "is_active";
        public static final String COLUMN_IS_DELETED = "is_deleted";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class StudentSubjectEntry implements BaseColumns {
        public static final String TABLE_NAME = "student_subjects";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_SUBJECT_ID = "subject_id";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class ScheduleEntry implements BaseColumns {
        public static final String TABLE_NAME = "recurring_schedules";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_SUBJECT_ID = "subject_id";
        public static final String COLUMN_SCHEDULE_GROUP_ID = "schedule_group_id";
        public static final String COLUMN_DAY_OF_WEEK = "day_of_week";
        public static final String COLUMN_START_TIME_MINUTES = "start_time_minutes";
        public static final String COLUMN_DURATION_MINUTES = "duration_minutes";
        public static final String COLUMN_START_DATE = "start_date";
        public static final String COLUMN_END_DATE = "end_date";
        public static final String COLUMN_BILLING_MODEL = "billing_model";
        public static final String COLUMN_FEE_AMOUNT_MINOR = "fee_amount_minor";
        public static final String COLUMN_ANCHOR_DAY = "anchor_day";
        public static final String COLUMN_IS_ACTIVE = "is_active";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class OccurrenceEntry implements BaseColumns {
        public static final String TABLE_NAME = "class_occurrences";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_SUBJECT_ID = "subject_id";
        public static final String COLUMN_SCHEDULE_ID = "schedule_id";
        public static final String COLUMN_SCHEDULE_GROUP_ID = "schedule_group_id";
        public static final String COLUMN_DATE = "date";
        public static final String COLUMN_START_TIME_MINUTES = "start_time_minutes";
        public static final String COLUMN_DURATION_MINUTES = "duration_minutes";
        public static final String COLUMN_RESCHEDULED_FROM_ID = "rescheduled_from_id";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class AttendanceEntry implements BaseColumns {
        public static final String TABLE_NAME = "attendance_records";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_OCCURRENCE_ID = "occurrence_id";
        public static final String COLUMN_STATUS = "status";
        public static final String COLUMN_CHARGE_DECISION = "charge_decision";
        public static final String COLUMN_APPLICABLE_FEE_MINOR = "applicable_fee_minor";
        public static final String COLUMN_NEEDS_RESCHEDULE = "needs_reschedule";
        public static final String COLUMN_MAKEUP_OCCURRENCE_ID = "makeup_occurrence_id";
        public static final String COLUMN_BILLED_INVOICE_ITEM_ID = "billed_invoice_item_id";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class LessonEntry implements BaseColumns {
        public static final String TABLE_NAME = "lessons";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_OCCURRENCE_ID = "occurrence_id";
        public static final String COLUMN_TOPIC_COVERED = "topic_covered";
        public static final String COLUMN_HOMEWORK = "homework";
        public static final String COLUMN_TUTOR_NOTES = "tutor_notes";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class FeeAgreementEntry implements BaseColumns {
        public static final String TABLE_NAME = "fee_agreements";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_FEE_TYPE = "fee_type";
        public static final String COLUMN_AMOUNT_MINOR = "amount_minor_units";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class InvoiceEntry implements BaseColumns {
        public static final String TABLE_NAME = "invoices";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_INVOICE_ID_DISPLAY = "invoice_id_display";
        public static final String COLUMN_BILLING_PERIOD = "billing_period";
        public static final String COLUMN_PERIOD_START = "period_start";
        public static final String COLUMN_PERIOD_END = "period_end";
        public static final String COLUMN_ISSUE_DATE = "issue_date";
        public static final String COLUMN_DUE_DATE = "due_date";
        public static final String COLUMN_SUBTOTAL_MINOR = "subtotal_minor";
        public static final String COLUMN_DISCOUNT_MINOR = "discount_minor";
        public static final String COLUMN_TOTAL_AMOUNT = "total_amount";
        public static final String COLUMN_PAID_AMOUNT = "paid_amount";
        public static final String COLUMN_STATUS = "status";
        public static final String COLUMN_NOTES = "notes";
        public static final String COLUMN_IS_VOID = "is_void";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class InvoiceItemEntry implements BaseColumns {
        public static final String TABLE_NAME = "invoice_items";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_INVOICE_ID = "invoice_id";
        public static final String COLUMN_SCHEDULE_GROUP_ID = "schedule_group_id";
        public static final String COLUMN_OCCURRENCE_ID = "occurrence_id";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_BILLING_MODEL = "billing_model";
        public static final String COLUMN_QUANTITY = "quantity";
        public static final String COLUMN_UNIT_PRICE_MINOR = "unit_price_minor";
        public static final String COLUMN_AMOUNT_MINOR = "amount_minor";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class PaymentEntry implements BaseColumns {
        public static final String TABLE_NAME = "payments";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_INVOICE_ID = "invoice_id";
        public static final String COLUMN_AMOUNT = "amount";
        public static final String COLUMN_PAYMENT_DATE = "payment_date";
        public static final String COLUMN_METHOD = "method";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class TransactionEntry implements BaseColumns {
        public static final String TABLE_NAME = "financial_transactions";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_TXN_ID_DISPLAY = "txn_id_display";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_AMOUNT_MINOR = "amount_minor";
        public static final String COLUMN_TIMESTAMP = "timestamp";
        public static final String COLUMN_METHOD = "method";
        public static final String COLUMN_REFERENCE_NO = "reference_no";
        public static final String COLUMN_NOTES = "notes";
        public static final String COLUMN_PARENT_TXN_ID = "parent_txn_id";
        public static final String COLUMN_IS_REVERSED = "is_reversed";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class PaymentAllocationEntry implements BaseColumns {
        public static final String TABLE_NAME = "payment_allocations";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_TRANSACTION_ID = "transaction_id";
        public static final String COLUMN_INVOICE_ID = "invoice_id";
        public static final String COLUMN_ALLOCATED_MINOR = "allocated_minor";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }

    public static class AuditLogEntry implements BaseColumns {
        public static final String TABLE_NAME = "audit_logs";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACCOUNT_ID = "account_id";
        public static final String COLUMN_STUDENT_ID = "student_id";
        public static final String COLUMN_ENTITY_TYPE = "entity_type";
        public static final String COLUMN_ENTITY_ID = "entity_id";
        public static final String COLUMN_ACTION = "action";
        public static final String COLUMN_DETAILS = "details";
        public static final String COLUMN_TIMESTAMP = "timestamp";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
    }
}
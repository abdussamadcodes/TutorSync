package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.FeeAgreementEntry;
import com.abdussamadcodes.tutorsync.model.FeeAgreement;

public class FeeAgreementRepository {

    public boolean saveAgreement(@NonNull FeeAgreement agreement) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            String accountId = agreement.getAccountId();
            String studentId = agreement.getStudentId();
            long now = System.currentTimeMillis();
            long modTime = agreement.getUpdatedAt() > 0 ? agreement.getUpdatedAt() : now;

            String existingId = null;
            long createdAt = modTime;

            try (Cursor c = db.query(FeeAgreementEntry.TABLE_NAME,
                    new String[]{FeeAgreementEntry.COLUMN_ID, FeeAgreementEntry.COLUMN_CREATED_AT},
                    FeeAgreementEntry.COLUMN_ACCOUNT_ID + " = ? AND (" +
                            FeeAgreementEntry.COLUMN_ID + " = ? OR " +
                            FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?)",
                    new String[]{accountId, agreement.getId(), studentId}, null, null, null)) {
                if (c.moveToFirst()) {
                    existingId = c.getString(0);
                    if (!c.isNull(1) && c.getLong(1) > 0) {
                        createdAt = c.getLong(1);
                    }
                }
            }

            String targetId = existingId != null ? existingId : TutorSyncContract.ensureValidUuid(agreement.getId());

            ContentValues values = new ContentValues();
            values.put(FeeAgreementEntry.COLUMN_ID, targetId);
            values.put(FeeAgreementEntry.COLUMN_ACCOUNT_ID, accountId);
            values.put(FeeAgreementEntry.COLUMN_STUDENT_ID, studentId);
            values.put(FeeAgreementEntry.COLUMN_FEE_TYPE, agreement.getFeeType());
            values.put(FeeAgreementEntry.COLUMN_AMOUNT_MINOR, agreement.getAmountMinorUnits());
            values.put(FeeAgreementEntry.COLUMN_CREATED_AT, createdAt);
            values.put(FeeAgreementEntry.COLUMN_UPDATED_AT, modTime);
            values.put(FeeAgreementEntry.COLUMN_LAST_MODIFIED, modTime);

            if (existingId != null) {
                return db.update(FeeAgreementEntry.TABLE_NAME, values,
                        FeeAgreementEntry.COLUMN_ACCOUNT_ID + " = ? AND " + FeeAgreementEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, existingId}) > 0;
            } else {
                return db.insert(FeeAgreementEntry.TABLE_NAME, null, values) != -1;
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @Nullable
    public FeeAgreement getAgreementForStudent(@NonNull String accountId, @NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor cursor = db.query(FeeAgreementEntry.TABLE_NAME, null,
                FeeAgreementEntry.COLUMN_ACCOUNT_ID + " = ? AND " + FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?",
                new String[]{accountId, studentId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return mapCursorToAgreement(cursor);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    @Nullable
    public FeeAgreement getAgreementForStudent(@NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor cursor = db.query(FeeAgreementEntry.TABLE_NAME, null,
                FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?", new String[]{studentId},
                null, null, FeeAgreementEntry.COLUMN_LAST_MODIFIED + " DESC", "1")) {
            if (cursor.moveToFirst()) {
                return mapCursorToAgreement(cursor);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    @NonNull
    private FeeAgreement mapCursorToAgreement(@NonNull Cursor cursor) {
        long lastMod = 0L;
        int lastModIdx = cursor.getColumnIndex(FeeAgreementEntry.COLUMN_LAST_MODIFIED);
        if (lastModIdx != -1 && !cursor.isNull(lastModIdx)) {
            lastMod = cursor.getLong(lastModIdx);
        }
        if (lastMod <= 0L) {
            int upIdx = cursor.getColumnIndex(FeeAgreementEntry.COLUMN_UPDATED_AT);
            if (upIdx != -1 && !cursor.isNull(upIdx)) {
                lastMod = cursor.getLong(upIdx);
            }
        }
        return new FeeAgreement(
                cursor.getString(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_ACCOUNT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_STUDENT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_FEE_TYPE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_AMOUNT_MINOR)),
                lastMod
        );
    }
}
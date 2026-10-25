package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.FeeAgreementEntry;
import com.abdussamadcodes.tutorsync.model.FeeAgreement;

public class FeeAgreementRepository {

    public boolean saveAgreement(@NonNull FeeAgreement agreement) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(FeeAgreementEntry.COLUMN_ID, agreement.getId());
            values.put(FeeAgreementEntry.COLUMN_ACCOUNT_ID, agreement.getAccountId());
            values.put(FeeAgreementEntry.COLUMN_STUDENT_ID, agreement.getStudentId());
            values.put(FeeAgreementEntry.COLUMN_FEE_TYPE, agreement.getFeeType());
            values.put(FeeAgreementEntry.COLUMN_AMOUNT_MINOR, agreement.getAmountMinorUnits());
            values.put(FeeAgreementEntry.COLUMN_UPDATED_AT, agreement.getUpdatedAt());

            return db.replace(FeeAgreementEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @Nullable
    public FeeAgreement getAgreementForStudent(@NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor cursor = db.query(FeeAgreementEntry.TABLE_NAME, null,
                FeeAgreementEntry.COLUMN_STUDENT_ID + " = ?", new String[]{studentId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return new FeeAgreement(
                        cursor.getString(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_ACCOUNT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_STUDENT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_FEE_TYPE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_AMOUNT_MINOR)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(FeeAgreementEntry.COLUMN_UPDATED_AT))
                );
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }
}
package com.abdussamadcodes.tutorsync.account;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.model.Account;

public class LocalAccountStore {

    public LocalAccountStore(@NonNull Context context) {
        // SharedPreferences no longer used for accounts
    }

    public boolean saveAccount(@NonNull Account account) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        boolean success = false;

        try {
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_ID, account.getId());
            values.put(AccountEntry.COLUMN_FULL_NAME, account.getFullName());
            values.put(AccountEntry.COLUMN_USERNAME, account.getUsername().toLowerCase());
            values.put(AccountEntry.COLUMN_PASSWORD_HASH, account.getPasswordHash());
            values.put(AccountEntry.COLUMN_SALT, account.getSalt());

            long currentTime = System.currentTimeMillis();
            values.put(AccountEntry.COLUMN_CREATED_AT, currentTime);
            values.put(AccountEntry.COLUMN_UPDATED_AT, currentTime);

            // insert returns -1 if an error occurred (e.g., username already exists due to UNIQUE constraint)
            long newRowId = db.insert(AccountEntry.TABLE_NAME, null, values);
            success = newRowId != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return success;
    }

    @Nullable
    public Account getAccountByUsername(@NonNull String username) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        Account account = null;

        String selection = AccountEntry.COLUMN_USERNAME + " = ?";
        String[] selectionArgs = { username.toLowerCase() };

        Cursor cursor = null;
        try {
            cursor = db.query(
                    AccountEntry.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_ID));
                String fullName = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_FULL_NAME));
                String savedUsername = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_USERNAME));
                String passwordHash = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_PASSWORD_HASH));
                String salt = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_SALT));

                account = new Account(id, fullName, savedUsername, passwordHash, salt);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            DatabaseManager.getInstance().closeDatabase();
        }

        return account;
    }
}
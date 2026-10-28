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
        // SQLite backed
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
            values.put(AccountEntry.COLUMN_PHONE, account.getPhone());
            values.put(AccountEntry.COLUMN_EMAIL, account.getEmail());
            values.put(AccountEntry.COLUMN_ADDRESS, account.getAddress());
            values.put(AccountEntry.COLUMN_DOB, account.getDob());
            values.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, account.getProfilePicPath());

            long currentTime = System.currentTimeMillis();
            values.put(AccountEntry.COLUMN_CREATED_AT, currentTime);
            values.put(AccountEntry.COLUMN_UPDATED_AT, currentTime);

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
            cursor = db.query(AccountEntry.TABLE_NAME, null, selection, selectionArgs, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                account = mapCursorToAccount(cursor);
            }
        } finally {
            if (cursor != null) cursor.close();
            DatabaseManager.getInstance().closeDatabase();
        }
        return account;
    }

    @Nullable
    public Account getAccountById(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        Account account = null;

        String selection = AccountEntry.COLUMN_ID + " = ?";
        String[] selectionArgs = { accountId };

        Cursor cursor = null;
        try {
            cursor = db.query(AccountEntry.TABLE_NAME, null, selection, selectionArgs, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                account = mapCursorToAccount(cursor);
            }
        } finally {
            if (cursor != null) cursor.close();
            DatabaseManager.getInstance().closeDatabase();
        }
        return account;
    }

    @NonNull
    private Account mapCursorToAccount(@NonNull Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_ID));
        String fullName = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_FULL_NAME));
        String savedUsername = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_USERNAME));
        String passwordHash = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_PASSWORD_HASH));
        String salt = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_SALT));
        String phone = getNullableString(cursor, AccountEntry.COLUMN_PHONE);
        String email = getNullableString(cursor, AccountEntry.COLUMN_EMAIL);
        String address = getNullableString(cursor, AccountEntry.COLUMN_ADDRESS);
        String dob = getNullableString(cursor, AccountEntry.COLUMN_DOB);
        String profilePicPath = getNullableString(cursor, AccountEntry.COLUMN_PROFILE_PIC_PATH);

        return new Account(id, fullName, savedUsername, passwordHash, salt,
                phone, email, address, dob, profilePicPath);
    }

    @Nullable
    private String getNullableString(@NonNull Cursor cursor, @NonNull String columnName) {
        int index = cursor.getColumnIndex(columnName);
        if (index != -1 && !cursor.isNull(index)) {
            return cursor.getString(index);
        }
        return null;
    }

    public boolean updatePassword(@NonNull String accountId, @NonNull String newPasswordHash, @NonNull String newSalt) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        boolean success = false;
        try {
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_PASSWORD_HASH, newPasswordHash);
            values.put(AccountEntry.COLUMN_SALT, newSalt);
            values.put(AccountEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            int rowsAffected = db.update(AccountEntry.TABLE_NAME, values, AccountEntry.COLUMN_ID + " = ?", new String[]{ accountId });
            success = rowsAffected > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return success;
    }

    public boolean updateProfilePicPath(@NonNull String accountId, @Nullable String path) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        boolean success = false;
        try {
            ContentValues values = new ContentValues();
            if (path == null) {
                values.putNull(AccountEntry.COLUMN_PROFILE_PIC_PATH);
            } else {
                values.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, path);
            }
            values.put(AccountEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            int rowsAffected = db.update(AccountEntry.TABLE_NAME, values, AccountEntry.COLUMN_ID + " = ?", new String[]{ accountId });
            success = rowsAffected > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return success;
    }

    public boolean updateProfile(@NonNull String accountId, @NonNull String fullName,
                                 @Nullable String phone, @Nullable String email,
                                 @Nullable String address, @Nullable String dob) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        boolean success = false;
        try {
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_FULL_NAME, fullName);
            values.put(AccountEntry.COLUMN_PHONE, phone);
            values.put(AccountEntry.COLUMN_EMAIL, email);
            values.put(AccountEntry.COLUMN_ADDRESS, address);
            values.put(AccountEntry.COLUMN_DOB, dob);
            values.put(AccountEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            int rowsAffected = db.update(AccountEntry.TABLE_NAME, values, AccountEntry.COLUMN_ID + " = ?", new String[]{ accountId });
            success = rowsAffected > 0;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return success;
    }

    public boolean updateProfile(@NonNull String accountId, @NonNull String fullName,
                                 @Nullable String phone, @Nullable String email,
                                 @Nullable String address, @Nullable String dob,
                                 @Nullable String unusedUsername) {
        return updateProfile(accountId, fullName, phone, email, address, dob);
    }
}
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
        String selection = AccountEntry.COLUMN_USERNAME + " = ?";
        String[] selectionArgs = { username.toLowerCase() };

        try (Cursor cursor = db.query(AccountEntry.TABLE_NAME, null, selection, selectionArgs, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return mapCursorToAccount(cursor);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    @Nullable
    public Account getAccountById(@NonNull String accountId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        String selection = AccountEntry.COLUMN_ID + " = ?";
        String[] selectionArgs = { accountId };

        try (Cursor cursor = db.query(AccountEntry.TABLE_NAME, null, selection, selectionArgs, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return mapCursorToAccount(cursor);
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    public boolean updateProfile(@NonNull String accountId, @NonNull String fullName,
                                 @Nullable String phone, @Nullable String email,
                                 @Nullable String address, @Nullable String dob,
                                 @Nullable String profilePicPath) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_FULL_NAME, fullName);
            values.put(AccountEntry.COLUMN_PHONE, phone);
            values.put(AccountEntry.COLUMN_EMAIL, email);
            values.put(AccountEntry.COLUMN_ADDRESS, address);
            values.put(AccountEntry.COLUMN_DOB, dob);
            values.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, profilePicPath);
            values.put(AccountEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            return db.update(AccountEntry.TABLE_NAME, values,
                    AccountEntry.COLUMN_ID + " = ?", new String[]{accountId}) > 0;
        } catch (Exception e) {
            return false;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    public boolean updateProfilePicPath(@NonNull String accountId, @Nullable String profilePicPath) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, profilePicPath);
            values.put(AccountEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            return db.update(AccountEntry.TABLE_NAME, values,
                    AccountEntry.COLUMN_ID + " = ?", new String[]{accountId}) > 0;
        } catch (Exception e) {
            return false;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private Account mapCursorToAccount(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_ID));
        String fullName = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_FULL_NAME));
        String savedUsername = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_USERNAME));
        String passwordHash = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_PASSWORD_HASH));
        String salt = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_SALT));

        int phoneIdx = cursor.getColumnIndex(AccountEntry.COLUMN_PHONE);
        int emailIdx = cursor.getColumnIndex(AccountEntry.COLUMN_EMAIL);
        int addressIdx = cursor.getColumnIndex(AccountEntry.COLUMN_ADDRESS);
        int dobIdx = cursor.getColumnIndex(AccountEntry.COLUMN_DOB);
        int picIdx = cursor.getColumnIndex(AccountEntry.COLUMN_PROFILE_PIC_PATH);

        String phone = phoneIdx >= 0 ? cursor.getString(phoneIdx) : null;
        String email = emailIdx >= 0 ? cursor.getString(emailIdx) : null;
        String address = addressIdx >= 0 ? cursor.getString(addressIdx) : null;
        String dob = dobIdx >= 0 ? cursor.getString(dobIdx) : null;
        String profilePicPath = picIdx >= 0 ? cursor.getString(picIdx) : null;

        return new Account(id, fullName, savedUsername, passwordHash, salt,
                phone, email, address, dob, profilePicPath);
    }
}
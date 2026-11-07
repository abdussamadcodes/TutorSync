package com.abdussamadcodes.tutorsync.account;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.model.Account;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class LocalAccountStore {

    public static final String GOOGLE_AUTH_SENTINEL = "GOOGLE_AUTH_MANAGED";

    public LocalAccountStore(@NonNull Context context) {
        // SQLite backed
    }

    public static boolean isGoogleManagedAccount(@Nullable Account account) {
        return account != null && GOOGLE_AUTH_SENTINEL.equals(account.getPasswordHash());
    }

    public boolean saveAccount(@NonNull Account account) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        boolean success = false;

        try {
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_ID, TutorSyncContract.ensureValidUuid(account.getId()));
            values.put(AccountEntry.COLUMN_FULL_NAME, account.getFullName());
            values.put(AccountEntry.COLUMN_USERNAME, account.getUsername().toLowerCase(Locale.ROOT));
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
            values.put(AccountEntry.COLUMN_LAST_MODIFIED, currentTime);

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
        String[] selectionArgs = { username.trim().toLowerCase(Locale.ROOT) };

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
    public Account getAccountByEmail(@NonNull String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            return null;
        }

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        Account account = null;

        String selection = "LOWER(" + AccountEntry.COLUMN_EMAIL + ") = ? OR " + AccountEntry.COLUMN_USERNAME + " = ?";
        String[] selectionArgs = { normalizedEmail, normalizedEmail };

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
    public Account findAccountByUsernameOrEmail(@NonNull String identifier) {
        String clean = identifier.trim();
        if (clean.isEmpty()) {
            return null;
        }
        Account byUsername = getAccountByUsername(clean);
        if (byUsername != null) {
            return byUsername;
        }
        return getAccountByEmail(clean);
    }

    @Nullable
    public Account getOrCreateGoogleAccount(@NonNull String firebaseUid,
                                            @NonNull String email,
                                            @Nullable String displayName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            return null;
        }

        // 1. Check if an account with this email already exists locally
        Account existing = getAccountByEmail(normalizedEmail);
        if (existing != null) {
            if (existing.getEmail() == null || existing.getEmail().trim().isEmpty()) {
                updateProfile(
                        existing.getId(),
                        existing.getFullName(),
                        existing.getPhone(),
                        normalizedEmail,
                        existing.getAddress(),
                        existing.getDob()
                );
                return getAccountById(existing.getId());
            }
            return existing;
        }

        // 2. Create a new local account scoped to a deterministic UUID
        String deterministicId = UUID.nameUUIDFromBytes(
                ("GOOGLE_ACCT:" + normalizedEmail).getBytes(StandardCharsets.UTF_8)
        ).toString();

        Account byId = getAccountById(deterministicId);
        if (byId != null) {
            return byId;
        }

        String resolvedName = (displayName != null && !displayName.trim().isEmpty())
                ? displayName.trim()
                : deriveNameFromEmail(normalizedEmail);

        String uniqueUsername = resolveUniqueUsernameForGoogle(normalizedEmail, firebaseUid);

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long currentTime = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_ID, deterministicId);
            values.put(AccountEntry.COLUMN_FULL_NAME, resolvedName);
            values.put(AccountEntry.COLUMN_USERNAME, uniqueUsername);
            values.put(AccountEntry.COLUMN_PASSWORD_HASH, GOOGLE_AUTH_SENTINEL);
            values.put(AccountEntry.COLUMN_SALT, GOOGLE_AUTH_SENTINEL);
            values.put(AccountEntry.COLUMN_EMAIL, normalizedEmail);
            values.put(AccountEntry.COLUMN_SYNC_STATUS, "GOOGLE_LINKED");
            values.put(AccountEntry.COLUMN_CREATED_AT, currentTime);
            values.put(AccountEntry.COLUMN_UPDATED_AT, currentTime);
            values.put(AccountEntry.COLUMN_LAST_MODIFIED, currentTime);

            long rowId = db.insert(AccountEntry.TABLE_NAME, null, values);
            if (rowId != -1) {
                return new Account(
                        deterministicId,
                        resolvedName,
                        uniqueUsername,
                        GOOGLE_AUTH_SENTINEL,
                        GOOGLE_AUTH_SENTINEL,
                        null,
                        normalizedEmail,
                        null,
                        null,
                        null
                );
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return getAccountByEmail(normalizedEmail);
    }

    @NonNull
    private String resolveUniqueUsernameForGoogle(@NonNull String normalizedEmail, @NonNull String firebaseUid) {
        if (getAccountByUsername(normalizedEmail) == null) {
            return normalizedEmail;
        }
        String prefix = normalizedEmail.contains("@")
                ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                : normalizedEmail;
        String cleanPrefix = prefix.replaceAll("[^a-z0-9]", "");
        if (cleanPrefix.isEmpty()) {
            cleanPrefix = "tutor";
        }
        String suffix = firebaseUid.length() >= 6
                ? firebaseUid.substring(0, 6).toLowerCase(Locale.ROOT)
                : String.valueOf(System.currentTimeMillis() % 100000);
        String candidate = cleanPrefix + "_" + suffix;
        int attempt = 1;
        while (getAccountByUsername(candidate) != null) {
            candidate = cleanPrefix + "_" + suffix + attempt;
            attempt++;
        }
        return candidate;
    }

    @NonNull
    private static String deriveNameFromEmail(@NonNull String email) {
        int atIndex = email.indexOf('@');
        String localPart = atIndex > 0 ? email.substring(0, atIndex) : email;
        String cleaned = localPart.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        return cleaned.isEmpty() ? "Tutor" : cleaned;
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
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_PASSWORD_HASH, newPasswordHash);
            values.put(AccountEntry.COLUMN_SALT, newSalt);
            values.put(AccountEntry.COLUMN_UPDATED_AT, now);
            values.put(AccountEntry.COLUMN_LAST_MODIFIED, now);

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
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            if (path == null) {
                values.putNull(AccountEntry.COLUMN_PROFILE_PIC_PATH);
            } else {
                values.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, path);
            }
            values.put(AccountEntry.COLUMN_UPDATED_AT, now);
            values.put(AccountEntry.COLUMN_LAST_MODIFIED, now);

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
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_FULL_NAME, fullName);
            values.put(AccountEntry.COLUMN_PHONE, phone);
            values.put(AccountEntry.COLUMN_EMAIL, email);
            values.put(AccountEntry.COLUMN_ADDRESS, address);
            values.put(AccountEntry.COLUMN_DOB, dob);
            values.put(AccountEntry.COLUMN_UPDATED_AT, now);
            values.put(AccountEntry.COLUMN_LAST_MODIFIED, now);

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
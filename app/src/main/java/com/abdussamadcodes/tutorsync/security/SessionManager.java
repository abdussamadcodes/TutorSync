package com.abdussamadcodes.tutorsync.security;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SessionManager {
    private static final String PREF_NAME = "tutorsync_session";
    private static final String KEY_ACTIVE_ACCOUNT_ID = "active_account_id";
    public static final String PIN_SALT_BASE64 = "VHV0b3JTeW5jUGluU2FsdA==";

    private final SharedPreferences prefs;

    public SessionManager(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void login(@NonNull String accountId) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, accountId).apply();
    }

    public void logout() {
        prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply();
    }

    public boolean hasActiveSession() {
        return prefs.contains(KEY_ACTIVE_ACCOUNT_ID);
    }

    @NonNull
    public String getActiveAccountId() {
        return prefs.getString(KEY_ACTIVE_ACCOUNT_ID, "");
    }

    public void savePinSetup(@Nullable String hashedPin, boolean useBiometric) {
        String accountId = getActiveAccountId();
        if (accountId.isEmpty()) return;

        SharedPreferences.Editor editor = prefs.edit();
        if (hashedPin == null || hashedPin.isEmpty()) {
            editor.remove("pin_" + accountId);
            editor.remove("bio_" + accountId);
        } else {
            editor.putString("pin_" + accountId, hashedPin);
            editor.putBoolean("bio_" + accountId, useBiometric);
        }
        editor.apply();
    }

    public void setBiometricEnabled(boolean useBiometric) {
        String accountId = getActiveAccountId();
        if (accountId.isEmpty() || !hasPinSetup()) return;
        prefs.edit().putBoolean("bio_" + accountId, useBiometric).apply();
    }

    public boolean hasPinSetup() {
        String accountId = getActiveAccountId();
        if (accountId.isEmpty()) return false;
        String hash = prefs.getString("pin_" + accountId, "");
        return hash != null && !hash.isEmpty();
    }

    @NonNull
    public String getSavedPinHash() {
        String accountId = getActiveAccountId();
        if (accountId.isEmpty()) return "";
        return prefs.getString("pin_" + accountId, "");
    }

    public boolean isBiometricEnabled() {
        String accountId = getActiveAccountId();
        if (accountId.isEmpty()) return false;
        return prefs.getBoolean("bio_" + accountId, false);
    }

    public boolean verifyPin(@NonNull String rawPin) {
        String savedHash = getSavedPinHash();
        if (savedHash.isEmpty()) return false;
        try {
            String candidateHash = PasswordHasher.hashPassword(rawPin, PIN_SALT_BASE64);
            return savedHash.equals(candidateHash);
        } catch (Exception e) {
            return false;
        }
    }
}
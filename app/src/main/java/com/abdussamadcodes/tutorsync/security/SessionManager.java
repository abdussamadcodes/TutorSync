package com.abdussamadcodes.tutorsync.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SessionManager {
    private static final String PREF_NAME = "tutorsync_session";
    private static final String KEY_ACTIVE_ACCOUNT_ID = "active_account_id";
    private static final String KEY_SESSION_UNLOCKED = "session_unlocked";
    private static final String KEY_UNLOCKED_BOOT_COUNT = "unlocked_boot_count";
    private static final String KEY_LAST_APP_UNLOCK_REALTIME = "last_app_unlock_realtime";
    public static final String PIN_SALT_BASE64 = "VHV0b3JTeW5jUGluU2FsdA==";

    private final Context appContext;
    private final SharedPreferences prefs;

    public SessionManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = this.appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void login(@NonNull String accountId) {
        prefs.edit()
                .putString(KEY_ACTIVE_ACCOUNT_ID, accountId)
                .putBoolean(KEY_SESSION_UNLOCKED, true)
                .putInt(KEY_UNLOCKED_BOOT_COUNT, getCurrentBootCount())
                .putLong(KEY_LAST_APP_UNLOCK_REALTIME, SystemClock.elapsedRealtime())
                .commit();
    }

    public void logout() {
        prefs.edit()
                .remove(KEY_ACTIVE_ACCOUNT_ID)
                .putBoolean(KEY_SESSION_UNLOCKED, false)
                .remove(KEY_UNLOCKED_BOOT_COUNT)
                .remove(KEY_LAST_APP_UNLOCK_REALTIME)
                .commit();
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
        editor.putBoolean(KEY_SESSION_UNLOCKED, true);
        editor.putInt(KEY_UNLOCKED_BOOT_COUNT, getCurrentBootCount());
        editor.putLong(KEY_LAST_APP_UNLOCK_REALTIME, SystemClock.elapsedRealtime());
        editor.commit();
    }

    public void setBiometricEnabled(boolean useBiometric) {
        String accountId = getActiveAccountId();
        if (accountId.isEmpty() || !hasPinSetup()) return;
        prefs.edit()
                .putBoolean("bio_" + accountId, useBiometric)
                .putBoolean(KEY_SESSION_UNLOCKED, true)
                .putInt(KEY_UNLOCKED_BOOT_COUNT, getCurrentBootCount())
                .putLong(KEY_LAST_APP_UNLOCK_REALTIME, SystemClock.elapsedRealtime())
                .commit();
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

    public void setSessionUnlockedState(boolean unlocked) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_SESSION_UNLOCKED, unlocked);
        if (unlocked) {
            editor.putInt(KEY_UNLOCKED_BOOT_COUNT, getCurrentBootCount());
            editor.putLong(KEY_LAST_APP_UNLOCK_REALTIME, SystemClock.elapsedRealtime());
        } else {
            editor.remove(KEY_LAST_APP_UNLOCK_REALTIME);
        }
        editor.commit();
    }

    public boolean isSessionUnlockedForCurrentBoot() {
        if (!prefs.getBoolean(KEY_SESSION_UNLOCKED, false)) {
            return false;
        }
        int savedBootCount = prefs.getInt(KEY_UNLOCKED_BOOT_COUNT, -1);
        int currentBootCount = getCurrentBootCount();
        if (savedBootCount != currentBootCount) {
            return false;
        }
        long lastUnlockRealtime = getLastAppUnlockRealtime();
        long nowRealtime = SystemClock.elapsedRealtime();
        return lastUnlockRealtime > 0L && nowRealtime >= lastUnlockRealtime;
    }

    public long getLastAppUnlockRealtime() {
        return prefs.getLong(KEY_LAST_APP_UNLOCK_REALTIME, 0L);
    }

    private int getCurrentBootCount() {
        try {
            return Settings.Global.getInt(appContext.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
        } catch (Exception e) {
            return 0;
        }
    }
}
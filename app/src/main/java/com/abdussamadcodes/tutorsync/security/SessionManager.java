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
    private static final String KEY_PIN_FAILS = "pin_fails_";
    private static final String KEY_PIN_LOCK_END_RT = "pin_lock_end_rt_";
    private static final String KEY_PIN_LOCK_BOOT = "pin_lock_boot_";
    private static final String KEY_PIN_LOCK_END_WALL = "pin_lock_end_wall_";

    /** Result of one PIN attempt on the lock screen. */
    public enum PinStatus { OK, WRONG, LOCKED_OUT, NO_PIN }

    public static final class PinAttempt {
        @NonNull public final PinStatus status;
        /** Wrong attempts still allowed before the first temporary lock (only while WRONG). */
        public final int attemptsLeft;
        /** Time the lock screen refuses further attempts (LOCKED_OUT, or WRONG that triggered a lock). */
        public final long lockoutRemainingMs;

        PinAttempt(@NonNull PinStatus status, int attemptsLeft, long lockoutRemainingMs) {
            this.status = status;
            this.attemptsLeft = attemptsLeft;
            this.lockoutRemainingMs = lockoutRemainingMs;
        }
    }

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
        // a new / removed PIN always starts with a clean attempt counter
        editor.remove(KEY_PIN_FAILS + accountId)
                .remove(KEY_PIN_LOCK_END_RT + accountId)
                .remove(KEY_PIN_LOCK_BOOT + accountId)
                .remove(KEY_PIN_LOCK_END_WALL + accountId);
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

    /** Saves a new PIN (salted, slow hash). Throws if the hash cannot be computed. */
    public void setPin(@NonNull String rawPin, boolean useBiometric) throws java.security.GeneralSecurityException {
        savePinSetup(PinHasher.hash(rawPin), useBiometric);
    }

    /** Length of the saved PIN (4-6), or 0 when it is not known (PIN saved by an older app version). */
    public int getSavedPinLength() {
        return PinHasher.storedLength(getSavedPinHash());
    }

    /** Milliseconds until the lock screen accepts PIN attempts again (0 = not locked out). */
    public long getPinLockoutRemainingMs() {
        String accountId = getActiveAccountId();
        if (accountId.isEmpty()) return 0L;
        long endRealtime = prefs.getLong(KEY_PIN_LOCK_END_RT + accountId, 0L);
        long endWall = prefs.getLong(KEY_PIN_LOCK_END_WALL + accountId, 0L);
        if (endRealtime == 0L && endWall == 0L) return 0L;

        long remaining;
        if (prefs.getInt(KEY_PIN_LOCK_BOOT + accountId, -1) == getCurrentBootCount()) {
            remaining = endRealtime - SystemClock.elapsedRealtime();
        } else {
            // after a reboot elapsedRealtime starts again from 0, so use the wall clock
            remaining = endWall - System.currentTimeMillis();
        }
        // never longer than the maximum lockout, even if the clock was changed
        return Math.max(0L, Math.min(remaining, PinLockoutPolicy.MAX_LOCKOUT_MS));
    }

    /**
     * One PIN attempt with attempt limiting. Does a slow hash, so call it OFF the main thread.
     * A PIN saved by an older app version is verified and silently re-saved in the new format.
     */
    @NonNull
    public PinAttempt attemptPin(@NonNull String rawPin) {
        String accountId = getActiveAccountId();
        if (accountId.isEmpty() || !hasPinSetup()) {
            return new PinAttempt(PinStatus.NO_PIN, 0, 0L);
        }
        long locked = getPinLockoutRemainingMs();
        if (locked > 0L) {
            return new PinAttempt(PinStatus.LOCKED_OUT, 0, locked);
        }

        String stored = getSavedPinHash();
        if (PinHasher.verify(rawPin, stored)) {
            SharedPreferences.Editor editor = prefs.edit()
                    .remove(KEY_PIN_FAILS + accountId)
                    .remove(KEY_PIN_LOCK_END_RT + accountId)
                    .remove(KEY_PIN_LOCK_BOOT + accountId)
                    .remove(KEY_PIN_LOCK_END_WALL + accountId);
            if (PinHasher.needsUpgrade(stored)) {
                try {
                    editor.putString("pin_" + accountId, PinHasher.hash(rawPin));
                } catch (Exception ignored) {
                    // keep the old hash; it still works
                }
            }
            editor.commit();
            return new PinAttempt(PinStatus.OK, 0, 0L);
        }

        int fails = prefs.getInt(KEY_PIN_FAILS + accountId, 0) + 1;
        long lockMs = PinLockoutPolicy.lockoutDurationMs(fails);
        SharedPreferences.Editor editor = prefs.edit().putInt(KEY_PIN_FAILS + accountId, fails);
        if (lockMs > 0L) {
            editor.putLong(KEY_PIN_LOCK_END_RT + accountId, SystemClock.elapsedRealtime() + lockMs)
                    .putInt(KEY_PIN_LOCK_BOOT + accountId, getCurrentBootCount())
                    .putLong(KEY_PIN_LOCK_END_WALL + accountId, System.currentTimeMillis() + lockMs);
        }
        editor.commit();
        return new PinAttempt(PinStatus.WRONG, PinLockoutPolicy.attemptsBeforeLockout(fails), lockMs);
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
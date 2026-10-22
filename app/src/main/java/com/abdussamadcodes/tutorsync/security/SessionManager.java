package com.abdussamadcodes.tutorsync.security;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

public class SessionManager {
    private static final String PREF_NAME = "tutorsync_session";
    private static final String KEY_ACTIVE_ACCOUNT_ID = "active_account_id";
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
}
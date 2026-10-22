package com.abdussamadcodes.tutorsync.account;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.model.Account;
import org.json.JSONObject;

public class LocalAccountStore {
    private static final String PREF_NAME = "tutorsync_accounts_db";
    private final SharedPreferences prefs;

    public LocalAccountStore(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean saveAccount(@NonNull Account account) {
        String key = account.getUsername().toLowerCase();
        if (prefs.contains(key)) return false;
        try {
            JSONObject json = new JSONObject();
            json.put("id", account.getId());
            json.put("username", account.getUsername());
            json.put("passwordHash", account.getPasswordHash());
            json.put("salt", account.getSalt());
            prefs.edit().putString(key, json.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public Account getAccountByUsername(@NonNull String username) {
        String jsonString = prefs.getString(username.toLowerCase(), null);
        if (jsonString == null) return null;
        try {
            JSONObject json = new JSONObject(jsonString);
            return new Account(
                    json.getString("id"),
                    json.getString("username"),
                    json.getString("passwordHash"),
                    json.getString("salt")
            );
        } catch (Exception e) {
            return null;
        }
    }
}
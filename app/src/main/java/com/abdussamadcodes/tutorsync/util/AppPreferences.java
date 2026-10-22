package com.abdussamadcodes.tutorsync.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

public final class AppPreferences {
    private static final String PREF_FILE_NAME = "tutorsync_bootstrap_prefs";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_LANGUAGE_CODE = "language_code";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_LAST_CRASH_SUMMARY = "last_crash_summary";

    public static final String LANG_ENGLISH = "en";
    public static final String LANG_BANGLA = "bn";
    public static final String THEME_SYSTEM = "SYSTEM";
    public static final String THEME_LIGHT = "LIGHT";
    public static final String THEME_DARK = "DARK";

    private final SharedPreferences sharedPreferences;

    public AppPreferences(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOnboardingCompleted() {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    @NonNull
    public String getLanguageCode() {
        String code = sharedPreferences.getString(KEY_LANGUAGE_CODE, LANG_ENGLISH);
        return LocaleThemeHelper.normalizeLanguageCode(code);
    }

    public void setLanguageCode(@NonNull String languageCode) {
        String normalized = LocaleThemeHelper.normalizeLanguageCode(languageCode);
        sharedPreferences.edit().putString(KEY_LANGUAGE_CODE, normalized).apply();
    }

    @NonNull
    public String getThemeMode() {
        String mode = sharedPreferences.getString(KEY_THEME_MODE, THEME_SYSTEM);
        return LocaleThemeHelper.normalizeThemeMode(mode);
    }

    public void setThemeMode(@NonNull String themeMode) {
        String normalized = LocaleThemeHelper.normalizeThemeMode(themeMode);
        sharedPreferences.edit().putString(KEY_THEME_MODE, normalized).apply();
    }

    public void recordLastCrashSummary(@NonNull String summary) {
        sharedPreferences.edit().putString(KEY_LAST_CRASH_SUMMARY, summary).commit();
    }
}
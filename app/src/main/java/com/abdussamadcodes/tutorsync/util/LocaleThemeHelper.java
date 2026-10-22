package com.abdussamadcodes.tutorsync.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.Locale;

public final class LocaleThemeHelper {

    private LocaleThemeHelper() {
    }

    @NonNull
    public static String normalizeLanguageCode(@Nullable String rawCode) {
        if (rawCode == null) {
            return AppPreferences.LANG_ENGLISH;
        }
        String cleaned = rawCode.trim().toLowerCase(Locale.US);
        return cleaned.startsWith("bn") ? AppPreferences.LANG_BANGLA : AppPreferences.LANG_ENGLISH;
    }

    @NonNull
    public static String normalizeThemeMode(@Nullable String rawTheme) {
        if (rawTheme == null) {
            return AppPreferences.THEME_SYSTEM;
        }
        String cleaned = rawTheme.trim().toUpperCase(Locale.US);
        if (AppPreferences.THEME_LIGHT.equals(cleaned)) {
            return AppPreferences.THEME_LIGHT;
        }
        if (AppPreferences.THEME_DARK.equals(cleaned)) {
            return AppPreferences.THEME_DARK;
        }
        return AppPreferences.THEME_SYSTEM;
    }

    public static int toNightModeFlag(@Nullable String themeMode) {
        String normalized = normalizeThemeMode(themeMode);
        switch (normalized) {
            case AppPreferences.THEME_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case AppPreferences.THEME_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    public static void applySavedLocaleAndTheme(@NonNull Context context) {
        AppPreferences prefs = new AppPreferences(context);
        applyThemeMode(prefs.getThemeMode());
        applyLanguage(prefs.getLanguageCode());
    }

    public static void applyThemeMode(@NonNull String themeMode) {
        int targetMode = toNightModeFlag(themeMode);
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }

    public static void applyLanguage(@NonNull String languageCode) {
        String normalized = normalizeLanguageCode(languageCode);
        LocaleListCompat targetLocales = LocaleListCompat.forLanguageTags(normalized);
        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        if (!targetLocales.equals(currentLocales)) {
            AppCompatDelegate.setApplicationLocales(targetLocales);
        }
    }
}
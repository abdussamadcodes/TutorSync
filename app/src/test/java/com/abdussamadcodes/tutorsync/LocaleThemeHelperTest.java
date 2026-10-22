package com.abdussamadcodes.tutorsync;

import static org.junit.Assert.assertEquals;

import androidx.appcompat.app.AppCompatDelegate;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;
import org.junit.Test;

public class LocaleThemeHelperTest {

    @Test
    public void normalizeLanguageCode_handlesEnglishAndBanglaVariants() {
        assertEquals(AppPreferences.LANG_ENGLISH, LocaleThemeHelper.normalizeLanguageCode(null));
        assertEquals(AppPreferences.LANG_ENGLISH, LocaleThemeHelper.normalizeLanguageCode(""));
        assertEquals(AppPreferences.LANG_ENGLISH, LocaleThemeHelper.normalizeLanguageCode("en-US"));
        assertEquals(AppPreferences.LANG_BANGLA, LocaleThemeHelper.normalizeLanguageCode("bn"));
        assertEquals(AppPreferences.LANG_BANGLA, LocaleThemeHelper.normalizeLanguageCode("bn-BD"));
    }

    @Test
    public void normalizeThemeMode_mapsToExpectedNightModeFlags() {
        assertEquals(AppPreferences.THEME_SYSTEM, LocaleThemeHelper.normalizeThemeMode(null));
        assertEquals(AppPreferences.THEME_LIGHT, LocaleThemeHelper.normalizeThemeMode("light"));
        assertEquals(AppPreferences.THEME_DARK, LocaleThemeHelper.normalizeThemeMode("dark"));

        assertEquals(
                AppCompatDelegate.MODE_NIGHT_NO,
                LocaleThemeHelper.toNightModeFlag(AppPreferences.THEME_LIGHT)
        );
        assertEquals(
                AppCompatDelegate.MODE_NIGHT_YES,
                LocaleThemeHelper.toNightModeFlag(AppPreferences.THEME_DARK)
        );
        assertEquals(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                LocaleThemeHelper.toNightModeFlag(AppPreferences.THEME_SYSTEM)
        );
    }
}
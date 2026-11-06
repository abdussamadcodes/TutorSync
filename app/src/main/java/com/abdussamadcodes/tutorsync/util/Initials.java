package com.abdussamadcodes.tutorsync.util;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Pure helpers for initials avatars (no Android dependency, unit-testable).
 * Works for Latin and Bangla names: "Rahim Uddin" gives "RU", "রহিম উদ্দিন" gives "রউ".
 */
public final class Initials {

    private Initials() {
    }

    /** First letter of the first and last word (one letter if the name is one word). Empty if no letters. */
    @NonNull
    public static String of(String name) {
        if (name == null) return "";
        String[] parts = name.trim().split("\\s+");
        String first = null;
        String last = null;
        for (String part : parts) {
            String letter = firstLetter(part);
            if (letter == null) continue;
            if (first == null) first = letter;
            last = letter;
        }
        if (first == null) return "";
        String result = countLetters(parts) > 1 ? first + last : first;
        return result.toUpperCase(Locale.ROOT);
    }

    /** Stable index in [0, size) for a name, so a person always gets the same avatar colour. */
    public static int paletteIndex(String name, int size) {
        if (size <= 0) return 0;
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return Math.floorMod(key.hashCode(), size);
    }

    private static int countLetters(String[] parts) {
        int n = 0;
        for (String p : parts) if (firstLetter(p) != null) n++;
        return n;
    }

    private static String firstLetter(String word) {
        if (word == null || word.isEmpty()) return null;
        int cp = word.codePointAt(0);
        if (!Character.isLetterOrDigit(cp)) return null;
        return new String(Character.toChars(cp));
    }
}
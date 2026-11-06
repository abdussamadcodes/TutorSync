package com.abdussamadcodes.tutorsync.util;

import androidx.annotation.NonNull;

/**
 * Pure number and money text helpers (no Android dependency, so they are unit-testable).
 * Use {@link AppFormat} in UI code; it picks the right language for you.
 *
 * English: 1,234,567   Bangla: ১২,৩৪,৫৬৭ (lakh grouping + Bengali digits).
 */
public final class NumberText {

    /** Taka sign. */
    public static final String TAKA = "\u09F3";
    private static final char BN_ZERO = '\u09E6';

    private NumberText() {
    }

    /** Replaces ASCII digits 0-9 with Bengali digits when {@code bangla} is true. */
    @NonNull
    public static String localizeDigits(@NonNull String text, boolean bangla) {
        if (!bangla) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c >= '0' && c <= '9' ? (char) (BN_ZERO + (c - '0')) : c);
        }
        return sb.toString();
    }

    /** Groups digits with commas: 3-3-3 for English, 2-2-3 (lakh) for Bangla. ASCII digits are used. */
    @NonNull
    public static String group(long value, boolean lakh) {
        if (value < 0) return "-" + group(-value, lakh);
        String s = Long.toString(value);
        int len = s.length();
        if (len <= 3) return s;
        StringBuilder sb = new StringBuilder();
        String tail = s.substring(len - 3);
        String head = s.substring(0, len - 3);
        int step = lakh ? 2 : 3;
        int first = head.length() % step;
        if (first > 0) sb.append(head, 0, first);
        for (int i = first; i < head.length(); i += step) {
            if (sb.length() > 0) sb.append(',');
            sb.append(head, i, i + step);
        }
        return sb.append(',').append(tail).toString();
    }

    /** Grouped whole number in the requested language. */
    @NonNull
    public static String number(long value, boolean bangla) {
        return localizeDigits(group(value, bangla), bangla);
    }

    /**
     * Money from minor units (paisa): "৳8,000", "৳1,250.50", "-৳300".
     * Whole amounts never show decimals; others always show two.
     */
    @NonNull
    public static String money(int minor, boolean bangla) {
        long abs = Math.abs((long) minor);
        long whole = abs / 100;
        int frac = (int) (abs % 100);
        StringBuilder sb = new StringBuilder();
        if (minor < 0) sb.append('-');
        sb.append(TAKA).append(group(whole, bangla));
        if (frac != 0) {
            sb.append('.').append(frac < 10 ? "0" : "").append(frac);
        }
        return localizeDigits(sb.toString(), bangla);
    }

    @NonNull
    public static String percent(int value, boolean bangla) {
        return localizeDigits(value + "%", bangla);
    }

    /** Index into a 7-item time-of-day names array (see fmt_time_periods). */
    public static int periodIndex(int hourOfDay) {
        if (hourOfDay < 4) return 0;
        if (hourOfDay < 6) return 1;
        if (hourOfDay < 12) return 2;
        if (hourOfDay < 16) return 3;
        if (hourOfDay < 18) return 4;
        if (hourOfDay < 20) return 5;
        return 6;
    }
}
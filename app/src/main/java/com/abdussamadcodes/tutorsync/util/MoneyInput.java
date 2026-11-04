package com.abdussamadcodes.tutorsync.util;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * M5: one safe way to read and show money typed by the tutor.
 *
 * - exact decimal arithmetic (no double rounding errors),
 * - at most 2 decimals (a 3rd decimal is rejected, never silently rounded),
 * - zero, negative, non-numeric and absurdly large values are rejected,
 * - the result always fits in an int, so totals cannot overflow.
 */
public final class MoneyInput {

    /** Returned when the text is not a valid positive amount. */
    public static final int INVALID = -1;
    /** Returned when the amount is valid but above the allowed maximum. */
    public static final int TOO_LARGE = -2;

    /** Largest fee for one class or one month: BDT 500,000. */
    public static final int MAX_FEE_MINOR = 50_000_000;
    /** Largest single payment: BDT 10,000,000. */
    public static final int MAX_PAYMENT_MINOR = 1_000_000_000;

    private MoneyInput() {
    }

    /**
     * @return the amount in minor units (paisa), or {@link #INVALID} / {@link #TOO_LARGE}.
     *         A positive result is always a valid amount.
     */
    public static int parse(@NonNull String raw, int maxMinor) {
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.trim().toCharArray()) {
            if (Character.isDigit(ch)) {            // also Bengali digits
                sb.append(Character.digit(ch, 10));
            } else if (ch == '.') {
                sb.append('.');
            } else {
                return INVALID;                       // letters, signs, spaces inside the number, ...
            }
        }
        if (sb.length() == 0) return INVALID;
        try {
            BigDecimal amount = new BigDecimal(sb.toString());
            if (amount.signum() <= 0) return INVALID;
            amount = amount.stripTrailingZeros();
            if (amount.scale() > 2) return INVALID;
            BigDecimal minor = amount.movePointRight(2);
            if (minor.compareTo(BigDecimal.valueOf(maxMinor)) > 0) return TOO_LARGE;
            return minor.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return INVALID;
        }
    }

    /** Text for an input box: "1250" for whole amounts, "1250.50" otherwise (never loses decimals). */
    @NonNull
    public static String formatForInput(int minor) {
        if (minor % 100 == 0) {
            return String.valueOf(minor / 100);
        }
        return String.format(Locale.US, "%d.%02d", minor / 100, Math.abs(minor % 100));
    }

    /** Text for a label: "৳1250" or "৳1250.50". */
    @NonNull
    public static String formatDisplay(int minor) {
        return "৳" + formatForInput(minor);
    }
}

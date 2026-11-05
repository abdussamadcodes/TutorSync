package com.abdussamadcodes.tutorsync.security;

/**
 * M9: how long the lock screen refuses new PIN attempts after wrong PINs.
 *
 * Wrong attempts 1-4 : no delay (a warning appears after the 3rd)
 *                  5 : 30 seconds
 *                  6 : 1 minute
 *                  7 : 5 minutes
 *                  8 : 15 minutes
 *            9 and up: 30 minutes
 * A correct PIN (or setting a new PIN) resets the counter.
 */
public final class PinLockoutPolicy {

    public static final int FREE_ATTEMPTS = 5;
    public static final long MAX_LOCKOUT_MS = 30L * 60L * 1000L;

    private PinLockoutPolicy() {
    }

    public static long lockoutDurationMs(int failedAttempts) {
        if (failedAttempts < FREE_ATTEMPTS) return 0L;
        switch (failedAttempts) {
            case 5:
                return 30_000L;
            case 6:
                return 60_000L;
            case 7:
                return 5L * 60_000L;
            case 8:
                return 15L * 60_000L;
            default:
                return MAX_LOCKOUT_MS;
        }
    }

    /** Wrong attempts that are still allowed before the first lockout starts. */
    public static int attemptsBeforeLockout(int failedAttempts) {
        return Math.max(0, FREE_ATTEMPTS - failedAttempts);
    }
}

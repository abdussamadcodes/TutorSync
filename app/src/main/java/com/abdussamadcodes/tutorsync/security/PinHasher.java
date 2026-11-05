package com.abdussamadcodes.tutorsync.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * M9: PIN hashing.
 *
 * New format ("v3"):  v3$iterations$salt$hash$pinLength
 *  - a fresh random 16-byte salt for every saved PIN (old versions used ONE fixed salt for every
 *    install, so a leaked hash could be cracked with a single pre-computed table),
 *  - PBKDF2-HMAC-SHA256 with 150,000 rounds (old: 10,000),
 *  - constant-time comparison.
 *
 * Old PINs (a plain Base64 hash) can still be verified, and {@link #needsUpgrade} tells the caller
 * to re-save them in the new format after a successful unlock, so nobody has to set a new PIN.
 */
public final class PinHasher {

    public static final int ITERATIONS = 150_000;

    private static final String PREFIX = "v3$";
    private static final int SALT_LEN = 16;
    private static final int KEY_BITS = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    // The fixed salt and round count used by app versions before this fix (verification only).
    private static final String LEGACY_SALT_BASE64 = "VHV0b3JTeW5jUGluU2FsdA==";
    private static final int LEGACY_ITERATIONS = 10_000;

    private PinHasher() {
    }

    @NonNull
    public static String hash(@NonNull String pin) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        byte[] h = pbkdf2(pin, salt, ITERATIONS);
        Base64.Encoder enc = Base64.getEncoder();
        return PREFIX + ITERATIONS + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(h) + "$" + pin.length();
    }

    public static boolean verify(@NonNull String pin, @Nullable String stored) {
        if (stored == null || stored.isEmpty()) return false;
        try {
            if (!stored.startsWith(PREFIX)) {
                byte[] expected = Base64.getDecoder().decode(stored);
                byte[] actual = pbkdf2(pin, Base64.getDecoder().decode(LEGACY_SALT_BASE64), LEGACY_ITERATIONS);
                return MessageDigest.isEqual(expected, actual);
            }
            String[] p = stored.split("\\$");
            if (p.length < 5) return false;
            int iterations = Integer.parseInt(p[1]);
            if (iterations < LEGACY_ITERATIONS || iterations > 5_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(expected, pbkdf2(pin, salt, iterations));
        } catch (Exception e) {
            return false;
        }
    }

    /** True for an old-format hash (or fewer rounds than today): re-save it after a correct PIN. */
    public static boolean needsUpgrade(@Nullable String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return true;
        String[] p = stored.split("\\$");
        try {
            return p.length < 5 || Integer.parseInt(p[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** Length of the saved PIN (4-6), or 0 when unknown (old-format hash). */
    public static int storedLength(@Nullable String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return 0;
        String[] p = stored.split("\\$");
        try {
            return p.length >= 5 ? Integer.parseInt(p[4]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static byte[] pbkdf2(String pin, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}

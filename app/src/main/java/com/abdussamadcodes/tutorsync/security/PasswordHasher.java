package com.abdussamadcodes.tutorsync.security;

import android.util.Base64;
import androidx.annotation.NonNull;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private PasswordHasher() {}

    @NonNull
    public static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    @NonNull
    public static String hashPassword(@NonNull String password, @NonNull String saltBase64) throws Exception {
        byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] hash = factory.generateSecret(spec).getEncoded();
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }
}
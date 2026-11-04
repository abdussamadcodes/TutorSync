package com.abdussamadcodes.tutorsync.util;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * M8: encryption for backup files.
 *
 * File layout:  "TSBK" | version | mode | pbkdf2 iterations | salt(16) | base IV(12) | chunks...
 * Every chunk is [length][AES-256-GCM ciphertext+tag] of at most 64 KB, so memory use stays flat for
 * any backup size. The header, the chunk number and a "last chunk" flag are authenticated, so a
 * changed header, re-ordered chunks or a truncated file are all detected.
 *
 * MODE_PASSWORD : key derived from the tutor's password with PBKDF2-HMAC-SHA256 (200,000 rounds).
 * MODE_KEYSTORE : key kept in the Android Keystore (used for automatic safety backups).
 */
public final class BackupCrypto {

    /** The backup is encrypted but no password was supplied. */
    public static final class PasswordRequiredException extends IOException {
        public PasswordRequiredException() {
            super("This backup is password protected.");
        }
    }

    /** The password is wrong (or the first part of the file is damaged). */
    public static final class WrongPasswordException extends IOException {
        public WrongPasswordException() {
            super("Wrong password, or the backup file is damaged.");
        }
    }

    public static final int MODE_PASSWORD = 1;
    public static final int MODE_KEYSTORE = 2;

    private static final byte[] MAGIC = new byte[]{'T', 'S', 'B', 'K'};
    private static final int FORMAT_VERSION = 1;
    private static final int PBKDF2_ITERATIONS = 200_000;
    private static final int MIN_ITERATIONS = 10_000;
    private static final int MAX_ITERATIONS = 2_000_000;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int CHUNK = 64 * 1024;
    private static final int HEADER_LEN = 4 + 1 + 1 + 4 + SALT_LEN + IV_LEN;
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "tutorsync_safety_backup_key_v1";

    private static final SecureRandom RANDOM = new SecureRandom();

    private BackupCrypto() {
    }

    // ------------------------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------------------------

    /** True when the stream starts with the encrypted-backup marker. The stream position is restored. */
    public static boolean isEncrypted(@NonNull BufferedInputStream in) throws IOException {
        in.mark(8);
        try {
            byte[] head = new byte[MAGIC.length];
            int total = 0;
            while (total < head.length) {
                int n = in.read(head, total, head.length - total);
                if (n < 0) break;
                total += n;
            }
            return total == MAGIC.length && Arrays.equals(head, MAGIC);
        } finally {
            in.reset();
        }
    }

    // ------------------------------------------------------------------------------------
    // Encrypt
    // ------------------------------------------------------------------------------------

    /** Wraps {@code out}; everything written is encrypted with a key derived from the password. */
    @NonNull
    public static OutputStream encryptWithPassword(@NonNull OutputStream out, @NonNull char[] password)
            throws IOException {
        try {
            byte[] salt = randomBytes(SALT_LEN);
            byte[] iv = randomBytes(IV_LEN);
            SecretKey key = deriveKey(password, salt, PBKDF2_ITERATIONS);
            byte[] header = buildHeader(MODE_PASSWORD, PBKDF2_ITERATIONS, salt, iv);
            out.write(header);
            return new EncryptingOutputStream(out, key, header, iv);
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not start encryption", e);
        }
    }

    /** Wraps {@code out}; everything written is encrypted with a device-bound Keystore key. */
    @NonNull
    public static OutputStream encryptWithKeystore(@NonNull OutputStream out) throws IOException {
        try {
            byte[] iv = randomBytes(IV_LEN);
            SecretKey key = getOrCreateKeystoreKey();
            byte[] header = buildHeader(MODE_KEYSTORE, 0, new byte[SALT_LEN], iv);
            out.write(header);
            return new EncryptingOutputStream(out, key, header, iv);
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not start encryption", e);
        }
    }

    // ------------------------------------------------------------------------------------
    // Decrypt
    // ------------------------------------------------------------------------------------

    /**
     * Wraps an encrypted backup stream and returns the decrypted bytes.
     *
     * @throws PasswordRequiredException when a password is needed and {@code password} is null/empty
     * @throws WrongPasswordException    (on the first read) when the password is wrong
     */
    @NonNull
    public static InputStream decrypt(@NonNull InputStream rawIn, @Nullable char[] password) throws IOException {
        BufferedInputStream in = rawIn instanceof BufferedInputStream
                ? (BufferedInputStream) rawIn : new BufferedInputStream(rawIn, 16 * 1024);
        byte[] header = readFully(in, HEADER_LEN);
        if (!Arrays.equals(Arrays.copyOfRange(header, 0, 4), MAGIC)) {
            throw new IOException("This is not an encrypted TutorSync backup.");
        }
        if ((header[4] & 0xFF) != FORMAT_VERSION) {
            throw new IOException("This backup was made by a newer version of the app.");
        }
        int mode = header[5] & 0xFF;
        ByteBuffer bb = ByteBuffer.wrap(header, 6, 4);
        int iterations = bb.getInt();
        byte[] salt = Arrays.copyOfRange(header, 10, 10 + SALT_LEN);
        byte[] iv = Arrays.copyOfRange(header, 10 + SALT_LEN, HEADER_LEN);

        try {
            SecretKey key;
            if (mode == MODE_PASSWORD) {
                if (password == null || password.length == 0) throw new PasswordRequiredException();
                if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
                    throw new IOException("The backup file is damaged.");
                }
                key = deriveKey(password, salt, iterations);
            } else if (mode == MODE_KEYSTORE) {
                key = getOrCreateKeystoreKey();
            } else {
                throw new IOException("Unsupported backup encryption.");
            }
            return new DecryptingInputStream(in, key, header, iv);
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not start decryption", e);
        }
    }

    // ------------------------------------------------------------------------------------
    // Streams
    // ------------------------------------------------------------------------------------

    private static final class EncryptingOutputStream extends OutputStream {
        private final OutputStream out;
        private final SecretKey key;
        private final byte[] header;
        private final byte[] baseIv;
        private final byte[] buf = new byte[CHUNK];
        private int len = 0;
        private long counter = 0;
        private boolean closed = false;

        EncryptingOutputStream(OutputStream out, SecretKey key, byte[] header, byte[] baseIv) {
            this.out = out;
            this.key = key;
            this.header = header;
            this.baseIv = baseIv;
        }

        @Override
        public void write(int b) throws IOException {
            if (len == CHUNK) flushChunk(false);
            buf[len++] = (byte) b;
        }

        @Override
        public void write(@NonNull byte[] b, int off, int n) throws IOException {
            while (n > 0) {
                if (len == CHUNK) flushChunk(false);   // only when more data follows, so the last chunk is flagged
                int c = Math.min(n, CHUNK - len);
                System.arraycopy(b, off, buf, len, c);
                len += c;
                off += c;
                n -= c;
            }
        }

        private void flushChunk(boolean last) throws IOException {
            try {
                Cipher cipher = gcm(Cipher.ENCRYPT_MODE, key, chunkIv(baseIv, counter), chunkAad(header, counter, last));
                byte[] ct = cipher.doFinal(buf, 0, len);
                out.write(ByteBuffer.allocate(4).putInt(ct.length).array());
                out.write(ct);
                counter++;
                len = 0;
            } catch (GeneralSecurityException e) {
                throw new IOException("Encryption failed", e);
            }
        }

        @Override
        public void flush() throws IOException {
            // chunks are written when full / on close; nothing to do (a partial chunk must stay buffered)
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                flushChunk(true);
                out.flush();
            } finally {
                out.close();
                Arrays.fill(buf, (byte) 0);
            }
        }
    }

    private static final class DecryptingInputStream extends InputStream {
        private final BufferedInputStream in;
        private final SecretKey key;
        private final byte[] header;
        private final byte[] baseIv;
        private byte[] cur = new byte[0];
        private int pos = 0;
        private long counter = 0;
        private boolean done = false;

        DecryptingInputStream(BufferedInputStream in, SecretKey key, byte[] header, byte[] baseIv) {
            this.in = in;
            this.key = key;
            this.header = header;
            this.baseIv = baseIv;
        }

        @Override
        public int read() throws IOException {
            while (pos >= cur.length) {
                if (!nextChunk()) return -1;
            }
            return cur[pos++] & 0xFF;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int n) throws IOException {
            if (n == 0) return 0;
            while (pos >= cur.length) {
                if (!nextChunk()) return -1;
            }
            int c = Math.min(n, cur.length - pos);
            System.arraycopy(cur, pos, b, off, c);
            pos += c;
            return c;
        }

        private boolean nextChunk() throws IOException {
            if (done) return false;
            int len = ByteBuffer.wrap(readFully(in, 4)).getInt();
            if (len < 16 || len > CHUNK + 16) {
                throw counter == 0 ? new WrongPasswordException() : new IOException("The backup file is damaged.");
            }
            byte[] ct = readFully(in, len);
            in.mark(1);
            boolean last = in.read() < 0;
            if (!last) in.reset();
            try {
                Cipher cipher = gcm(Cipher.DECRYPT_MODE, key, chunkIv(baseIv, counter), chunkAad(header, counter, last));
                cur = cipher.doFinal(ct);
            } catch (GeneralSecurityException e) {
                if (counter == 0) throw new WrongPasswordException();
                throw new IOException("The backup file is damaged or incomplete.");
            }
            counter++;
            pos = 0;
            if (last) done = true;
            return cur.length > 0 || !done;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    private static byte[] buildHeader(int mode, int iterations, byte[] salt, byte[] iv) {
        return ByteBuffer.allocate(HEADER_LEN)
                .put(MAGIC).put((byte) FORMAT_VERSION).put((byte) mode)
                .putInt(iterations).put(salt).put(iv).array();
    }

    private static byte[] chunkIv(byte[] baseIv, long counter) {
        byte[] iv = baseIv.clone();
        for (int i = 0; i < 8; i++) {
            iv[IV_LEN - 1 - i] ^= (byte) (counter >>> (8 * i));
        }
        return iv;
    }

    private static byte[] chunkAad(byte[] header, long counter, boolean last) {
        return ByteBuffer.allocate(header.length + 9).put(header).putLong(counter).put((byte) (last ? 1 : 0)).array();
    }

    private static Cipher gcm(int mode, SecretKey key, byte[] iv, byte[] aad) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        c.updateAAD(aad);
        return c;
    }

    private static SecretKey deriveKey(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(raw, "AES");
            } finally {
                Arrays.fill(raw, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    private static SecretKey getOrCreateKeystoreKey() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(false)   // we supply a unique IV for every chunk
                .build());
        return kg.generateKey();
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int total = 0;
        while (total < n) {
            int r = in.read(b, total, n - total);
            if (r < 0) throw new IOException("The backup file is incomplete.");
            total += r;
        }
        return b;
    }
}

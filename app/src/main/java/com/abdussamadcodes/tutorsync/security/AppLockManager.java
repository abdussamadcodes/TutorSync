package com.abdussamadcodes.tutorsync.security;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.abdussamadcodes.tutorsync.ui.account.PinLockActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignUpActivity;
import com.abdussamadcodes.tutorsync.ui.onboarding.WelcomeActivity;

import java.lang.ref.WeakReference;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class AppLockManager implements Application.ActivityLifecycleCallbacks {
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String PROBE_KEY_ALIAS = "tutorsync_device_unlock_probe";

    private static AppLockManager instance;

    private final Application application;
    private final SessionManager sessionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isSessionUnlocked;
    private boolean isLockScreenShowing = false;
    private WeakReference<Activity> topActivityRef = new WeakReference<>(null);

    // Tracks foreground state so Keystore is probed at most once per foreground session
    private int startedActivityCount = 0;
    private boolean wasKeystoreProbedThisForegroundSession = false;

    private AppLockManager(@NonNull Application application) {
        this.application = application;
        this.sessionManager = new SessionManager(application);

        if (isDeviceCurrentlyLocked(application)) {
            this.isSessionUnlocked = false;
            this.sessionManager.setSessionUnlockedState(false);
        } else {
            this.isSessionUnlocked = this.sessionManager.isSessionUnlockedForCurrentBoot();
        }

        application.registerActivityLifecycleCallbacks(this);
        registerDisplayStateListener(application);
        registerScreenAndKeyguardReceiver(application);
    }

    private void registerDisplayStateListener(@NonNull Application app) {
        DisplayManager dm = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return;

        dm.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayRemoved(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) return;
                Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (display != null && display.getState() != Display.STATE_ON) {
                    lockSession();
                } else if (isDeviceCurrentlyLocked(app)) {
                    lockSession();
                }
            }
        }, mainHandler);
    }

    private void registerScreenAndKeyguardReceiver(@NonNull Application app) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    lockSession();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    if (isDeviceCurrentlyLocked(context)) {
                        lockSession();
                    }
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    long lastUnlock = sessionManager.getLastAppUnlockRealtime();
                    long elapsed = SystemClock.elapsedRealtime() - lastUnlock;
                    if (lastUnlock == 0L || elapsed > 1200L) {
                        lockSession();
                    }
                    Activity top = topActivityRef.get();
                    if (top != null && !top.isFinishing() && !top.isDestroyed()) {
                        promptLockScreenIfNeeded(top);
                    }
                }
            }
        };

        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    public static synchronized void init(@NonNull Application application) {
        if (instance == null) {
            instance = new AppLockManager(application);
        }
    }

    @NonNull
    public static AppLockManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AppLockManager not initialized");
        }
        return instance;
    }

    public void markUnlocked() {
        isSessionUnlocked = true;
        isLockScreenShowing = false;
        wasKeystoreProbedThisForegroundSession = true;
        sessionManager.setSessionUnlockedState(true);
    }

    public void markExternalFlowStarted() {
        // Session stays unlocked throughout the current phone-unlocked session
    }

    public void resetLockState() {
        isLockScreenShowing = false;
        lockSession();
    }

    private void lockSession() {
        isSessionUnlocked = false;
        wasKeystoreProbedThisForegroundSession = false;
        sessionManager.setSessionUnlockedState(false);
    }

    private boolean isDeviceCurrentlyLocked(@NonNull Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

        boolean screenOff = pm != null && !pm.isInteractive();
        boolean deviceLocked = km != null && (km.isKeyguardLocked() || km.isDeviceLocked());

        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) {
            Display defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (defaultDisplay != null && defaultDisplay.getState() != Display.STATE_ON) {
                screenOff = true;
            }
        }
        return screenOff || deviceLocked;
    }

    /**
     * Checks AndroidKeyStore's hardware authentication timestamp table to detect if the user
     * unlocked the device lock screen AFTER the last time they unlocked TutorSync.
     * This catches phone lock/unlock cycles even if the app process was killed in Recent Apps.
     */
    private boolean hasDeviceUnlockedSinceAppUnlock() {
        long lastAppUnlockRealtime = sessionManager.getLastAppUnlockRealtime();
        if (lastAppUnlockRealtime <= 0L) {
            return true;
        }

        long elapsedMs = SystemClock.elapsedRealtime() - lastAppUnlockRealtime;
        if (elapsedMs < 0L) {
            return true; // Device rebooted
        }

        int elapsedSeconds = (int) (elapsedMs / 1000L);
        if (elapsedSeconds < 4) {
            return false;
        }

        KeyguardManager km = (KeyguardManager) application.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            return false;
        }

        int probeTimeoutSeconds = Math.max(1, elapsedSeconds - 3);
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    PROBE_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                        probeTimeoutSeconds,
                        KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
            } else {
                //noinspection deprecation
                builder.setUserAuthenticationValidityDurationSeconds(probeTimeoutSeconds);
            }

            SecretKey secretKey = keyGenerator.generateKey();
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            // Cipher.init succeeded, meaning a device unlock occurred within probeTimeoutSeconds
            return true;
        } catch (UserNotAuthenticatedException e) {
            // No device unlock occurred within probeTimeoutSeconds; phone remained unlocked
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
                ks.load(null);
                if (ks.containsAlias(PROBE_KEY_ALIAS)) {
                    ks.deleteEntry(PROBE_KEY_ALIAS);
                }
            } catch (Throwable ignored) {}
        }
    }

    private boolean isExemptActivity(@NonNull Activity activity) {
        return activity instanceof PinLockActivity
                || activity instanceof SignInActivity
                || activity instanceof SignUpActivity
                || activity instanceof WelcomeActivity;
    }

    private void promptLockScreenIfNeeded(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (activity instanceof PinLockActivity) {
            isLockScreenShowing = true;
            return;
        }
        if (isExemptActivity(activity)) {
            return;
        }

        if (!sessionManager.hasActiveSession() || !sessionManager.hasPinSetup()) {
            markUnlocked();
            return;
        }

        if (isDeviceCurrentlyLocked(activity)) {
            lockSession();
        } else if (isSessionUnlocked && sessionManager.isSessionUnlockedForCurrentBoot()) {
            // Only probe AndroidKeyStore once when entering the foreground, never during in-app navigation
            if (!wasKeystoreProbedThisForegroundSession) {
                if (hasDeviceUnlockedSinceAppUnlock()) {
                    lockSession();
                } else {
                    wasKeystoreProbedThisForegroundSession = true;
                    return;
                }
            } else {
                return;
            }
        } else {
            lockSession();
        }

        if (!isLockScreenShowing) {
            isLockScreenShowing = true;
            Intent intent = new Intent(activity, PinLockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(intent);
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (!isExemptActivity(activity)) {
            if (startedActivityCount == 0) {
                wasKeystoreProbedThisForegroundSession = false;
            }
            startedActivityCount++;
            topActivityRef = new WeakReference<>(activity);
            if (isDeviceCurrentlyLocked(activity)) {
                lockSession();
            }
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof PinLockActivity) {
            isLockScreenShowing = true;
            return;
        }
        if (!isExemptActivity(activity)) {
            topActivityRef = new WeakReference<>(activity);
        }
        promptLockScreenIfNeeded(activity);

        // Follow-up check after keyguard dismiss animation completes
        mainHandler.postDelayed(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed() && !isExemptActivity(activity)) {
                if (!isSessionUnlocked && !isLockScreenShowing) {
                    promptLockScreenIfNeeded(activity);
                }
            }
        }, 200L);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (isDeviceCurrentlyLocked(activity)) {
            lockSession();
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (!isExemptActivity(activity)) {
            startedActivityCount = Math.max(0, startedActivityCount - 1);
        }
        if (isDeviceCurrentlyLocked(activity)) {
            lockSession();
        }
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (activity instanceof PinLockActivity) {
            isLockScreenShowing = false;
        }
    }
}
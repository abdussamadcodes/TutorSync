package com.abdussamadcodes.tutorsync.security;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.ui.account.PinLockActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignUpActivity;
import com.abdussamadcodes.tutorsync.ui.onboarding.WelcomeActivity;

public final class AppLockManager implements Application.ActivityLifecycleCallbacks {
    private static final long LOCK_GRACE_PERIOD_MS = 3 * 60 * 1000L; // 3 minutes grace period for brief app switches
    private static final long EXTERNAL_FLOW_GRACE_MS = 10 * 60 * 1000L; // 10 minutes for gallery/document pickers

    private static AppLockManager instance;

    private final SessionManager sessionManager;
    private boolean isSessionUnlocked = false;
    private boolean isLockScreenShowing = false;
    private int startedActivityCount = 0;

    private AppLockManager(@NonNull Application application) {
        this.sessionManager = new SessionManager(application);

        // Restore unlocked state if the process was recreated while briefly in background or in an external picker
        long now = System.currentTimeMillis();
        long lastBg = sessionManager.getLastBackgroundTimestamp();
        long extFlowTs = sessionManager.getExternalFlowTimestamp();
        boolean withinBgGrace = lastBg > 0 && (now - lastBg) >= 0 && (now - lastBg) < LOCK_GRACE_PERIOD_MS;
        boolean withinExtGrace = extFlowTs > 0 && (now - extFlowTs) >= 0 && (now - extFlowTs) < EXTERNAL_FLOW_GRACE_MS;

        if (sessionManager.isSessionUnlockedState() && (withinBgGrace || withinExtGrace)) {
            this.isSessionUnlocked = true;
        } else {
            this.isSessionUnlocked = false;
            sessionManager.setSessionUnlockedState(false);
        }

        application.registerActivityLifecycleCallbacks(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        application.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    if (sessionManager.getLastBackgroundTimestamp() == 0L) {
                        sessionManager.setLastBackgroundTimestamp(System.currentTimeMillis());
                    }
                }
            }
        }, filter);
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
        sessionManager.setSessionUnlockedState(true);
        sessionManager.setLastBackgroundTimestamp(0L);
        sessionManager.setExternalFlowTimestamp(0L);
    }

    public void markExternalFlowStarted() {
        sessionManager.setExternalFlowTimestamp(System.currentTimeMillis());
    }

    public void resetLockState() {
        isSessionUnlocked = false;
        isLockScreenShowing = false;
        sessionManager.setSessionUnlockedState(false);
        sessionManager.setLastBackgroundTimestamp(0L);
        sessionManager.setExternalFlowTimestamp(0L);
    }

    private boolean isExemptActivity(@NonNull Activity activity) {
        return activity instanceof PinLockActivity
                || activity instanceof SignInActivity
                || activity instanceof SignUpActivity
                || activity instanceof WelcomeActivity;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (!(activity instanceof PinLockActivity)) {
            startedActivityCount++;
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
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

        long now = System.currentTimeMillis();
        long extFlowTs = sessionManager.getExternalFlowTimestamp();
        if (extFlowTs > 0) {
            sessionManager.setExternalFlowTimestamp(0L);
            if ((now - extFlowTs) >= 0 && (now - extFlowTs) < EXTERNAL_FLOW_GRACE_MS) {
                markUnlocked();
                return;
            }
        }

        long lastBg = sessionManager.getLastBackgroundTimestamp();
        if (lastBg > 0) {
            long elapsed = now - lastBg;
            sessionManager.setLastBackgroundTimestamp(0L);
            if (elapsed >= LOCK_GRACE_PERIOD_MS) {
                isSessionUnlocked = false;
                sessionManager.setSessionUnlockedState(false);
            } else if (sessionManager.isSessionUnlockedState()) {
                isSessionUnlocked = true;
            }
        }

        if (!isSessionUnlocked && !isLockScreenShowing) {
            isLockScreenShowing = true;
            Intent intent = new Intent(activity, PinLockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(intent);
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (activity instanceof PinLockActivity) {
            return;
        }
        startedActivityCount = Math.max(0, startedActivityCount - 1);
        if (startedActivityCount == 0 && !activity.isChangingConfigurations()) {
            sessionManager.setLastBackgroundTimestamp(System.currentTimeMillis());
        }
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivityPaused(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (activity instanceof PinLockActivity) {
            isLockScreenShowing = false;
        }
    }
}
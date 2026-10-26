package com.abdussamadcodes.tutorsync.security;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.ui.account.PinLockActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignUpActivity;
import com.abdussamadcodes.tutorsync.ui.onboarding.WelcomeActivity;

public final class AppLockManager implements Application.ActivityLifecycleCallbacks {
    private static AppLockManager instance;

    private final SessionManager sessionManager;
    private boolean isSessionUnlocked = false;
    private boolean isLockScreenShowing = false;
    private int startedActivityCount = 0;

    private AppLockManager(@NonNull Application application) {
        this.sessionManager = new SessionManager(application);
        application.registerActivityLifecycleCallbacks(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        application.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    isSessionUnlocked = false;
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
    }

    public void resetLockState() {
        isSessionUnlocked = false;
        isLockScreenShowing = false;
    }

    private boolean isExemptActivity(@NonNull Activity activity) {
        return activity instanceof PinLockActivity
                || activity instanceof SignInActivity
                || activity instanceof SignUpActivity
                || activity instanceof WelcomeActivity;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        startedActivityCount++;
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

        if (sessionManager.hasActiveSession() && sessionManager.hasPinSetup()) {
            if (!isSessionUnlocked && !isLockScreenShowing) {
                isLockScreenShowing = true;
                Intent intent = new Intent(activity, PinLockActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(intent);
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        } else {
            isSessionUnlocked = true;
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedActivityCount = Math.max(0, startedActivityCount - 1);
        if (startedActivityCount == 0 && !activity.isChangingConfigurations()) {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);

            boolean screenOff = pm != null && !pm.isInteractive();
            boolean deviceLocked = km != null && km.isKeyguardLocked();

            if (screenOff || deviceLocked) {
                isSessionUnlocked = false;
            }
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
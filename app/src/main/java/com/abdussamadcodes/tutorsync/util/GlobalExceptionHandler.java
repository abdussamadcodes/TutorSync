package com.abdussamadcodes.tutorsync.util;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "TutorSyncCrashGuard";
    private final Context appContext;
    @Nullable
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public GlobalExceptionHandler(
            @NonNull Context context,
            @Nullable Thread.UncaughtExceptionHandler defaultHandler) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = defaultHandler;
    }

    public static void install(@NonNull Context context) {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (!(current instanceof GlobalExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler(context, current));
        }
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        try {
            String safeSummary = throwable.getClass().getSimpleName()
                    + " on thread " + thread.getName()
                    + " at " + System.currentTimeMillis();
            Log.e(TAG, "Unhandled exception caught: " + safeSummary, throwable);
            new AppPreferences(appContext).recordLastCrashSummary(safeSummary);
        } catch (Throwable ignored) {
            // Never block default crash reporting
        } finally {
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }
}
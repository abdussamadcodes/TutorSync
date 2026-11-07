package com.abdussamadcodes.tutorsync.ui.common;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.abdussamadcodes.tutorsync.receiver.SystemStatusReceiver;
import com.abdussamadcodes.tutorsync.service.ClassReminderService;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.google.android.material.snackbar.Snackbar;

public abstract class BaseActivity extends AppCompatActivity {
    protected AppPreferences appPreferences;
    private SystemStatusReceiver systemStatusReceiver;

    // Runtime Permission request for Android 13+ Notifications
    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startReminderService();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appPreferences = new AppPreferences(this);
        systemStatusReceiver = new SystemStatusReceiver();

        // Check for Notification Permission on Android 13+ before starting the Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                startReminderService();
            }
        } else {
            // Android 12 and below automatically grant notification permissions
            startReminderService();
        }
    }

    private void startReminderService() {
        try {
            Intent serviceIntent = new Intent(this, ClassReminderService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception ignored) {
            // Safety catch
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Dynamically register the System Broadcast Receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(systemStatusReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister to prevent memory leaks when Activity is in background
        try {
            unregisterReceiver(systemStatusReceiver);
        } catch (IllegalArgumentException e) {
            // Ignored if receiver wasn't registered
        }
    }

    protected void applySystemBarInsets(@NonNull View rootView) {
        final int initialLeft = rootView.getPaddingLeft();
        final int initialTop = rootView.getPaddingTop();
        final int initialRight = rootView.getPaddingRight();
        final int initialBottom = rootView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(initialLeft + insets.left, initialTop + insets.top, initialRight + insets.right, initialBottom + insets.bottom);
            return windowInsets;
        });
    }

    protected void showMessage(@NonNull View anchorView, @StringRes int messageResId) {
        Snackbar.make(anchorView, messageResId, Snackbar.LENGTH_SHORT).show();
    }

    protected void showMessage(@NonNull View anchorView, @NonNull String message) {
        Snackbar.make(anchorView, message, Snackbar.LENGTH_SHORT).show();
    }
}
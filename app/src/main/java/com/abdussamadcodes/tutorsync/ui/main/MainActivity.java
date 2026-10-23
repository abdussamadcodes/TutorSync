package com.abdussamadcodes.tutorsync.ui.main;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityMainBinding;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.account.PinLockActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.onboarding.WelcomeActivity;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;

public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private SessionManager sessionManager;
    private boolean isPinVerifiedThisSession = false;
    private static final String STATE_PIN_VERIFIED = "state_pin_verified";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            isPinVerifiedThisSession = savedInstanceState.getBoolean(STATE_PIN_VERIFIED, false);
        }

        sessionManager = new SessionManager(this);

        if (!sessionManager.hasActiveSession()) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.mainScrollRoot);
        setupListeners();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PIN_VERIFIED, isPinVerifiedThisSession);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager.hasActiveSession()) {
            refreshStatusLabels();

            // Trigger PIN Lock on resume if not verified (Mock setting for Phase 2B)
            if (!isPinVerifiedThisSession) {
                startActivity(new Intent(this, PinLockActivity.class));
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
                } else {
                    //noinspection deprecation
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }
                isPinVerifiedThisSession = true;
            }
        }
    }

    private void refreshStatusLabels() {
        binding.tvActiveLanguageValue.setText(R.string.main_current_language_value);
        String themeMode = appPreferences.getThemeMode();
        if (AppPreferences.THEME_LIGHT.equals(themeMode)) binding.tvActiveThemeValue.setText(R.string.theme_light);
        else if (AppPreferences.THEME_DARK.equals(themeMode)) binding.tvActiveThemeValue.setText(R.string.theme_dark);
        else binding.tvActiveThemeValue.setText(R.string.theme_system);
    }

    private void setupListeners() {
        binding.btnOpenLanguageSettings.setOnClickListener(v -> startActivity(WelcomeActivity.createReopenIntent(this)));

        binding.btnCycleTheme.setOnClickListener(v -> {
            String current = appPreferences.getThemeMode();
            String next = AppPreferences.THEME_SYSTEM.equals(current) ? AppPreferences.THEME_LIGHT :
                    AppPreferences.THEME_LIGHT.equals(current) ? AppPreferences.THEME_DARK : AppPreferences.THEME_SYSTEM;
            appPreferences.setThemeMode(next);
            refreshStatusLabels();
            LocaleThemeHelper.applyThemeMode(next);
            showMessage(binding.getRoot(), R.string.msg_theme_updated);
        });

        // Use the explicit Sign Out button
        binding.btnSignOut.setOnClickListener(v -> {
            sessionManager.logout();
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });

        // Open Students List
        binding.cardStudents.setOnClickListener(v -> {
            startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.student.StudentListActivity.class));
        });
    }
}
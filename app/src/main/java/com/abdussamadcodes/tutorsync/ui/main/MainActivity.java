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

        // Restore the PIN verification state if the activity was recreated (e.g., theme change)
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
        // Save the PIN verification state before the activity is destroyed
        outState.putBoolean(STATE_PIN_VERIFIED, isPinVerifiedThisSession);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager.hasActiveSession()) {
            refreshStatusLabels();
            // Trigger PIN Lock only if it hasn't been verified yet this session
            if (!isPinVerifiedThisSession) {
                startActivity(new Intent(this, PinLockActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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

        // Sign Out
        binding.mainContentContainer.getChildAt(0).setOnClickListener(v -> {
            sessionManager.logout();
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
    }
}
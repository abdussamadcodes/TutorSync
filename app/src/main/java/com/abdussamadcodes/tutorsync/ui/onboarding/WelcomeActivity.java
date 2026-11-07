package com.abdussamadcodes.tutorsync.ui.onboarding;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityWelcomeBinding;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.main.MainActivity;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;

public class WelcomeActivity extends BaseActivity {

    public static final String EXTRA_REOPEN_FOR_LANGUAGE_CHANGE = "com.abdussamadcodes.tutorsync.EXTRA_REOPEN_FOR_LANGUAGE_CHANGE";
    private static final String STATE_SETUP_STEP = "state_setup_step";
    private static final int STEP_LANGUAGE = 1;
    private static final int STEP_THEME = 2;

    /** Keeps the active setup step intact across live locale/theme Activity recreations. */
    private static int sCurrentStep = STEP_LANGUAGE;

    private ActivityWelcomeBinding binding;
    private AppPreferences appPreferences;
    private SessionManager sessionManager;
    private int currentStep = STEP_LANGUAGE;
    private boolean isReopenedFromInsideApp = false;

    @NonNull
    public static Intent createReopenIntent(@NonNull Context context) {
        Intent intent = new Intent(context, WelcomeActivity.class);
        intent.putExtra(EXTRA_REOPEN_FOR_LANGUAGE_CHANGE, true);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        appPreferences = new AppPreferences(this);
        sessionManager = new SessionManager(this);
        isReopenedFromInsideApp = getIntent().getBooleanExtra(EXTRA_REOPEN_FOR_LANGUAGE_CHANGE, false);

        if (appPreferences.isOnboardingCompleted() && !isReopenedFromInsideApp) {
            routeAfterOnboarding();
            return;
        }

        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(STATE_SETUP_STEP, sCurrentStep);
        } else {
            currentStep = sCurrentStep;
        }
        sCurrentStep = currentStep;

        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.welcomeRoot);

        setupListeners();
        setupBackNavigation();
        renderSelectionStates();
        renderStep(false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SETUP_STEP, currentStep);
    }

    private void setupListeners() {
        binding.cardLangEnglish.setOnClickListener(v -> selectLanguage(AppPreferences.LANG_ENGLISH));
        binding.cardLangBangla.setOnClickListener(v -> selectLanguage(AppPreferences.LANG_BANGLA));

        binding.cardThemeSystem.setOnClickListener(v -> selectTheme(AppPreferences.THEME_SYSTEM));
        binding.cardThemeLight.setOnClickListener(v -> selectTheme(AppPreferences.THEME_LIGHT));
        binding.cardThemeDark.setOnClickListener(v -> selectTheme(AppPreferences.THEME_DARK));

        binding.btnStepBack.setOnClickListener(v -> {
            if (currentStep == STEP_THEME) {
                currentStep = STEP_LANGUAGE;
                sCurrentStep = STEP_LANGUAGE;
                renderStep(true);
            }
        });

        binding.btnPrimaryAction.setOnClickListener(v -> {
            if (currentStep == STEP_LANGUAGE) {
                currentStep = STEP_THEME;
                sCurrentStep = STEP_THEME;
                renderStep(true);
            } else {
                completeOnboarding();
            }
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep == STEP_THEME) {
                    currentStep = STEP_LANGUAGE;
                    sCurrentStep = STEP_LANGUAGE;
                    renderStep(true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void selectLanguage(@NonNull String langCode) {
        String current = appPreferences.getLanguageCode();
        sCurrentStep = currentStep;
        appPreferences.setLanguageCode(langCode);
        renderSelectionStates();
        if (!current.equals(langCode)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                LocaleThemeHelper.applySavedLocaleAndTheme(this);
            }, 150);
        }
    }

    private void selectTheme(@NonNull String themeMode) {
        String current = appPreferences.getThemeMode();
        sCurrentStep = currentStep;
        appPreferences.setThemeMode(themeMode);
        renderSelectionStates();
        if (!current.equals(themeMode)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                LocaleThemeHelper.applySavedLocaleAndTheme(this);
            }, 150);
        }
    }

    private void renderSelectionStates() {
        boolean isBangla = AppPreferences.LANG_BANGLA.equals(appPreferences.getLanguageCode());
        binding.cardLangEnglish.setChecked(!isBangla);
        binding.cardLangEnglish.setSelected(!isBangla);

        binding.cardLangBangla.setChecked(isBangla);
        binding.cardLangBangla.setSelected(isBangla);

        String theme = appPreferences.getThemeMode();
        boolean isSystem = AppPreferences.THEME_SYSTEM.equals(theme);
        boolean isLight = AppPreferences.THEME_LIGHT.equals(theme);
        boolean isDark = AppPreferences.THEME_DARK.equals(theme);

        binding.cardThemeSystem.setChecked(isSystem);
        binding.cardThemeSystem.setSelected(isSystem);

        binding.cardThemeLight.setChecked(isLight);
        binding.cardThemeLight.setSelected(isLight);

        binding.cardThemeDark.setChecked(isDark);
        binding.cardThemeDark.setSelected(isDark);
    }

    private void renderStep(boolean animate) {
        boolean isStep1 = (currentStep == STEP_LANGUAGE);

        binding.tvStepBadge.setText(isStep1 ? R.string.setup_step_1_of_2 : R.string.setup_step_2_of_2);
        binding.setupProgress.setProgressCompat(isStep1 ? 50 : 100, animate);
        binding.btnStepBack.setVisibility(isStep1 ? View.GONE : View.VISIBLE);

        binding.tvStepTitle.setText(isStep1 ? R.string.setup_lang_title : R.string.setup_theme_title);
        binding.tvStepSubtitle.setText(isStep1 ? R.string.setup_lang_subtitle : R.string.setup_theme_subtitle);

        int actionTextRes;
        if (isStep1) {
            actionTextRes = R.string.setup_action_continue;
        } else {
            actionTextRes = isReopenedFromInsideApp ? R.string.action_save_and_close : R.string.setup_action_get_started;
        }
        binding.btnPrimaryAction.setText(actionTextRes);

        View incoming = isStep1 ? binding.stepContainerLanguage : binding.stepContainerTheme;
        View outgoing = isStep1 ? binding.stepContainerTheme : binding.stepContainerLanguage;

        outgoing.setVisibility(View.GONE);
        if (animate) {
            incoming.setAlpha(0f);
            incoming.setVisibility(View.VISIBLE);
            incoming.animate().alpha(1f).setDuration(180L).start();
        } else {
            incoming.setAlpha(1f);
            incoming.setVisibility(View.VISIBLE);
        }
    }

    private void completeOnboarding() {
        sCurrentStep = STEP_LANGUAGE;
        appPreferences.setOnboardingCompleted(true);
        if (isReopenedFromInsideApp) {
            finish();
        } else {
            routeAfterOnboarding();
        }
    }

    private void routeAfterOnboarding() {
        Class<?> destination = sessionManager.hasActiveSession()
                ? MainActivity.class
                : SignInActivity.class;
        startActivity(new Intent(this, destination));
        finish();
    }
}
package com.abdussamadcodes.tutorsync.ui.onboarding;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityWelcomeBinding;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.main.MainActivity;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;
import com.google.android.material.card.MaterialCardView;

public class WelcomeActivity extends BaseActivity {
    public static final String EXTRA_REOPEN_FOR_LANGUAGE_CHANGE =
            "com.abdussamadcodes.tutorsync.EXTRA_REOPEN_FOR_LANGUAGE_CHANGE";

    private ActivityWelcomeBinding binding;
    private boolean isReopenedFromInsideApp = false;

    @NonNull
    public static Intent createReopenIntent(@NonNull Context context) {
        Intent intent = new Intent(context, WelcomeActivity.class);
        intent.putExtra(EXTRA_REOPEN_FOR_LANGUAGE_CHANGE, true);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isReopenedFromInsideApp = getIntent().getBooleanExtra(EXTRA_REOPEN_FOR_LANGUAGE_CHANGE, false);

        if (appPreferences.isOnboardingCompleted() && !isReopenedFromInsideApp) {
            navigateToMainAndFinish();
            return;
        }

        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.welcomeScrollRoot);

        if (isReopenedFromInsideApp) {
            binding.btnContinue.setText(R.string.action_save_and_close);
        }
        bindCurrentSelections();
        setupListeners();
    }

    private void bindCurrentSelections() {
        String currentLang = appPreferences.getLanguageCode();
        boolean isBangla = AppPreferences.LANG_BANGLA.equals(currentLang);
        updateCardSelectionState(binding.cardLanguageEnglish, binding.ivCheckEnglish, !isBangla);
        updateCardSelectionState(binding.cardLanguageBangla, binding.ivCheckBangla, isBangla);

        String currentTheme = appPreferences.getThemeMode();
        if (AppPreferences.THEME_LIGHT.equals(currentTheme)) {
            binding.toggleGroupTheme.check(R.id.btnThemeLight);
        } else if (AppPreferences.THEME_DARK.equals(currentTheme)) {
            binding.toggleGroupTheme.check(R.id.btnThemeDark);
        } else {
            binding.toggleGroupTheme.check(R.id.btnThemeSystem);
        }
    }

    private void setupListeners() {
        binding.cardLanguageEnglish.setOnClickListener(
                v -> onLanguageChosen(AppPreferences.LANG_ENGLISH)
        );
        binding.cardLanguageBangla.setOnClickListener(
                v -> onLanguageChosen(AppPreferences.LANG_BANGLA)
        );

        binding.toggleGroupTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String targetTheme = AppPreferences.THEME_SYSTEM;
            if (checkedId == R.id.btnThemeLight) {
                targetTheme = AppPreferences.THEME_LIGHT;
            } else if (checkedId == R.id.btnThemeDark) {
                targetTheme = AppPreferences.THEME_DARK;
            }

            if (!targetTheme.equals(appPreferences.getThemeMode())) {
                appPreferences.setThemeMode(targetTheme);
                LocaleThemeHelper.applyThemeMode(targetTheme);
            }
        });

        binding.btnContinue.setOnClickListener(v -> {
            appPreferences.setOnboardingCompleted(true);
            if (isReopenedFromInsideApp) {
                finish();
            } else {
                navigateToMainAndFinish();
            }
        });
    }

    private void onLanguageChosen(@NonNull String languageCode) {
        String currentLang = appPreferences.getLanguageCode();
        appPreferences.setLanguageCode(languageCode);
        bindCurrentSelections();
        if (!languageCode.equals(currentLang)) {
            LocaleThemeHelper.applyLanguage(languageCode);
        }
    }

    private void updateCardSelectionState(
            @NonNull MaterialCardView card,
            @NonNull View checkIcon,
            boolean isSelected) {
        int strokeWidthPx = getResources().getDimensionPixelSize(
                isSelected ? R.dimen.card_stroke_width_selected : R.dimen.card_stroke_width
        );
        int strokeColor = resolveThemeColor(
                isSelected
                        ? com.google.android.material.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorOutlineVariant
        );
        card.setStrokeWidth(strokeWidthPx);
        card.setStrokeColor(strokeColor);
        checkIcon.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
    }

    private int resolveThemeColor(@AttrRes int attrRes) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    private void navigateToMainAndFinish() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
package com.abdussamadcodes.tutorsync.ui.account;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityPinLockBinding;
import com.abdussamadcodes.tutorsync.security.AppLockManager;
import com.abdussamadcodes.tutorsync.security.BiometricAuthHelper;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;

public class PinLockActivity extends BaseActivity {
    private ActivityPinLockBinding binding;
    private SessionManager sessionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean biometricAvailable = false;
    private boolean hasAutoPromptedBiometric = false;

    private final Runnable checkAndPromptBiometricRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || hasAutoPromptedBiometric || !biometricAvailable) {
                return;
            }
            if (isDeviceReadyForBiometricPrompt()) {
                hasAutoPromptedBiometric = true;
                promptFingerprint();
            } else {
                mainHandler.postDelayed(this, 150L);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPinLockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.pinLockRoot);

        sessionManager = new SessionManager(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        biometricAvailable = sessionManager.isBiometricEnabled()
                && BiometricAuthHelper.isBiometricReady(this);

        if (biometricAvailable) {
            binding.btnBiometric.setVisibility(View.VISIBLE);
            binding.btnBiometric.setOnClickListener(v -> promptFingerprint());
        } else {
            binding.btnBiometric.setVisibility(View.GONE);
            focusPinInput();
        }

        binding.etPin.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.etPin.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString();
                if (input.length() >= 4) {
                    if (sessionManager.verifyPin(input)) {
                        unlockAndFinish();
                    } else if (input.length() == 6) {
                        binding.etPin.setError(getString(R.string.app_lock_incorrect_pin));
                    }
                }
            }
        });

        binding.etPin.setOnEditorActionListener((v, actionId, event) -> {
            String input = binding.etPin.getText().toString();
            if (sessionManager.verifyPin(input)) {
                unlockAndFinish();
            } else {
                binding.etPin.setError(getString(R.string.app_lock_incorrect_pin));
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (biometricAvailable && !hasAutoPromptedBiometric) {
            mainHandler.removeCallbacks(checkAndPromptBiometricRunnable);
            mainHandler.post(checkAndPromptBiometricRunnable);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && biometricAvailable && !hasAutoPromptedBiometric) {
            mainHandler.removeCallbacks(checkAndPromptBiometricRunnable);
            mainHandler.post(checkAndPromptBiometricRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(checkAndPromptBiometricRunnable);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean screenOff = pm != null && !pm.isInteractive();
        boolean keyguardLocked = km != null && (km.isKeyguardLocked() || km.isDeviceLocked());
        if (screenOff || keyguardLocked) {
            // Reset so biometric prompt triggers fresh when the user unlocks the phone again
            hasAutoPromptedBiometric = false;
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean isDeviceReadyForBiometricPrompt() {
        if (!hasWindowFocus()) return false;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean screenInteractive = pm == null || pm.isInteractive();
        boolean keyguardLocked = km != null && (km.isKeyguardLocked() || km.isDeviceLocked());
        return screenInteractive && !keyguardLocked;
    }

    private void promptFingerprint() {
        BiometricAuthHelper.promptBiometric(
                this,
                getString(R.string.app_lock_biometric_title),
                getString(R.string.app_lock_biometric_subtitle),
                new BiometricAuthHelper.BiometricCallback() {
                    @Override
                    public void onSuccess() {
                        unlockAndFinish();
                    }

                    @Override
                    public void onError(@NonNull String errString) {
                        focusPinInput();
                    }
                }
        );
    }

    private void focusPinInput() {
        binding.etPin.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etPin, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void unlockAndFinish() {
        AppLockManager.getInstance().markUnlocked();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
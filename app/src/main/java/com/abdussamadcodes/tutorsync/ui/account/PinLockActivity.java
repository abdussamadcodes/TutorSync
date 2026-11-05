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
import com.abdussamadcodes.tutorsync.util.AppExecutors;

public class PinLockActivity extends BaseActivity {
    private ActivityPinLockBinding binding;
    private SessionManager sessionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean biometricAvailable = false;
    private boolean hasAutoPromptedBiometric = false;
    private boolean verifying = false;     // a PIN check is running in the background
    private boolean lockedOut = false;     // too many wrong attempts: input disabled

    private final Runnable lockoutTicker = new Runnable() {
        @Override
        public void run() {
            long remaining = sessionManager.getPinLockoutRemainingMs();
            if (remaining <= 0L) {
                endLockout();
            } else {
                binding.tvPinStatus.setText(getString(R.string.app_lock_locked_out, formatRemaining(remaining)));
                mainHandler.postDelayed(this, 500L);
            }
        }
    };

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
                if (!lockedOut) binding.tvPinStatus.setText("");
            }
            @Override
            public void afterTextChanged(Editable s) {
                // Submit automatically only when the PIN has its full length. (Checking after every
                // digit would count every typed digit as a wrong attempt.)
                int pinLength = sessionManager.getSavedPinLength();
                int target = pinLength > 0 ? pinLength : 6;
                if (s.length() == target) {
                    submitPin(s.toString());
                }
            }
        });

        binding.etPin.setOnEditorActionListener((v, actionId, event) -> {
            String input = binding.etPin.getText().toString();
            if (input.length() < 4) {
                binding.tvPinStatus.setText(R.string.app_lock_pin_length_err);
            } else {
                submitPin(input);
            }
            return true;
        });

        showLockoutIfActive();
    }

    /** Checks the PIN in the background (the hash is deliberately slow) and applies attempt limits. */
    private void submitPin(@NonNull String input) {
        if (verifying || lockedOut || input.length() < 4) return;
        verifying = true;
        binding.etPin.setEnabled(false);
        AppExecutors.getInstance().diskIO().execute(() -> {
            SessionManager.PinAttempt attempt = sessionManager.attemptPin(input);
            AppExecutors.getInstance().mainThread().execute(() -> {
                verifying = false;
                if (isFinishing() || isDestroyed()) return;
                binding.etPin.setEnabled(true);
                switch (attempt.status) {
                    case OK:
                        unlockAndFinish();
                        break;
                    case LOCKED_OUT:
                        binding.etPin.setText("");
                        startLockout();
                        break;
                    case WRONG:
                        binding.etPin.setText("");
                        if (attempt.lockoutRemainingMs > 0L) {
                            startLockout();
                        } else if (attempt.attemptsLeft > 0 && attempt.attemptsLeft <= 2) {
                            binding.tvPinStatus.setText(getString(
                                    R.string.app_lock_wrong_pin_attempts_left, attempt.attemptsLeft));
                            focusPinInput();
                        } else {
                            binding.tvPinStatus.setText(R.string.app_lock_incorrect_pin);
                            focusPinInput();
                        }
                        break;
                    default:
                        binding.tvPinStatus.setText(R.string.app_lock_incorrect_pin);
                        break;
                }
            });
        });
    }

    private void showLockoutIfActive() {
        if (sessionManager.getPinLockoutRemainingMs() > 0L) {
            startLockout();
        }
    }

    private void startLockout() {
        lockedOut = true;
        binding.etPin.setEnabled(false);
        mainHandler.removeCallbacks(lockoutTicker);
        mainHandler.post(lockoutTicker);
    }

    private void endLockout() {
        lockedOut = false;
        mainHandler.removeCallbacks(lockoutTicker);
        binding.tvPinStatus.setText("");
        binding.etPin.setEnabled(true);
        focusPinInput();
    }

    @NonNull
    private static String formatRemaining(long ms) {
        long totalSeconds = (ms + 999L) / 1000L;
        return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!lockedOut) showLockoutIfActive();
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
        binding.etPin.setText("");
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
package com.abdussamadcodes.tutorsync.ui.account;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.databinding.ActivityPinLockBinding;
import com.abdussamadcodes.tutorsync.security.BiometricAuthHelper;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;

public class PinLockActivity extends BaseActivity {
    private ActivityPinLockBinding binding;
    // In Phase 3, this will read from Account Settings. For now, it's a mock check.
    private final String MOCK_SAVED_PIN = "1234";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPinLockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.pinLockRoot);

        if (BiometricAuthHelper.isBiometricReady(this)) {
            binding.btnBiometric.setVisibility(View.VISIBLE);
            promptFingerprint();
        }

        binding.btnBiometric.setOnClickListener(v -> promptFingerprint());

        binding.etPin.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= 4) {
                    if (s.toString().equals(MOCK_SAVED_PIN)) {
                        unlockAndFinish();
                    } else if (s.length() == 4) {
                        binding.etPin.setError("Incorrect PIN");
                    }
                }
            }
        });
    }

    private void promptFingerprint() {
        BiometricAuthHelper.promptBiometric(this, "TutorSync Locked", "Authenticate to continue", new BiometricAuthHelper.BiometricCallback() {
            @Override
            public void onSuccess() {
                unlockAndFinish();
            }

            @Override
            public void onError(@NonNull String errString) {
                // Ignore, user can just type the PIN
            }
        });
    }

    private void unlockAndFinish() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        // Prevent backing out of the lock screen without unlocking
        moveTaskToBack(true);
    }
}
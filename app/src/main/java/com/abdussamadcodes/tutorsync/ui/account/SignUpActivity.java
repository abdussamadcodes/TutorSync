package com.abdussamadcodes.tutorsync.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.GoogleAuthHelper;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivityAuthBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.security.PasswordHasher;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.common.ButtonLoading;
import com.abdussamadcodes.tutorsync.ui.main.MainActivity;
import com.google.firebase.auth.FirebaseUser;

import java.util.UUID;

public class SignUpActivity extends BaseActivity {

    private ActivityAuthBinding binding;
    private LocalAccountStore accountStore;
    private SessionManager sessionManager;
    private ButtonLoading btnLoading;
    private ButtonLoading btnGoogleLoading;
    private boolean isPasswordValid = false;
    private boolean doPasswordsMatch = false;

    private final ActivityResultLauncher<Intent> googleFallbackLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                GoogleAuthHelper.handleFallbackIntentResult(this, result.getData(), createGoogleAuthCallback());
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.authRoot);

        accountStore = new LocalAccountStore(this);
        sessionManager = new SessionManager(this);
        btnLoading = new ButtonLoading(binding.btnPrimaryAction);
        btnGoogleLoading = new ButtonLoading(binding.btnGoogleAuth);

        setupUI();
        setupListeners();
    }

    private void setupUI() {
        binding.tvHeading.setText(R.string.auth2_sign_up_heading);
        binding.tvSubheading.setText(R.string.auth2_sign_up_subheading);
        binding.btnPrimaryAction.setText(R.string.auth_action_sign_up);
        binding.btnToggleAuth.setText(R.string.auth_prompt_have_account);

        binding.btnBack.setVisibility(View.VISIBLE);
        binding.tilFullName.setVisibility(View.VISIBLE);
        binding.tilConfirmPassword.setVisibility(View.VISIBLE);
        binding.llPasswordRules.setVisibility(View.VISIBLE);
        binding.tvMatchIndicator.setVisibility(View.VISIBLE);

        binding.btnForgotPassword.setVisibility(View.GONE);

        evaluatePasswordRules("");
        evaluateMatchIndicator();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> navigateToSignIn());
        binding.btnToggleAuth.setOnClickListener(v -> navigateToSignIn());
        binding.btnPrimaryAction.setOnClickListener(v -> attemptSignUp());
        binding.btnGoogleAuth.setOnClickListener(v -> startGoogleSignIn());

        binding.etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                evaluatePasswordRules(s.toString());
                evaluateMatchIndicator();
            }
        });

        binding.etConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                evaluateMatchIndicator();
            }
        });
    }

    @NonNull
    private GoogleAuthHelper.AuthCallback createGoogleAuthCallback() {
        return new GoogleAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(@NonNull Account localAccount, @NonNull FirebaseUser firebaseUser) {
                btnGoogleLoading.setLoading(false);
                binding.btnPrimaryAction.setEnabled(true);
                sessionManager.login(localAccount.getId());
                startActivity(new Intent(SignUpActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onFallbackIntentRequired(@NonNull Intent signInIntent) {
                googleFallbackLauncher.launch(signInIntent);
            }

            @Override
            public void onCancelled() {
                btnGoogleLoading.setLoading(false);
                binding.btnPrimaryAction.setEnabled(true);
            }

            @Override
            public void onError(@NonNull String message) {
                btnGoogleLoading.setLoading(false);
                binding.btnPrimaryAction.setEnabled(true);
                showMessage(binding.getRoot(), message);
            }
        };
    }

    private void startGoogleSignIn() {
        binding.btnPrimaryAction.setEnabled(false);
        btnGoogleLoading.setLoading(true, getString(R.string.auth2_google_signing_in));

        GoogleAuthHelper.signInWithGoogle(this, createGoogleAuthCallback());
    }

    private void navigateToSignIn() {
        startActivity(new Intent(this, SignInActivity.class));
        finish();
    }

    private void evaluatePasswordRules(String pass) {
        boolean hasLength = pass.length() >= 8;
        boolean hasUpperLower = pass.matches(".*[A-Z]..*") || (pass.matches(".*[A-Z].*") && pass.matches(".*[a-z].*"));
        boolean hasDigit = pass.matches(".*[0-9].*");
        boolean hasSpecial = pass.matches(".*[^A-Za-z0-9].*");

        updateRuleRow(binding.ivRuleLength, binding.tvRuleLength, hasLength);
        updateRuleRow(binding.ivRuleUpperLower, binding.tvRuleUpperLower, hasUpperLower);
        updateRuleRow(binding.ivRuleDigit, binding.tvRuleDigit, hasDigit);
        updateRuleRow(binding.ivRuleSpecial, binding.tvRuleSpecial, hasSpecial);

        int score = (hasLength ? 1 : 0) + (hasUpperLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        isPasswordValid = (score == 4);

        binding.progressStrength.setProgressCompat(score * 25, true);

        if (score <= 1) {
            binding.progressStrength.setIndicatorColor(ContextCompat.getColor(this, R.color.ts_error));
            binding.tvStrengthLabel.setText(R.string.auth2_pw_strength_weak);
            binding.tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.ts_error));
        } else if (score == 2) {
            binding.progressStrength.setIndicatorColor(ContextCompat.getColor(this, R.color.ts_warning));
            binding.tvStrengthLabel.setText(R.string.auth2_pw_strength_fair);
            binding.tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.ts_warning));
        } else if (score == 3) {
            binding.progressStrength.setIndicatorColor(ContextCompat.getColor(this, R.color.ts_info));
            binding.tvStrengthLabel.setText(R.string.auth2_pw_strength_good);
            binding.tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.ts_info));
        } else {
            binding.progressStrength.setIndicatorColor(ContextCompat.getColor(this, R.color.ts_success));
            binding.tvStrengthLabel.setText(R.string.auth2_pw_strength_strong);
            binding.tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.ts_success));
        }
    }

    private void updateRuleRow(ImageView icon, TextView label, boolean isMet) {
        int colorRes = isMet ? R.color.ts_success : R.color.ts_error;
        int iconRes = isMet ? R.drawable.ms_check : R.drawable.ms_close;

        icon.setImageResource(iconRes);
        icon.setColorFilter(ContextCompat.getColor(this, colorRes));
        label.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private void evaluateMatchIndicator() {
        String pass = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";
        String confirm = binding.etConfirmPassword.getText() != null ? binding.etConfirmPassword.getText().toString() : "";

        if (confirm.isEmpty()) {
            binding.tvMatchIndicator.setVisibility(View.GONE);
            doPasswordsMatch = false;
            return;
        }

        binding.tvMatchIndicator.setVisibility(View.VISIBLE);
        doPasswordsMatch = pass.equals(confirm);

        if (doPasswordsMatch) {
            binding.tvMatchIndicator.setText(R.string.auth2_pw_match_ok);
            binding.tvMatchIndicator.setTextColor(ContextCompat.getColor(this, R.color.ts_success));
        } else {
            binding.tvMatchIndicator.setText(R.string.auth2_pw_match_err);
            binding.tvMatchIndicator.setTextColor(ContextCompat.getColor(this, R.color.ts_error));
        }
    }

    private void attemptSignUp() {
        String fullName = binding.etFullName.getText() != null ? binding.etFullName.getText().toString().trim() : "";
        String username = binding.etUsername.getText() != null ? binding.etUsername.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";

        binding.tilFullName.setError(null);
        binding.tilUsername.setError(null);
        binding.tilPassword.setError(null);

        if (fullName.isEmpty()) {
            binding.tilFullName.setError(getString(R.string.auth2_err_full_name_required));
            return;
        }
        if (username.isEmpty()) {
            binding.tilUsername.setError(getString(R.string.auth2_err_username_required));
            return;
        }
        if (!username.matches("^[a-zA-Z0-9]+$")) {
            binding.tilUsername.setError(getString(R.string.auth_err_invalid_username));
            return;
        }
        if (!isPasswordValid) {
            binding.tilPassword.setError(getString(R.string.auth_err_empty));
            return;
        }
        if (!doPasswordsMatch) {
            binding.tilConfirmPassword.setError(getString(R.string.auth_err_password_mismatch));
            return;
        }

        btnLoading.setLoading(true, getString(R.string.auth2_action_creating_account));
        binding.btnGoogleAuth.setEnabled(false);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (accountStore.getAccountByUsername(username) != null) {
                btnLoading.setLoading(false);
                binding.btnGoogleAuth.setEnabled(true);
                binding.tilUsername.setError(getString(R.string.auth_err_user_exists));
                return;
            }

            try {
                String salt = PasswordHasher.generateSalt();
                String hash = PasswordHasher.hashPassword(password, salt);

                Account newAccount = new Account(
                        UUID.randomUUID().toString(),
                        fullName,
                        username,
                        hash,
                        salt
                );

                if (accountStore.saveAccount(newAccount)) {
                    sessionManager.login(newAccount.getId());
                    startActivity(new Intent(SignUpActivity.this, MainActivity.class));
                    finish();
                } else {
                    btnLoading.setLoading(false);
                    binding.btnGoogleAuth.setEnabled(true);
                    showMessage(binding.getRoot(), R.string.auth_err_hashing);
                }
            } catch (Exception e) {
                btnLoading.setLoading(false);
                binding.btnGoogleAuth.setEnabled(true);
                showMessage(binding.getRoot(), R.string.auth_err_hashing);
            }
        }, 300);
    }
}
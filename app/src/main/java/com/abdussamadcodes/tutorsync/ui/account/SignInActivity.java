package com.abdussamadcodes.tutorsync.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivityAuthBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.security.PasswordHasher;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.common.ButtonLoading;
import com.abdussamadcodes.tutorsync.ui.main.MainActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SignInActivity extends BaseActivity {

    private ActivityAuthBinding binding;
    private LocalAccountStore accountStore;
    private SessionManager sessionManager;
    private ButtonLoading btnLoading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.authRoot);

        accountStore = new LocalAccountStore(this);
        sessionManager = new SessionManager(this);
        btnLoading = new ButtonLoading(binding.btnPrimaryAction);

        setupUI();
        setupListeners();
    }

    private void setupUI() {
        binding.tvHeading.setText(R.string.auth2_sign_in_heading);
        binding.tvSubheading.setText(R.string.auth2_sign_in_subheading);
        binding.btnPrimaryAction.setText(R.string.auth_action_sign_in);
        binding.btnToggleAuth.setText(R.string.auth_prompt_no_account);

        // Hide Sign Up specific elements
        binding.btnBack.setVisibility(View.GONE);
        binding.tilFullName.setVisibility(View.GONE);
        binding.tilConfirmPassword.setVisibility(View.GONE);
        binding.llPasswordRules.setVisibility(View.GONE);
        binding.tvMatchIndicator.setVisibility(View.GONE);
    }

    private void setupListeners() {
        binding.btnPrimaryAction.setOnClickListener(v -> attemptSignIn());

        binding.btnToggleAuth.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });

        binding.btnForgotPassword.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.auth2_forgot_password_title)
                        .setMessage(R.string.auth2_forgot_password_body)
                        .setPositiveButton(R.string.auth2_forgot_password_ok, null)
                        .show()
        );
    }

    private void attemptSignIn() {
        String username = binding.etUsername.getText() != null ? binding.etUsername.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";

        binding.tilUsername.setError(null);
        binding.tilPassword.setError(null);

        if (username.isEmpty()) {
            binding.tilUsername.setError(getString(R.string.auth2_err_username_required));
            return;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.auth2_err_password_required));
            return;
        }

        btnLoading.setLoading(true, getString(R.string.auth2_action_signing_in));

        // Offload slightly to allow ripple and loading state to render
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Account account = accountStore.getAccountByUsername(username);
            if (account != null) {
                try {
                    String calculatedHash = PasswordHasher.hashPassword(password, account.getSalt());
                    if (calculatedHash.equals(account.getPasswordHash())) {
                        sessionManager.login(account.getId());
                        startActivity(new Intent(SignInActivity.this, MainActivity.class));
                        finish();
                        return;
                    }
                } catch (Exception e) {
                    // Fallthrough to generic error below
                }
            }

            btnLoading.setLoading(false);
            showMessage(binding.getRoot(), R.string.auth_err_invalid_credentials);
        }, 300);
    }
}
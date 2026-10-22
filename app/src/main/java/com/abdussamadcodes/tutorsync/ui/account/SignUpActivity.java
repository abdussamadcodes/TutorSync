package com.abdussamadcodes.tutorsync.ui.account;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivitySignUpBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.security.PasswordHasher;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.main.MainActivity;
import java.util.UUID;

public class SignUpActivity extends BaseActivity {
    private ActivitySignUpBinding binding;
    private LocalAccountStore accountStore;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.signUpRoot);

        accountStore = new LocalAccountStore(this);
        sessionManager = new SessionManager(this);

        binding.btnGoToSignIn.setOnClickListener(v -> finish());
        binding.btnSignUp.setOnClickListener(v -> attemptSignUp());
    }

    private void attemptSignUp() {
        String fullName = binding.etFullName.getText().toString().trim();
        String username = binding.etUsername.getText().toString().trim();
        String password = binding.etPassword.getText().toString();
        String confirm = binding.etConfirmPassword.getText().toString();

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showMessage(binding.getRoot(), R.string.auth_err_empty);
            return;
        }

        // Username Rule: Only letters and numbers (no spaces, no symbols)
        if (!username.matches("^[a-zA-Z0-9]+$")) {
            showMessage(binding.getRoot(), R.string.auth_err_invalid_username);
            return;
        }

        // Premium Dynamic Password Checks
        if (password.length() < 8) {
            showMessage(binding.getRoot(), R.string.auth_err_pass_length);
            return;
        }
        if (!password.matches(".*[A-Z].*")) {
            showMessage(binding.getRoot(), R.string.auth_err_pass_upper);
            return;
        }
        if (!password.matches(".*[a-z].*")) {
            showMessage(binding.getRoot(), R.string.auth_err_pass_lower);
            return;
        }
        if (!password.matches(".*[0-9].*")) {
            showMessage(binding.getRoot(), R.string.auth_err_pass_digit);
            return;
        }
        if (!password.matches(".*[@#$%^&+=!*()~_\\-].*")) {
            showMessage(binding.getRoot(), R.string.auth_err_pass_special);
            return;
        }

        if (!password.equals(confirm)) {
            showMessage(binding.getRoot(), R.string.auth_err_password_mismatch);
            return;
        }

        try {
            String salt = PasswordHasher.generateSalt();
            String hash = PasswordHasher.hashPassword(password, salt);
            String accountId = UUID.randomUUID().toString();

            Account newAccount = new Account(accountId, fullName, username, hash, salt);
            if (accountStore.saveAccount(newAccount)) {
                sessionManager.login(accountId);
                startActivity(new Intent(this, MainActivity.class));
                finishAffinity();
            } else {
                showMessage(binding.getRoot(), R.string.auth_err_user_exists);
            }
        } catch (Exception e) {
            showMessage(binding.getRoot(), R.string.auth_err_hashing);
        }
    }
}
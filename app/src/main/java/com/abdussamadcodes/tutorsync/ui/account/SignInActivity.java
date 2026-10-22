package com.abdussamadcodes.tutorsync.ui.account;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivitySignInBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.security.PasswordHasher;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.main.MainActivity;

public class SignInActivity extends BaseActivity {
    private ActivitySignInBinding binding;
    private LocalAccountStore accountStore;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.signInRoot);

        accountStore = new LocalAccountStore(this);
        sessionManager = new SessionManager(this);

        binding.btnGoToSignUp.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
        binding.btnSignIn.setOnClickListener(v -> attemptSignIn());
    }

    private void attemptSignIn() {
        String username = binding.etUsername.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage(binding.getRoot(), R.string.auth_err_empty);
            return;
        }

        Account account = accountStore.getAccountByUsername(username);
        if (account == null) {
            showMessage(binding.getRoot(), R.string.auth_err_invalid_credentials);
            return;
        }

        try {
            String calculatedHash = PasswordHasher.hashPassword(password, account.getSalt());
            if (calculatedHash.equals(account.getPasswordHash())) {
                sessionManager.login(account.getId());
                startActivity(new Intent(this, MainActivity.class));
                finishAffinity();
            } else {
                showMessage(binding.getRoot(), R.string.auth_err_invalid_credentials);
            }
        } catch (Exception e) {
            showMessage(binding.getRoot(), R.string.auth_err_hashing);
        }
    }
}
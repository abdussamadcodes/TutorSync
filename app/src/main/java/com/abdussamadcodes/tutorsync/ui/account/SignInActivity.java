package com.abdussamadcodes.tutorsync.ui.account;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import com.abdussamadcodes.tutorsync.util.AppExecutors;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;

public class SignInActivity extends BaseActivity {

    private ActivityAuthBinding binding;
    private LocalAccountStore accountStore;
    private SessionManager sessionManager;
    private ButtonLoading btnLoading;
    private ButtonLoading btnGoogleLoading;

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

        binding.btnGoogleAuth.setOnClickListener(v -> startGoogleSignIn());

        binding.btnToggleAuth.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });

        binding.btnForgotPassword.setOnClickListener(v -> showSmartPasswordRecoveryDialog());
    }

    private void startGoogleSignIn() {
        binding.tilUsername.setError(null);
        binding.tilPassword.setError(null);
        binding.btnPrimaryAction.setEnabled(false);
        btnGoogleLoading.setLoading(true, getString(R.string.auth2_google_signing_in));

        GoogleAuthHelper.signInWithGoogle(this, new GoogleAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(@NonNull Account localAccount, @NonNull FirebaseUser firebaseUser) {
                btnGoogleLoading.setLoading(false);
                binding.btnPrimaryAction.setEnabled(true);
                sessionManager.login(localAccount.getId());
                startActivity(new Intent(SignInActivity.this, MainActivity.class));
                finish();
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
        });
    }

    private void attemptSignIn() {
        String identifier = binding.etUsername.getText() != null
                ? binding.etUsername.getText().toString().trim()
                : "";
        String password = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString()
                : "";

        binding.tilUsername.setError(null);
        binding.tilPassword.setError(null);

        if (identifier.isEmpty()) {
            binding.tilUsername.setError(getString(R.string.auth2_err_username_required));
            return;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.auth2_err_password_required));
            return;
        }

        btnLoading.setLoading(true, getString(R.string.auth2_action_signing_in));
        binding.btnGoogleAuth.setEnabled(false);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Account account = accountStore.findAccountByUsernameOrEmail(identifier);
            if (account != null) {
                if (LocalAccountStore.isGoogleManagedAccount(account)) {
                    btnLoading.setLoading(false);
                    binding.btnGoogleAuth.setEnabled(true);
                    showMessage(binding.getRoot(), R.string.auth2_err_google_managed_use_button);
                    return;
                }
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
            binding.btnGoogleAuth.setEnabled(true);
            showMessage(binding.getRoot(), R.string.auth_err_invalid_credentials);
        }, 300);
    }

    private void showSmartPasswordRecoveryDialog() {
        String prefilled = binding.etUsername.getText() != null
                ? binding.etUsername.getText().toString().trim()
                : "";

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (20 * getResources().getDisplayMetrics().density);
        int padTop = (int) (8 * getResources().getDisplayMetrics().density);
        container.setPadding(padH, padTop, padH, 0);

        TextInputLayout tilInput = new TextInputLayout(
                this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        tilInput.setHint(getString(R.string.auth2_recovery_hint));
        TextInputEditText etInput = new TextInputEditText(tilInput.getContext());
        etInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        etInput.setSingleLine(true);
        if (!prefilled.isEmpty()) {
            etInput.setText(prefilled);
        }
        tilInput.addView(etInput);
        container.addView(tilInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auth2_forgot_password_title)
                .setMessage(R.string.auth2_recovery_prompt)
                .setView(container)
                .setPositiveButton(R.string.auth2_recovery_check_action, (d, which) -> {
                    String query = etInput.getText() != null ? etInput.getText().toString().trim() : "";
                    evaluateRecoveryForIdentifier(query);
                })
                .setNeutralButton(R.string.auth2_continue_with_google, (d, which) -> startGoogleSignIn())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void evaluateRecoveryForIdentifier(@NonNull String identifier) {
        if (identifier.isEmpty()) {
            showOfflineRecoveryExplanation();
            return;
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            Account found = accountStore.findAccountByUsernameOrEmail(identifier);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (found != null && LocalAccountStore.isGoogleManagedAccount(found)) {
                    String label = found.getEmail() != null ? found.getEmail() : found.getUsername();
                    showGoogleAccountRecoveryDialog(label);
                    return;
                }

                String candidateEmail = null;
                if (found != null && found.getEmail() != null && Patterns.EMAIL_ADDRESS.matcher(found.getEmail().trim()).matches()) {
                    candidateEmail = found.getEmail().trim();
                } else if (Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                    candidateEmail = identifier;
                }

                if (candidateEmail != null) {
                    showEmailPasswordResetDialog(candidateEmail);
                } else {
                    showOfflineRecoveryExplanation();
                }
            });
        });
    }

    private void showGoogleAccountRecoveryDialog(@NonNull String accountLabel) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auth2_recovery_google_title)
                .setMessage(getString(R.string.auth2_recovery_google_msg, accountLabel))
                .setPositiveButton(R.string.auth2_recovery_google_sign_in_now, (d, w) -> startGoogleSignIn())
                .setNeutralButton(R.string.auth2_recovery_google_open_web, (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://accounts.google.com/signin/recovery")));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showEmailPasswordResetDialog(@NonNull String email) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auth2_recovery_email_title)
                .setMessage(getString(R.string.auth2_recovery_email_msg, email))
                .setPositiveButton(R.string.auth2_recovery_send_email, (d, w) -> {
                    GoogleAuthHelper.sendPasswordResetEmail(email, new GoogleAuthHelper.ResetEmailCallback() {
                        @Override
                        public void onSent() {
                            if (isFinishing() || isDestroyed()) return;
                            showMessage(binding.getRoot(), getString(R.string.auth2_recovery_email_sent, email));
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            if (isFinishing() || isDestroyed()) return;
                            showMessage(binding.getRoot(), message);
                        }
                    });
                })
                .setNeutralButton(R.string.auth2_continue_with_google, (d, w) -> startGoogleSignIn())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showOfflineRecoveryExplanation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auth2_forgot_password_title)
                .setMessage(R.string.auth2_forgot_password_body)
                .setPositiveButton(R.string.auth2_forgot_password_ok, null)
                .setNeutralButton(R.string.auth2_continue_with_google, (d, w) -> startGoogleSignIn())
                .show();
    }
}
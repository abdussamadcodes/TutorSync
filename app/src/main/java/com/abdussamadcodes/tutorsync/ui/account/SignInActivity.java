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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
        binding.tvHeading.setText(R.string.auth2_sign_in_heading);
        binding.tvSubheading.setText(R.string.auth2_sign_in_subheading);
        binding.btnPrimaryAction.setText(R.string.auth_action_sign_in);
        binding.btnToggleAuth.setText(R.string.auth_prompt_no_account);

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

    @NonNull
    private GoogleAuthHelper.AuthCallback createGoogleAuthCallback() {
        return new GoogleAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(@NonNull Account localAccount, @NonNull FirebaseUser firebaseUser) {
                btnGoogleLoading.setLoading(false);
                binding.btnPrimaryAction.setEnabled(true);
                sessionManager.login(localAccount.getId());
                startActivity(new Intent(SignInActivity.this, MainActivity.class));
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
        binding.tilUsername.setError(null);
        binding.tilPassword.setError(null);
        binding.btnPrimaryAction.setEnabled(false);
        btnGoogleLoading.setLoading(true, getString(R.string.auth2_google_signing_in));

        GoogleAuthHelper.signInWithGoogle(this, createGoogleAuthCallback());
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

            // 1. Check local SQLite password hash first (works 100% offline)
            if (account != null && !LocalAccountStore.isGoogleManagedAccount(account)) {
                try {
                    String calculatedHash = PasswordHasher.hashPassword(password, account.getSalt());
                    if (calculatedHash.equals(account.getPasswordHash())) {
                        sessionManager.login(account.getId());
                        startActivity(new Intent(SignInActivity.this, MainActivity.class));
                        finish();
                        return;
                    }
                } catch (Exception ignored) {
                }
            }

            // 2. If identifier or linked account has an email, check Firebase Auth!
            // This handles signing in with a NEW password after a Firebase Password Reset email,
            // or signing in on a freshly installed device, and syncs the new password to SQLite.
            String emailToTry = null;
            if (Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                emailToTry = identifier;
            } else if (account != null && account.getEmail() != null
                    && Patterns.EMAIL_ADDRESS.matcher(account.getEmail().trim()).matches()) {
                emailToTry = account.getEmail().trim();
            }

            if (emailToTry != null) {
                GoogleAuthHelper.signInWithEmailAndSyncLocal(this, emailToTry, password,
                        new GoogleAuthHelper.EmailSignInCallback() {
                            @Override
                            public void onSuccess(@NonNull Account localAccount,
                                                  @NonNull FirebaseUser firebaseUser,
                                                  boolean isEmailVerified) {
                                btnLoading.setLoading(false);
                                binding.btnGoogleAuth.setEnabled(true);
                                sessionManager.login(localAccount.getId());
                                startActivity(new Intent(SignInActivity.this, MainActivity.class));
                                finish();
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                btnLoading.setLoading(false);
                                binding.btnGoogleAuth.setEnabled(true);
                                if (account != null && LocalAccountStore.isGoogleManagedAccount(account)) {
                                    showMessage(binding.getRoot(), R.string.auth2_err_google_managed_use_button);
                                } else {
                                    showMessage(binding.getRoot(), R.string.auth_err_invalid_credentials);
                                }
                            }
                        });
                return;
            }

            if (account != null && LocalAccountStore.isGoogleManagedAccount(account)) {
                btnLoading.setLoading(false);
                binding.btnGoogleAuth.setEnabled(true);
                showMessage(binding.getRoot(), R.string.auth2_err_google_managed_use_button);
                return;
            }

            btnLoading.setLoading(false);
            binding.btnGoogleAuth.setEnabled(true);
            showMessage(binding.getRoot(), R.string.auth_err_invalid_credentials);
        }, 250);
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
                .setPositiveButton(R.string.auth2_recovery_send_email, (d, which) -> {
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

                String candidateEmail = null;
                if (found != null && found.getEmail() != null
                        && Patterns.EMAIL_ADDRESS.matcher(found.getEmail().trim()).matches()) {
                    candidateEmail = found.getEmail().trim();
                } else if (Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                    candidateEmail = identifier;
                }

                // If the account was created only via Google Sign-In, offer Google Sign-In OR sending a Firebase password email
                if (found != null && LocalAccountStore.isGoogleManagedAccount(found)) {
                    String label = found.getEmail() != null ? found.getEmail() : found.getUsername();
                    showGoogleAccountRecoveryDialog(label);
                    return;
                }

                // Send password reset email directly if we have an email
                if (candidateEmail != null) {
                    final String targetEmail = candidateEmail;
                    GoogleAuthHelper.sendPasswordResetEmail(targetEmail, new GoogleAuthHelper.ResetEmailCallback() {
                        @Override
                        public void onSent() {
                            if (isFinishing() || isDestroyed()) return;
                            new MaterialAlertDialogBuilder(SignInActivity.this)
                                    .setTitle("Password Reset Email Sent")
                                    .setMessage("We sent a password reset link to:\n" + targetEmail
                                            + "\n\n1. Open the email (check your Spam folder too) and set your new password.\n"
                                            + "2. Return here and sign in with your email and new password — TutorSync will automatically update your offline password.")
                                    .setPositiveButton(R.string.auth2_forgot_password_ok, null)
                                    .show();
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            if (isFinishing() || isDestroyed()) return;
                            showMessage(binding.getRoot(), message);
                        }
                    });
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

    private void showOfflineRecoveryExplanation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auth2_forgot_password_title)
                .setMessage(R.string.auth2_forgot_password_body)
                .setPositiveButton(R.string.auth2_forgot_password_ok, null)
                .setNeutralButton(R.string.auth2_continue_with_google, (d, w) -> startGoogleSignIn())
                .show();
    }
}
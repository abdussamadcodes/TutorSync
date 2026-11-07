package com.abdussamadcodes.tutorsync.account;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.security.PasswordHasher;
import com.abdussamadcodes.tutorsync.util.AppExecutors;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public final class GoogleAuthHelper {

    private static final String TAG = "GoogleAuthHelper";
    private static final long FIREBASE_TIMEOUT_MS = 12000L;

    public interface AuthCallback {
        void onSuccess(@NonNull Account localAccount, @NonNull FirebaseUser firebaseUser);
        void onFallbackIntentRequired(@NonNull Intent signInIntent);
        void onCancelled();
        void onError(@NonNull String message);
    }

    public interface EmailSignUpCallback {
        void onRegistered(@NonNull Account localAccount, @NonNull FirebaseUser firebaseUser, boolean verificationEmailSent);
        void onError(@NonNull String message);
    }

    public interface EmailSignInCallback {
        void onSuccess(@NonNull Account localAccount, @NonNull FirebaseUser firebaseUser, boolean isEmailVerified);
        void onError(@NonNull String message);
    }

    public interface VerificationStatusCallback {
        void onResult(boolean isVerified);
        void onError(@NonNull String message);
    }

    public interface ResetEmailCallback {
        void onSent();
        void onError(@NonNull String message);
    }

    public interface PasswordUpdateCallback {
        void onUpdated();
        void onError(@NonNull String message);
    }

    private GoogleAuthHelper() {
    }

    @Nullable
    public static String getWebClientId(@NonNull Context context) {
        int resId = context.getResources().getIdentifier(
                "default_web_client_id", "string", context.getPackageName());
        if (resId == 0) {
            return null;
        }
        String value = context.getString(resId).trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Returns true if the local account is a Google-managed account OR if the currently signed-in
     * FirebaseUser for this account authenticated via google.com without an email/password provider.
     */
    public static boolean isGoogleSignInAccount(@Nullable Account account) {
        if (account == null) {
            return false;
        }
        if (LocalAccountStore.isGoogleManagedAccount(account)) {
            return true;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            String fbEmail = user.getEmail().trim().toLowerCase(Locale.ROOT);
            String acctEmail = account.getEmail() != null ? account.getEmail().trim().toLowerCase(Locale.ROOT) : "";
            String acctUser = account.getUsername().trim().toLowerCase(Locale.ROOT);
            if (fbEmail.equals(acctEmail) || fbEmail.equals(acctUser)) {
                boolean hasGoogle = false;
                boolean hasPassword = false;
                for (UserInfo info : user.getProviderData()) {
                    if (GoogleAuthProvider.PROVIDER_ID.equals(info.getProviderId())) {
                        hasGoogle = true;
                    } else if (EmailAuthProvider.PROVIDER_ID.equals(info.getProviderId())) {
                        hasPassword = true;
                    }
                }
                if (hasGoogle && !hasPassword) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Re-authenticates the Email/Password user in Firebase and updates their Firebase password
     * so clearing app data will recognize the new password on future sign-ins.
     */
    public static void updateFirebasePassword(
            @NonNull String email,
            @NonNull String currentPassword,
            @NonNull String newPassword,
            @NonNull PasswordUpdateCallback callback) {

        final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        // Case 1: Active FirebaseUser matches the account email -> reauthenticate & updatePassword
        if (currentUser != null && currentUser.getEmail() != null
                && currentUser.getEmail().trim().equalsIgnoreCase(normalizedEmail)) {
            AuthCredential credential = EmailAuthProvider.getCredential(normalizedEmail, currentPassword);
            currentUser.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
                if (!reauthTask.isSuccessful()) {
                    Exception e = reauthTask.getException();
                    mainHandler.post(() -> callback.onError(
                            e != null && e.getMessage() != null
                                    ? e.getMessage()
                                    : "Could not verify current password with Firebase."));
                    return;
                }
                currentUser.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                    if (updateTask.isSuccessful()) {
                        mainHandler.post(callback::onUpdated);
                    } else {
                        Exception e = updateTask.getException();
                        mainHandler.post(() -> callback.onError(
                                e != null && e.getMessage() != null
                                        ? e.getMessage()
                                        : "Failed to update password in Firebase."));
                    }
                });
            });
            return;
        }

        // Case 2: No active Firebase session (e.g. signed in offline earlier) -> sign in with current password & update
        auth.signInWithEmailAndPassword(normalizedEmail, currentPassword)
                .addOnCompleteListener(signInTask -> {
                    if (!signInTask.isSuccessful()) {
                        Exception e = signInTask.getException();
                        mainHandler.post(() -> callback.onError(
                                e != null && e.getMessage() != null
                                        ? e.getMessage()
                                        : "Could not connect to Firebase to update your password. Check your internet connection."));
                        return;
                    }
                    FirebaseUser signedInUser = auth.getCurrentUser();
                    if (signedInUser == null) {
                        mainHandler.post(() -> callback.onError("Firebase session unavailable."));
                        return;
                    }
                    signedInUser.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                        if (updateTask.isSuccessful()) {
                            mainHandler.post(callback::onUpdated);
                        } else {
                            Exception e = updateTask.getException();
                            mainHandler.post(() -> callback.onError(
                                    e != null && e.getMessage() != null
                                            ? e.getMessage()
                                            : "Failed to update password in Firebase."));
                        }
                    });
                });
    }

    public static void signInWithGoogle(@NonNull Activity activity, @NonNull AuthCallback callback) {
        final String webClientId = getWebClientId(activity);
        if (webClientId == null) {
            callback.onError("Missing default_web_client_id. Check google-services.json in app/.");
            return;
        }

        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .requestProfile()
                    .build();

            GoogleSignInClient client = GoogleSignIn.getClient(activity, gso);

            final AtomicBoolean launched = new AtomicBoolean(false);
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            Runnable launchPicker = () -> {
                if (launched.compareAndSet(false, true)) {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        callback.onCancelled();
                        return;
                    }
                    callback.onFallbackIntentRequired(client.getSignInIntent());
                }
            };

            mainHandler.postDelayed(launchPicker, 1000L);

            client.signOut().addOnCompleteListener(t -> {
                mainHandler.removeCallbacks(launchPicker);
                launchPicker.run();
            });
        } catch (Exception ex) {
            Log.e(TAG, "Failed to start Google Sign-In", ex);
            callback.onError(ex.getMessage() != null ? ex.getMessage() : "Google Sign-In unavailable.");
        }
    }

    public static void handleFallbackIntentResult(
            @NonNull Activity activity,
            @Nullable Intent data,
            @NonNull AuthCallback callback) {
        if (data == null) {
            callback.onCancelled();
            return;
        }
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                authenticateWithFirebase(activity, account.getIdToken(), callback);
            } else {
                callback.onError("Google Sign-In did not return an ID token.");
            }
        } catch (ApiException e) {
            int code = e.getStatusCode();
            Log.e(TAG, "GoogleSignIn ApiException statusCode=" + code, e);
            if (code == 12501) {
                callback.onCancelled();
            } else if (code == 10) {
                callback.onError("Developer Error (code 10): Verify debug SHA-1 & SHA-256 are added in Firebase Console and re-download google-services.json.");
            } else if (code == 7) {
                callback.onError("Network error (code 7): Check your internet connection.");
            } else {
                callback.onError("Google Sign-In failed (code " + code + ").");
            }
        } catch (Exception e) {
            Log.e(TAG, "GoogleSignIn unexpected exception", e);
            callback.onError(e.getMessage() != null ? e.getMessage() : "Google Sign-In failed.");
        }
    }

    private static void authenticateWithFirebase(
            @NonNull Activity activity,
            @NonNull String idToken,
            @NonNull AuthCallback callback) {

        final Context appContext = activity.getApplicationContext();
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        Runnable timeoutGuard = () -> {
            if (finished.compareAndSet(false, true)) {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    callback.onError("Firebase connection timed out. Check internet access.");
                }
            }
        };
        mainHandler.postDelayed(timeoutGuard, FIREBASE_TIMEOUT_MS);

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance()
                .signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    mainHandler.removeCallbacks(timeoutGuard);
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }

                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        Log.e(TAG, "Firebase signInWithCredential failed", ex);
                        mainHandler.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            callback.onError(ex != null && ex.getMessage() != null
                                    ? ex.getMessage()
                                    : "Firebase authentication failed.");
                        });
                        return;
                    }

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                        mainHandler.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            callback.onError("Google account did not provide an email address.");
                        });
                        return;
                    }

                    final String uid = user.getUid();
                    final String email = user.getEmail();
                    final String displayName = user.getDisplayName();

                    AppExecutors.getInstance().diskIO().execute(() -> {
                        Account localAccount = null;
                        String dbError = null;
                        try {
                            LocalAccountStore store = new LocalAccountStore(appContext);
                            localAccount = store.getOrCreateGoogleAccount(uid, email, displayName);
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating/linking local account", e);
                            dbError = e.getMessage();
                        }
                        final Account resolvedAccount = localAccount;
                        final String finalDbError = dbError;
                        mainHandler.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) {
                                return;
                            }
                            if (resolvedAccount != null) {
                                callback.onSuccess(resolvedAccount, user);
                            } else {
                                callback.onError(finalDbError != null
                                        ? "Local DB error: " + finalDbError
                                        : "Could not create or link local tutor account.");
                            }
                        });
                    });
                });
    }

    public static void registerEmailPasswordAccount(
            @NonNull Activity activity,
            @NonNull String fullName,
            @NonNull String email,
            @NonNull String password,
            @NonNull EmailSignUpCallback callback) {

        final Context appContext = activity.getApplicationContext();
        final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        mainHandler.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            callback.onError(ex != null && ex.getMessage() != null
                                    ? ex.getMessage()
                                    : "Could not register email with Firebase. Make sure Email/Password is enabled in Firebase Console.");
                        });
                        return;
                    }

                    FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (firebaseUser == null) {
                        mainHandler.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            callback.onError("Firebase user creation failed.");
                        });
                        return;
                    }

                    firebaseUser.sendEmailVerification().addOnCompleteListener(verifyTask -> {
                        final boolean sent = verifyTask.isSuccessful();
                        AppExecutors.getInstance().diskIO().execute(() -> {
                            Account savedAccount = null;
                            try {
                                LocalAccountStore store = new LocalAccountStore(appContext);
                                String salt = PasswordHasher.generateSalt();
                                String hash = PasswordHasher.hashPassword(password, salt);

                                Account existing = store.findAccountByUsernameOrEmail(normalizedEmail);
                                if (existing != null) {
                                    store.updatePassword(existing.getId(), hash, salt);
                                    store.updateProfile(
                                            existing.getId(),
                                            fullName,
                                            existing.getPhone(),
                                            normalizedEmail,
                                            existing.getAddress(),
                                            existing.getDob()
                                    );
                                    savedAccount = store.getAccountById(existing.getId());
                                } else {
                                    String deterministicId = UUID.nameUUIDFromBytes(
                                            ("GOOGLE_ACCT:" + normalizedEmail).getBytes(StandardCharsets.UTF_8)
                                    ).toString();
                                    Account newAccount = new Account(
                                            deterministicId,
                                            fullName,
                                            normalizedEmail,
                                            hash,
                                            salt,
                                            null,
                                            normalizedEmail,
                                            null,
                                            null,
                                            null
                                    );
                                    if (store.saveAccount(newAccount)) {
                                        savedAccount = newAccount;
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed saving email account locally", e);
                            }

                            final Account finalAccount = savedAccount;
                            mainHandler.post(() -> {
                                if (activity.isFinishing() || activity.isDestroyed()) return;
                                if (finalAccount != null) {
                                    callback.onRegistered(finalAccount, firebaseUser, sent);
                                } else {
                                    callback.onError("Failed to save local account.");
                                }
                            });
                        });
                    });
                });
    }

    public static void signInWithEmailAndSyncLocal(
            @NonNull Activity activity,
            @NonNull String email,
            @NonNull String password,
            @NonNull EmailSignInCallback callback) {

        final Context appContext = activity.getApplicationContext();
        final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(normalizedEmail, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        mainHandler.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            callback.onError(ex != null && ex.getMessage() != null
                                    ? ex.getMessage()
                                    : "Invalid email or password.");
                        });
                        return;
                    }

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) {
                        mainHandler.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            callback.onError("Authentication failed.");
                        });
                        return;
                    }

                    user.reload().addOnCompleteListener(reloadTask -> {
                        final boolean verified = user.isEmailVerified();
                        AppExecutors.getInstance().diskIO().execute(() -> {
                            Account localAccount = null;
                            try {
                                LocalAccountStore store = new LocalAccountStore(appContext);
                                String newSalt = PasswordHasher.generateSalt();
                                String newHash = PasswordHasher.hashPassword(password, newSalt);

                                localAccount = store.findAccountByUsernameOrEmail(normalizedEmail);
                                if (localAccount != null) {
                                    store.updatePassword(localAccount.getId(), newHash, newSalt);
                                    localAccount = store.getAccountById(localAccount.getId());
                                } else {
                                    localAccount = store.getOrCreateGoogleAccount(
                                            user.getUid(), normalizedEmail, user.getDisplayName());
                                    if (localAccount != null) {
                                        store.updatePassword(localAccount.getId(), newHash, newSalt);
                                        localAccount = store.getAccountById(localAccount.getId());
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error syncing local password after Firebase sign-in", e);
                            }

                            final Account resolved = localAccount;
                            mainHandler.post(() -> {
                                if (activity.isFinishing() || activity.isDestroyed()) return;
                                if (resolved != null) {
                                    callback.onSuccess(resolved, user, verified);
                                } else {
                                    callback.onError("Could not sync local account.");
                                }
                            });
                        });
                    });
                });
    }

    public static void checkEmailVerified(@NonNull VerificationStatusCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("No active Firebase user.");
            return;
        }
        user.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(user.isEmailVerified());
            } else {
                Exception e = task.getException();
                callback.onError(e != null && e.getMessage() != null
                        ? e.getMessage()
                        : "Could not check verification status.");
            }
        });
    }

    public static void resendVerificationEmail(@NonNull ResetEmailCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("No active Firebase user.");
            return;
        }
        user.sendEmailVerification().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onSent();
            } else {
                Exception e = task.getException();
                callback.onError(e != null && e.getMessage() != null
                        ? e.getMessage()
                        : "Could not resend verification email.");
            }
        });
    }

    public static void sendPasswordResetEmail(@NonNull String email, @NonNull ResetEmailCallback callback) {
        String cleanEmail = email.trim();
        if (cleanEmail.isEmpty()) {
            callback.onError("Email address is required.");
            return;
        }
        FirebaseAuth.getInstance()
                .sendPasswordResetEmail(cleanEmail)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSent();
                    } else {
                        Exception e = task.getException();
                        callback.onError(e != null && e.getMessage() != null
                                ? e.getMessage()
                                : "Unable to send password reset email.");
                    }
                });
    }

    public static void signOut(@NonNull Context context) {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {
        }
        try {
            String webClientId = getWebClientId(context);
            if (webClientId != null) {
                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build();
                GoogleSignIn.getClient(context.getApplicationContext(), gso).signOut();
            }
        } catch (Exception ignored) {
        }
        try {
            CredentialManager credentialManager = CredentialManager.create(context.getApplicationContext());
            credentialManager.clearCredentialStateAsync(
                    new ClearCredentialStateRequest(),
                    new CancellationSignal(),
                    ContextCompat.getMainExecutor(context.getApplicationContext()),
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override
                        public void onResult(Void result) {
                        }

                        @Override
                        public void onError(@NonNull ClearCredentialException e) {
                        }
                    }
            );
        } catch (Exception ignored) {
        }
    }
}
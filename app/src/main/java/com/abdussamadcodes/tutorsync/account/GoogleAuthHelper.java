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
import com.abdussamadcodes.tutorsync.util.AppExecutors;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

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

    public interface ResetEmailCallback {
        void onSent();
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
     * Launches the Google account chooser reliably every time (including immediately after
     * clearing app storage or signing out) without hanging on CredentialManager's HiddenActivity.
     */
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

            // Clear any stale cached GoogleSignIn state with a 1.5s max wait so it never hangs,
            // then launch the account chooser intent directly.
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

            // Watchdog in case signOut() IPC takes > 1s after clearing app data
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
                // User pressed back / dismissed the account picker
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
                    callback.onError("Firebase connection timed out. Check emulator internet access.");
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
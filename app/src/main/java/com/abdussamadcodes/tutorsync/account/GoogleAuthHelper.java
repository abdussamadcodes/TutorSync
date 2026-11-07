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
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.util.AppExecutors;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public final class GoogleAuthHelper {

    private static final String TAG = "GoogleAuthHelper";
    private static final long CREDENTIAL_MANAGER_TIMEOUT_MS = 8000L;

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
     * Launches Google Sign-In via Credential Manager (single-step button flow) with a watchdog
     * fallback to GoogleSignInClient if Credential Manager stalls after clearing app data.
     */
    public static void signInWithGoogle(@NonNull Activity activity, @NonNull AuthCallback callback) {
        final String webClientId = getWebClientId(activity);
        if (webClientId == null) {
            callback.onError("Missing default_web_client_id. Verify google-services.json has Google Sign-In enabled.");
            return;
        }

        final CredentialManager credentialManager = CredentialManager.create(activity);
        final CancellationSignal cancellationSignal = new CancellationSignal();
        final AtomicBoolean completed = new AtomicBoolean(false);
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        // Watchdog: if Credential Manager binder stalls after app-data clear, switch to Play Services Intent
        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                Log.w(TAG, "CredentialManager timed out; falling back to GoogleSignInClient intent.");
                try {
                    cancellationSignal.cancel();
                } catch (Exception ignored) {
                }
                launchFallbackSignInIntent(activity, webClientId, callback);
            }
        };
        mainHandler.postDelayed(timeoutRunnable, CREDENTIAL_MANAGER_TIMEOUT_MS);

        GetSignInWithGoogleOption signInWithGoogleOption =
                new GetSignInWithGoogleOption.Builder(webClientId).build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build();

        credentialManager.getCredentialAsync(
                activity,
                request,
                cancellationSignal,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        mainHandler.removeCallbacks(timeoutRunnable);
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        handleCredentialResult(activity, result.getCredential(), callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        mainHandler.removeCallbacks(timeoutRunnable);
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        Log.w(TAG, "CredentialManager onError: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        if (e instanceof GetCredentialCancellationException) {
                            callback.onCancelled();
                        } else {
                            // Fall back cleanly to Google Play Services Sign-In Intent
                            launchFallbackSignInIntent(activity, webClientId, callback);
                        }
                    }
                }
        );
    }

    private static void launchFallbackSignInIntent(
            @NonNull Activity activity,
            @NonNull String webClientId,
            @NonNull AuthCallback callback) {
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            GoogleSignInClient client = GoogleSignIn.getClient(activity, gso);
            client.signOut().addOnCompleteListener(t -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    callback.onCancelled();
                    return;
                }
                callback.onFallbackIntentRequired(client.getSignInIntent());
            });
        } catch (Exception ex) {
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
            // 12501 = SIGN_IN_CANCELLED, 12502 = SIGN_IN_CURRENTLY_IN_PROGRESS
            if (e.getStatusCode() == 12501) {
                callback.onCancelled();
            } else {
                Log.e(TAG, "GoogleSignIn ApiException code=" + e.getStatusCode(), e);
                callback.onError("Google Sign-In error (code " + e.getStatusCode() + "). Check SHA-1 in Firebase Console.");
            }
        } catch (Exception e) {
            callback.onError(e.getMessage() != null ? e.getMessage() : "Google Sign-In failed.");
        }
    }

    private static void handleCredentialResult(
            @NonNull Activity activity,
            @NonNull Credential credential,
            @NonNull AuthCallback callback) {

        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(credential.getData());
                String idToken = googleIdTokenCredential.getIdToken();
                authenticateWithFirebase(activity, idToken, callback);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse GoogleIdTokenCredential", e);
                callback.onError("Invalid Google ID token credential.");
            }
        } else {
            callback.onError("Unsupported credential type returned.");
        }
    }

    /**
     * Authenticates with Firebase WITHOUT passing Activity as a lifecycle-bound listener,
     * preventing the callback from being dropped during overlay window transitions.
     */
    private static void authenticateWithFirebase(
            @NonNull Activity activity,
            @NonNull String idToken,
            @NonNull AuthCallback callback) {

        final Context appContext = activity.getApplicationContext();
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        FirebaseAuth.getInstance()
                .signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        Log.e(TAG, "Firebase signInWithCredential failed", ex);
                        AppExecutors.getInstance().mainThread().execute(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            callback.onError(ex != null && ex.getMessage() != null
                                    ? ex.getMessage()
                                    : "Firebase authentication failed.");
                        });
                        return;
                    }

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                        AppExecutors.getInstance().mainThread().execute(() -> {
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
                        try {
                            LocalAccountStore store = new LocalAccountStore(appContext);
                            localAccount = store.getOrCreateGoogleAccount(uid, email, displayName);
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating/linking local account", e);
                        }
                        final Account resolvedAccount = localAccount;
                        AppExecutors.getInstance().mainThread().execute(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) {
                                return;
                            }
                            if (resolvedAccount != null) {
                                callback.onSuccess(resolvedAccount, user);
                            } else {
                                callback.onError("Could not create or link local tutor account.");
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
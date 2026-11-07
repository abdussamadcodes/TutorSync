package com.abdussamadcodes.tutorsync.ui.main;

import android.app.Activity;
import android.text.Editable;
import android.text.InputType;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.util.BackupRestoreManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.util.Date;

public final class BackupDialogs {

    public interface PasswordCallback {
        void onPassword(@NonNull char[] password);
    }

    private static final int MIN_PASSWORD_LENGTH = 8;

    private BackupDialogs() {
    }

    public static void askNewPassword(@NonNull Activity activity,
                                      @NonNull PasswordCallback onPassword,
                                      @NonNull Runnable onNoPassword) {
        LinearLayout layout = verticalLayout(activity);
        TextInputLayout tilPw = passwordField(activity, activity.getString(R.string.backup_password_hint));
        TextInputLayout tilConfirm = passwordField(activity, activity.getString(R.string.backup_password_confirm_hint));
        layout.addView(tilPw, matchWrap());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(activity, 12);
        layout.addView(tilConfirm, lp);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.backup_export_password_title)
                .setMessage(R.string.backup_export_password_message)
                .setView(layout)
                .setPositiveButton(R.string.backup_export_confirm_action, null)
                .setNegativeButton(R.string.common_cancel, null)
                .setNeutralButton(R.string.backup_export_without_password, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                tilPw.setError(null);
                tilConfirm.setError(null);
                char[] pw = chars(tilPw);
                char[] confirm = chars(tilConfirm);
                if (pw.length < MIN_PASSWORD_LENGTH) {
                    tilPw.setError(activity.getString(R.string.backup_err_password_short, MIN_PASSWORD_LENGTH));
                    wipe(pw, confirm);
                    return;
                }
                if (!java.util.Arrays.equals(pw, confirm)) {
                    tilConfirm.setError(activity.getString(R.string.backup_err_password_mismatch));
                    wipe(pw, confirm);
                    return;
                }
                wipe(confirm);
                dialog.dismiss();
                onPassword.onPassword(pw);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.backup_plain_warning_title)
                        .setMessage(R.string.backup_plain_warning_msg)
                        .setPositiveButton(R.string.backup_plain_warning_action, (x, w) -> onNoPassword.run())
                        .setNegativeButton(R.string.common_cancel, null)
                        .show();
            });
        });
        dialog.show();
    }

    public static void askExistingPassword(@NonNull Activity activity,
                                           boolean previousAttemptWasWrong,
                                           @NonNull PasswordCallback onPassword,
                                           @Nullable Runnable onCancel) {
        LinearLayout layout = verticalLayout(activity);
        TextInputLayout til = passwordField(activity, activity.getString(R.string.backup_password_hint));
        if (previousAttemptWasWrong) {
            til.setError(activity.getString(R.string.backup_err_wrong_password));
        }
        layout.addView(til, matchWrap());

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.backup_import_password_title)
                .setMessage(R.string.backup_import_password_message)
                .setView(layout)
                .setPositiveButton(R.string.backup_import_password_action, null)
                .setNegativeButton(R.string.common_cancel, (d, w) -> {
                    if (onCancel != null) onCancel.run();
                })
                .setOnCancelListener(d -> {
                    if (onCancel != null) onCancel.run();
                })
                .create();
        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    char[] pw = chars(til);
                    if (pw.length == 0) {
                        til.setError(activity.getString(R.string.backup_err_password_required));
                        return;
                    }
                    dialog.dismiss();
                    onPassword.onPassword(pw);
                }));
        dialog.show();
    }

    public static void chooseRestoreSource(@NonNull Activity activity,
                                           long latestSafetyBackupTime,
                                           @NonNull Runnable onPickFile,
                                           @NonNull Runnable onUseSafetyBackup) {
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(latestSafetyBackupTime));
        String[] items = new String[]{
                activity.getString(R.string.backup_source_file),
                activity.getString(R.string.backup_source_safety, when)
        };

        // Add radio buttons and an OK button
        final int[] selectedIndex = {0};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.backup_source_title)
                .setSingleChoiceItems(items, 0, (d, which) -> selectedIndex[0] = which)
                .setPositiveButton(R.string.common_ok, (d, which) -> {
                    if (selectedIndex[0] == 0) onPickFile.run();
                    else onUseSafetyBackup.run();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    public static void confirmRestore(@NonNull Activity activity,
                                      @NonNull BackupRestoreManager.ParsedBackup backup,
                                      @NonNull String activeAccountId,
                                      @NonNull Runnable onConfirm) {
        String when = backup.exportedAt > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(backup.exportedAt))
                : activity.getString(R.string.backup_unknown_date);
        String source = backup.isFromAccount(activeAccountId)
                ? activity.getString(R.string.backup_source_same_account)
                : activity.getString(R.string.backup_source_other_account);

        StringBuilder msg = new StringBuilder();
        msg.append(activity.getString(R.string.backup_confirm_restore_details,
                when, source, backup.students, backup.classes, backup.invoices, backup.transactions));
        if (!backup.isFromAccount(activeAccountId)) {
            msg.append("\n\n").append(activity.getString(R.string.backup_confirm_restore_other_account));
        }
        msg.append("\n\n").append(activity.getString(R.string.backup_confirm_restore_merge));

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.backup_confirm_restore_title)
                .setMessage(msg.toString())
                .setPositiveButton(R.string.backup_confirm_restore_action, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private static LinearLayout verticalLayout(@NonNull Activity a) {
        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(a, 20);
        layout.setPadding(pad, dp(a, 8), pad, 0);
        return layout;
    }

    private static TextInputLayout passwordField(@NonNull Activity a, @NonNull String hint) {
        TextInputLayout til = new TextInputLayout(a, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);
        return til;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(@NonNull Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density);
    }

    private static char[] chars(@NonNull TextInputLayout til) {
        Editable e = til.getEditText() != null ? til.getEditText().getText() : null;
        if (e == null) return new char[0];
        char[] out = new char[e.length()];
        e.getChars(0, e.length(), out, 0);
        return out;
    }

    private static void wipe(char[]... arrays) {
        for (char[] a : arrays) {
            java.util.Arrays.fill(a, '\0');
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.abdussamadcodes.tutorsync.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Standard confirm dialogs. Destructive ones use a red confirm button.
 * <pre>
 * ConfirmDialog.destructive(this, titleRes, "Delete Rahim?", R.string.common_delete, () -> delete());
 * ConfirmDialog.discardChanges(this, this::finish);
 * </pre>
 */
public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    /** Red confirm button, for delete / remove. */
    public static void destructive(@NonNull Context context, @StringRes int title, @NonNull CharSequence message,
                                   @StringRes int confirmLabel, @NonNull Runnable onConfirm) {
        build(new MaterialAlertDialogBuilder(context,
                        R.style.ThemeOverlay_TutorSync_MaterialAlertDialog_Destructive),
                title, message, confirmLabel, R.string.common_cancel, onConfirm);
    }

    /** Normal confirm button. */
    public static void confirm(@NonNull Context context, @StringRes int title, @NonNull CharSequence message,
                               @StringRes int confirmLabel, @NonNull Runnable onConfirm) {
        build(new MaterialAlertDialogBuilder(context), title, message, confirmLabel, R.string.common_cancel, onConfirm);
    }

    /** "Discard changes?" shown when leaving a form with unsaved input. */
    public static void discardChanges(@NonNull Context context, @NonNull Runnable onDiscard) {
        build(new MaterialAlertDialogBuilder(context,
                        R.style.ThemeOverlay_TutorSync_MaterialAlertDialog_Destructive),
                R.string.common_unsaved_title, context.getString(R.string.common_unsaved_message),
                R.string.common_discard, R.string.common_keep_editing, onDiscard);
    }

    private static void build(MaterialAlertDialogBuilder builder, @StringRes int title, CharSequence message,
                              @StringRes int confirmLabel, @StringRes int cancelLabel, Runnable onConfirm) {
        builder.setTitle(title)
                .setMessage(message)
                .setNegativeButton(cancelLabel, null)
                .setPositiveButton(confirmLabel, (d, w) -> onConfirm.run())
                .show();
    }
}
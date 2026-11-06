package com.abdussamadcodes.tutorsync.ui.common;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * Snackbar helpers. {@link #undo} implements "delete with Undo": hide the item, show the snackbar,
 * and only really delete when the snackbar closes without Undo.
 * <pre>
 * adapter.hide(item);
 * Snacks.undo(root, getString(R.string.common_deleted), () -> adapter.restore(item), () -> repo.delete(item));
 * </pre>
 */
public final class Snacks {

    private Snacks() {
    }

    public static void show(@NonNull View anchor, @NonNull CharSequence message) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show();
    }

    /**
     * @param onUndo   put the item back in the UI
     * @param onCommit run when the snackbar ends WITHOUT Undo (really delete now); may be null
     */
    public static void undo(@NonNull View anchor, @NonNull CharSequence message,
                            @NonNull Runnable onUndo, @Nullable Runnable onCommit) {
        Snackbar bar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG);
        bar.setAction(R.string.common_undo, v -> onUndo.run());
        bar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION && onCommit != null) onCommit.run();
            }
        });
        bar.show();
    }
}
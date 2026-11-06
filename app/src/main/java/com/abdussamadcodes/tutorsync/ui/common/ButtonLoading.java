package com.abdussamadcodes.tutorsync.ui.common;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;

/**
 * Loading state for a MaterialButton: shows a small spinner in front of the label and blocks taps
 * (the button keeps its normal colours). Restores icon, label and tint when loading ends.
 *
 * <pre>
 * ButtonLoading signIn = new ButtonLoading(binding.signInButton);
 * signIn.setLoading(true, "Signing in...");   // start (use a string resource in real code)
 * signIn.setLoading(false);                                   // stop
 * </pre>
 */
public final class ButtonLoading {

    private final MaterialButton button;
    private boolean loading;
    private CharSequence savedText;
    private Drawable savedIcon;
    private ColorStateList savedIconTint;
    private int savedIconGravity;

    public ButtonLoading(@NonNull MaterialButton button) {
        this.button = button;
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean on) {
        setLoading(on, null);
    }

    /** @param loadingText optional label to show while loading (for example "Signing in..."). */
    public void setLoading(boolean on, @Nullable CharSequence loadingText) {
        if (on == loading) {
            if (on && loadingText != null) button.setText(loadingText);
            return;
        }
        loading = on;
        if (on) {
            savedText = button.getText();
            savedIcon = button.getIcon();
            savedIconTint = button.getIconTint();
            savedIconGravity = button.getIconGravity();

            int color = button.getCurrentTextColor();
            CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(
                    button.getContext(), null, 0,
                    com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall);
            spec.indicatorColors = new int[]{color};
            IndeterminateDrawable<CircularProgressIndicatorSpec> spinner =
                    IndeterminateDrawable.createCircularDrawable(button.getContext(), spec);

            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setIconTint(ColorStateList.valueOf(color));
            button.setIcon(spinner);
            if (loadingText != null) button.setText(loadingText);
            button.setClickable(false);
        } else {
            button.setIcon(savedIcon);
            button.setIconTint(savedIconTint);
            button.setIconGravity(savedIconGravity);
            button.setText(savedText);
            button.setClickable(true);
            savedIcon = null;
            savedIconTint = null;
            savedText = null;
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;

import com.abdussamadcodes.tutorsync.R;

/**
 * Titled group of form fields. Children declared in XML are spaced 16dp apart automatically.
 * <pre>
 * &lt;...ui.common.FormSectionView app:tsTitle="Contact" app:tsIcon="@drawable/ms_call"
 *     app:tsMessage="How we reach the family"&gt;
 *     ...TextInputLayouts...
 * &lt;/...FormSectionView&gt;
 * </pre>
 */
public class FormSectionView extends LinearLayout {

    public FormSectionView(Context context) {
        this(context, null);
    }

    public FormSectionView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FormSectionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        int gap = getResources().getDimensionPixelSize(R.dimen.space_md);
        GradientDrawable spacer = new GradientDrawable();
        spacer.setSize(1, gap);
        setDividerDrawable(spacer);
        setShowDividers(SHOW_DIVIDER_MIDDLE);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.FormSectionView, defStyleAttr, 0);
        try {
            String title = ta.getString(R.styleable.FormSectionView_tsTitle);
            String message = ta.getString(R.styleable.FormSectionView_tsMessage);
            int iconRes = ta.getResourceId(R.styleable.FormSectionView_tsIcon, 0);
            if (title != null || message != null) addHeader(title, message, iconRes);
        } finally {
            ta.recycle();
        }
    }

    private void addHeader(@Nullable String title, @Nullable String message, int iconRes) {
        Context context = getContext();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        int onVariant = ContextCompat.getColor(context, R.color.ts_on_surface_variant);

        if (title != null) {
            TextView titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextAppearance(R.style.TextAppearance_TutorSync_SectionHeader);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            if (iconRes != 0) {
                Drawable icon = ContextCompat.getDrawable(context, iconRes);
                titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                titleView.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.space_xs));
                TextViewCompat.setCompoundDrawableTintList(titleView, android.content.res.ColorStateList.valueOf(onVariant));
            }
            ViewCompat.setAccessibilityHeading(titleView, true);
            box.addView(titleView);
        }
        if (message != null) {
            TextView messageView = new TextView(context);
            messageView.setText(message);
            messageView.setTextAppearance(R.style.TextAppearance_TutorSync_Meta);
            box.addView(messageView);
        }
        addView(box, 0);
    }
}
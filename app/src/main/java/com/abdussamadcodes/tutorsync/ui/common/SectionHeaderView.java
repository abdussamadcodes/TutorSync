package com.abdussamadcodes.tutorsync.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ViewSectionHeaderBinding;

/**
 * Section title with an optional text action on the right ("See all").
 * <pre>
 * &lt;...ui.common.SectionHeaderView app:tsTitle="Classes" /&gt;
 * header.setAction("See all", v -> openList());
 * </pre>
 */
public class SectionHeaderView extends LinearLayout {

    private final ViewSectionHeaderBinding binding;

    public SectionHeaderView(Context context) {
        this(context, null);
    }

    public SectionHeaderView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SectionHeaderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.min_touch_target));
        binding = ViewSectionHeaderBinding.inflate(LayoutInflater.from(context), this);
        ViewCompat.setAccessibilityHeading(binding.sectionTitle, true);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SectionHeaderView, defStyleAttr, 0);
        try {
            setTitle(ta.getString(R.styleable.SectionHeaderView_tsTitle));
            String action = ta.getString(R.styleable.SectionHeaderView_tsActionText);
            if (action != null) binding.sectionAction.setText(action);
        } finally {
            ta.recycle();
        }
    }

    public void setTitle(@Nullable CharSequence title) {
        binding.sectionTitle.setText(title);
    }

    public void setTitle(@StringRes int titleRes) {
        setTitle(getContext().getText(titleRes));
    }

    /** Shows the right-hand action. A null label or listener hides it. */
    public void setAction(@Nullable CharSequence label, @Nullable OnClickListener listener) {
        binding.sectionAction.setText(label);
        binding.sectionAction.setOnClickListener(listener);
        binding.sectionAction.setVisibility(label == null || listener == null ? View.GONE : View.VISIBLE);
    }

    public void setAction(@StringRes int labelRes, @Nullable OnClickListener listener) {
        setAction(getContext().getText(labelRes), listener);
    }
}
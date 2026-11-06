package com.abdussamadcodes.tutorsync.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ViewEmptyStateBinding;

/**
 * Centered empty state: tonal icon circle, title, message and an optional action button.
 *
 * <pre>
 * &lt;com.abdussamadcodes.tutorsync.ui.common.EmptyStateView
 *     android:id="@+id/emptyState"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     app:tsIcon="@drawable/ms_group" app:tsTitle="@string/..." app:tsMessage="@string/..." />
 * </pre>
 *
 * Show it with {@code setVisibility} in place of the list; use {@link #setAction} to add a button.
 */
public class EmptyStateView extends LinearLayout {

    private final ViewEmptyStateBinding binding;

    public EmptyStateView(Context context) {
        this(context, null);
    }

    public EmptyStateView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmptyStateView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        int pad = getResources().getDimensionPixelSize(R.dimen.space_lg);
        setPadding(pad, pad, pad, pad);
        binding = ViewEmptyStateBinding.inflate(LayoutInflater.from(context), this);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.EmptyStateView, defStyleAttr, 0);
        try {
            setIcon(ta.getResourceId(R.styleable.EmptyStateView_tsIcon, 0));
            setTitle(ta.getString(R.styleable.EmptyStateView_tsTitle));
            setMessage(ta.getString(R.styleable.EmptyStateView_tsMessage));
            String action = ta.getString(R.styleable.EmptyStateView_tsActionText);
            if (action != null) binding.emptyAction.setText(action);
        } finally {
            ta.recycle();
        }
    }

    /** Pass 0 to hide the icon. */
    public void setIcon(@DrawableRes int iconRes) {
        binding.emptyIconCard.setVisibility(iconRes == 0 ? GONE : VISIBLE);
        if (iconRes != 0) binding.emptyIcon.setImageResource(iconRes);
    }

    public void setTitle(@Nullable CharSequence title) {
        binding.emptyTitle.setText(title);
        binding.emptyTitle.setVisibility(title == null || title.length() == 0 ? GONE : VISIBLE);
    }

    public void setTitle(@StringRes int titleRes) {
        setTitle(getContext().getText(titleRes));
    }

    public void setMessage(@Nullable CharSequence message) {
        binding.emptyMessage.setText(message);
        binding.emptyMessage.setVisibility(message == null || message.length() == 0 ? GONE : VISIBLE);
    }

    public void setMessage(@StringRes int messageRes) {
        setMessage(getContext().getText(messageRes));
    }

    /** Shows the action button with a label and click handler. Pass a null label to hide it. */
    public void setAction(@Nullable CharSequence label, @Nullable OnClickListener listener) {
        binding.emptyAction.setText(label);
        binding.emptyAction.setOnClickListener(listener);
        binding.emptyAction.setVisibility(label == null || listener == null ? View.GONE : View.VISIBLE);
    }

    public void setAction(@StringRes int labelRes, @Nullable OnClickListener listener) {
        setAction(getContext().getText(labelRes), listener);
    }
}
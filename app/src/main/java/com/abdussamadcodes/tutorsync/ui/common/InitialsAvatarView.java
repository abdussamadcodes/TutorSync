package com.abdussamadcodes.tutorsync.ui.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.util.Initials;

/**
 * Round avatar showing a person's initials on a soft tonal colour. The colour is chosen from the
 * name, so a person always gets the same colour. Decorative: hidden from screen readers (the name
 * is read from the row itself).
 *
 * <pre>
 * &lt;com.abdussamadcodes.tutorsync.ui.common.InitialsAvatarView
 *     android:layout_width="@dimen/avatar_md" android:layout_height="@dimen/avatar_md" /&gt;
 * avatar.setName(student.getName());
 * </pre>
 */
public class InitialsAvatarView extends AppCompatTextView {

    /** {background, foreground} pairs. Semantic colours (red, green) are avoided on purpose. */
    @ColorRes
    private static final int[][] PALETTE = {
            {R.color.ts_primary_container, R.color.ts_on_primary_container},
            {R.color.ts_tertiary_container, R.color.ts_on_tertiary_container},
            {R.color.ts_info_container, R.color.ts_on_info_container},
            {R.color.ts_secondary_container, R.color.ts_on_secondary_container},
            {R.color.ts_neutral_container, R.color.ts_on_neutral_container},
    };

    public InitialsAvatarView(Context context) {
        this(context, null);
    }

    public InitialsAvatarView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InitialsAvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTextAppearance(R.style.TextAppearance_TutorSync_TitleSmall);
        setGravity(Gravity.CENTER);
        setIncludeFontPadding(false);
        setMaxLines(1);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setName(null);
    }

    /** Sets the person's full name. Null or blank shows a neutral empty circle. */
    public void setName(@Nullable String name) {
        int[] pair = PALETTE[Initials.paletteIndex(name, PALETTE.length)];
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ContextCompat.getColor(getContext(), pair[0]));
        setBackground(circle);
        setTextColor(ContextCompat.getColor(getContext(), pair[1]));
        setText(Initials.of(name));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int size = Math.max(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Letters scale with the avatar so they always fit the circle.
        float textPx = Math.min(w, h) * 0.4f;
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
        TextViewCompat.setLineHeight(this, Math.round(textPx * 1.5f));
    }
}
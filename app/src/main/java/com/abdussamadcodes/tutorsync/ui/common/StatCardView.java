package com.abdussamadcodes.tutorsync.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ViewStatCardBinding;
import com.abdussamadcodes.tutorsync.util.AppFormat;
import com.google.android.material.card.MaterialCardView;

/**
 * Stat tile: small label (optional icon) above a big value.
 *
 * <pre>
 * &lt;...ui.common.StatCardView app:tsLabel="Received" app:tsTone="success" /&gt;
 * card.bindMoney(minorUnits, true);   // count-up animation, formatted by AppFormat
 * </pre>
 * Tones: surface (default), tonal, alert (amber = money due), error, success, hero (dashboard).
 */
public class StatCardView extends MaterialCardView {

    /** Colour set of a tile. All pairs are 4.5:1 or better. */
    public enum Tone {
        SURFACE(R.color.ts_surface, R.color.ts_on_surface, R.color.ts_on_surface_variant, true),
        TONAL(R.color.ts_primary_container, R.color.ts_on_primary_container, R.color.ts_on_primary_container, false),
        ALERT(R.color.ts_tertiary_container, R.color.ts_on_tertiary_container, R.color.ts_on_tertiary_container, false),
        ERROR(R.color.ts_error_container, R.color.ts_on_error_container, R.color.ts_on_error_container, false),
        SUCCESS(R.color.ts_success_container, R.color.ts_on_success_container, R.color.ts_on_success_container, false),
        HERO(R.color.ts_hero, R.color.ts_on_hero, R.color.ts_on_hero_muted, false);

        @ColorRes final int background;
        @ColorRes final int value;
        @ColorRes final int label;
        final boolean outlined;

        Tone(@ColorRes int background, @ColorRes int value, @ColorRes int label, boolean outlined) {
            this.background = background;
            this.value = value;
            this.label = label;
            this.outlined = outlined;
        }
    }

    private final ViewStatCardBinding binding;
    private ValueAnimator animator;
    private int shownMinor;
    private boolean hasMoney;

    public StatCardView(Context context) {
        this(context, null);
    }

    public StatCardView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public StatCardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        binding = ViewStatCardBinding.inflate(LayoutInflater.from(context), this);
        ViewCompat.setScreenReaderFocusable(this, true);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.StatCardView, defStyleAttr, 0);
        try {
            setLabel(ta.getString(R.styleable.StatCardView_tsLabel));
            setValue(ta.getString(R.styleable.StatCardView_tsValue));
            setIcon(ta.getResourceId(R.styleable.StatCardView_tsIcon, 0));
            Tone[] tones = Tone.values();
            int toneIndex = ta.getInt(R.styleable.StatCardView_tsTone, 0);
            setTone(tones[Math.max(0, Math.min(toneIndex, tones.length - 1))]);
        } finally {
            ta.recycle();
        }
    }

    public void setTone(@NonNull Tone tone) {
        Context c = getContext();
        setCardBackgroundColor(ContextCompat.getColor(c, tone.background));
        setStrokeWidth(tone.outlined ? getResources().getDimensionPixelSize(R.dimen.card_stroke_width) : 0);
        setStrokeColor(ContextCompat.getColor(c, R.color.ts_outline_variant));
        binding.statValue.setTextColor(ContextCompat.getColor(c, tone.value));
        int labelColor = ContextCompat.getColor(c, tone.label);
        binding.statLabel.setTextColor(labelColor);
        binding.statIcon.setImageTintList(ColorStateList.valueOf(labelColor));
    }

    public void setLabel(@Nullable CharSequence label) {
        binding.statLabel.setText(label);
        updateDescription();
    }

    public void setValue(@Nullable CharSequence value) {
        cancelAnimation();
        hasMoney = false;
        binding.statValue.setText(value);
        updateDescription();
    }

    /** Pass 0 to hide the icon. */
    public void setIcon(@DrawableRes int iconRes) {
        binding.statIcon.setVisibility(iconRes == 0 ? GONE : VISIBLE);
        if (iconRes != 0) binding.statIcon.setImageResource(iconRes);
    }

    /** Shows money from minor units (paisa); counts up from the last shown value when {@code animate}. */
    public void bindMoney(int minor, boolean animate) {
        cancelAnimation();
        AppFormat f = AppFormat.of(getContext());
        int from = hasMoney ? shownMinor : 0;
        hasMoney = true;
        shownMinor = minor;
        if (!animate || from == minor) {
            binding.statValue.setText(f.money(minor));
            updateDescription();
            return;
        }
        animator = ValueAnimator.ofInt(from, minor);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> binding.statValue.setText(f.money((int) a.getAnimatedValue())));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                binding.statValue.setText(f.money(minor));
                updateDescription();
            }
        });
        animator.start();
    }

    private void updateDescription() {
        CharSequence label = binding.statLabel.getText();
        CharSequence value = binding.statValue.getText();
        if (label.length() == 0 && value.length() == 0) return;
        setContentDescription(getContext().getString(R.string.stat_card_description, label, value));
    }

    private void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }
}
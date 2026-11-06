package com.abdussamadcodes.tutorsync.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.google.android.material.chip.Chip;

/**
 * One place that decides how every status looks: same tone and wording on every screen.
 *
 * Paid = success, Partial = warning, Unpaid / Overdue = error, Present = success,
 * Absent = error, Cancelled / Tutor absent = neutral, Makeup = info.
 *
 * Use with a chip declared as {@code style="@style/Widget.TutorSync.Chip.Status"}:
 * <pre>
 * StatusChipBinder.bindInvoice(binding.statusChip, invoice.getStatus());
 * </pre>
 * Colours come from ts_* tokens, so they follow light/dark automatically.
 */
public final class StatusChipBinder {

    /** Visual tone of a status chip. */
    public enum Tone {
        PRIMARY(R.color.ts_primary_container, R.color.ts_on_primary_container),
        SUCCESS(R.color.ts_success_container, R.color.ts_on_success_container),
        WARNING(R.color.ts_warning_container, R.color.ts_on_warning_container),
        ERROR(R.color.ts_error_container, R.color.ts_on_error_container),
        INFO(R.color.ts_info_container, R.color.ts_on_info_container),
        NEUTRAL(R.color.ts_neutral_container, R.color.ts_on_neutral_container);

        @ColorRes final int background;
        @ColorRes final int foreground;

        Tone(@ColorRes int background, @ColorRes int foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }

    private StatusChipBinder() {
    }

    /** Applies a tone and label to a read-only chip. */
    public static void bind(@NonNull Chip chip, @NonNull Tone tone, @NonNull CharSequence label) {
        Context context = chip.getContext();
        int fg = ContextCompat.getColor(context, tone.foreground);
        chip.setText(label);
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, tone.background)));
        chip.setTextColor(fg);
        chip.setChipIconTint(ColorStateList.valueOf(fg));
        chip.setChipStrokeWidth(0f);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setClickable(false);
        chip.setFocusable(false);
    }

    public static void bind(@NonNull Chip chip, @NonNull Tone tone, @StringRes int labelRes) {
        bind(chip, tone, chip.getContext().getString(labelRes));
    }

    /** Invoice status: PAID, PARTIAL, UNPAID, OVERDUE (see {@link Invoice}). */
    public static void bindInvoice(@NonNull Chip chip, String status) {
        if (Invoice.STATUS_PAID.equals(status)) {
            bind(chip, Tone.SUCCESS, R.string.status_paid);
        } else if (Invoice.STATUS_PARTIAL.equals(status)) {
            bind(chip, Tone.WARNING, R.string.status_partial);
        } else if (Invoice.STATUS_OVERDUE.equals(status)) {
            bind(chip, Tone.ERROR, R.string.status_overdue);
        } else if (Invoice.STATUS_UNPAID.equals(status)) {
            bind(chip, Tone.ERROR, R.string.status_unpaid);
        } else {
            bind(chip, Tone.NEUTRAL, status == null ? "" : status);
        }
    }

    /** Attendance status: PRESENT, STUDENT_ABSENT, TUTOR_ABSENT, CANCELLED_*, RESCHEDULED. */
    public static void bindAttendance(@NonNull Chip chip, String status) {
        if (AttendanceRecord.STATUS_PRESENT.equals(status)) {
            bind(chip, Tone.SUCCESS, R.string.status_present);
        } else if (AttendanceRecord.STATUS_STUDENT_ABSENT.equals(status)) {
            bind(chip, Tone.ERROR, R.string.status_absent);
        } else if (AttendanceRecord.STATUS_TUTOR_ABSENT.equals(status)) {
            bind(chip, Tone.NEUTRAL, R.string.status_tutor_absent);
        } else if (AttendanceRecord.STATUS_CANCELLED_BY_STUDENT.equals(status)
                || AttendanceRecord.STATUS_CANCELLED_BY_TUTOR.equals(status)) {
            bind(chip, Tone.NEUTRAL, R.string.status_cancelled);
        } else if (AttendanceRecord.STATUS_RESCHEDULED.equals(status)) {
            bind(chip, Tone.INFO, R.string.status_makeup_scheduled);
        } else {
            bind(chip, Tone.NEUTRAL, status == null ? "" : status);
        }
    }
}
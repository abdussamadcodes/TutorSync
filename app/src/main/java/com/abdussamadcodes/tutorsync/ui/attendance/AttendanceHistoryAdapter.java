package com.abdussamadcodes.tutorsync.ui.attendance;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ItemAttendanceHistoryBinding;
import com.abdussamadcodes.tutorsync.databinding.ItemAttendanceHistoryHeaderBinding;
import com.abdussamadcodes.tutorsync.model.AttendanceHistoryItem;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AttendanceHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(@NonNull AttendanceHistoryItem item);
    }

    /** One line of the list: either a day header or a class card. */
    public static final class Row {
        static final int TYPE_HEADER = 0;
        static final int TYPE_ITEM = 1;

        final int type;
        @Nullable final String headerTitle;
        @Nullable final String headerMeta;
        @Nullable final AttendanceHistoryItem item;

        private Row(int type, @Nullable String headerTitle, @Nullable String headerMeta,
                    @Nullable AttendanceHistoryItem item) {
            this.type = type;
            this.headerTitle = headerTitle;
            this.headerMeta = headerMeta;
            this.item = item;
        }

        public static Row header(@NonNull String title, @Nullable String meta) {
            return new Row(TYPE_HEADER, title, meta, null);
        }

        public static Row item(@NonNull AttendanceHistoryItem item) {
            return new Row(TYPE_ITEM, null, null, item);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnItemClickListener listener;

    public AttendanceHistoryAdapter(@NonNull OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == Row.TYPE_HEADER) {
            return new HeaderHolder(ItemAttendanceHistoryHeaderBinding.inflate(inflater, parent, false));
        }
        return new ItemHolder(ItemAttendanceHistoryBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row);
        } else if (holder instanceof ItemHolder && row.item != null) {
            ((ItemHolder) holder).bind(row.item, listener);
        }
    }

    // ------------------------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------------------------

    /** "4:00 PM" from minutes after midnight. */
    @NonNull
    public static String formatTime(int startMinutes) {
        int hourOfDay = (startMinutes / 60) % 24;
        int minute = startMinutes % 60;
        int h = hourOfDay % 12;
        if (h == 0) h = 12;
        return String.format(Locale.US, "%d:%02d %s", h, minute, hourOfDay >= 12 ? "PM" : "AM");
    }

    @NonNull
    public static String formatMoney(int amountMinor) {
        return String.format(Locale.US, "৳ %.2f", amountMinor / 100.0);
    }

    private static void tintPill(@NonNull TextView pill, @ColorInt int background, @ColorInt int text) {
        pill.setBackgroundTintList(ColorStateList.valueOf(background));
        pill.setTextColor(text);
    }

    // ------------------------------------------------------------------------------------
    // View holders
    // ------------------------------------------------------------------------------------

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemAttendanceHistoryHeaderBinding binding;

        HeaderHolder(@NonNull ItemAttendanceHistoryHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Row row) {
            binding.tvHeaderTitle.setText(row.headerTitle);
            if (row.headerMeta == null || row.headerMeta.isEmpty()) {
                binding.tvHeaderMeta.setVisibility(View.GONE);
            } else {
                binding.tvHeaderMeta.setVisibility(View.VISIBLE);
                binding.tvHeaderMeta.setText(row.headerMeta);
            }
        }
    }

    static final class ItemHolder extends RecyclerView.ViewHolder {
        private final ItemAttendanceHistoryBinding binding;

        ItemHolder(@NonNull ItemAttendanceHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull AttendanceHistoryItem item, @NonNull OnItemClickListener listener) {
            Context ctx = binding.getRoot().getContext();
            View root = binding.getRoot();

            int warning = ContextCompat.getColor(ctx, R.color.ts_warning);
            int warningContainer = ContextCompat.getColor(ctx, R.color.ts_warning_container);
            int success = ContextCompat.getColor(ctx, R.color.ts_success);
            int successContainer = ContextCompat.getColor(ctx, R.color.ts_success_container);
            int error = MaterialColors.getColor(root, com.google.android.material.R.attr.colorError);
            int errorContainer = MaterialColors.getColor(root, com.google.android.material.R.attr.colorErrorContainer);
            int onErrorContainer = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnErrorContainer);
            int primary = MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimary);
            int primaryContainer = MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimaryContainer);
            int onPrimaryContainer = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnPrimaryContainer);
            int surfaceVariant = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceVariant);
            int onSurfaceVariant = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant);
            int outline = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutline);

            binding.tvStudentName.setText(item.studentName);
            binding.tvSubject.setText(item.subjectSummary);
            binding.tvTime.setText(formatTime(item.startTimeMinutes));
            binding.tvDuration.setText(ctx.getString(R.string.attendance_duration_min, item.durationMinutes));

            // ---- status pill + accent ----
            int accent;
            if (item.needsAttendance) {
                binding.tvStatusPill.setText(R.string.attendance_label_needs);
                tintPill(binding.tvStatusPill, warningContainer, warning);
                accent = warning;
            } else {
                String st = item.status == null ? "" : item.status;
                switch (st) {
                    case AttendanceRecord.STATUS_PRESENT:
                        binding.tvStatusPill.setText(R.string.attendance_label_present);
                        tintPill(binding.tvStatusPill, successContainer, success);
                        accent = success;
                        break;
                    case AttendanceRecord.STATUS_STUDENT_ABSENT:
                        binding.tvStatusPill.setText(R.string.attendance_label_student_absent);
                        tintPill(binding.tvStatusPill, errorContainer, onErrorContainer);
                        accent = error;
                        break;
                    case AttendanceRecord.STATUS_TUTOR_ABSENT:
                        binding.tvStatusPill.setText(R.string.attendance_label_tutor_absent);
                        tintPill(binding.tvStatusPill, errorContainer, onErrorContainer);
                        accent = error;
                        break;
                    case AttendanceRecord.STATUS_CANCELLED_BY_STUDENT:
                        binding.tvStatusPill.setText(R.string.attendance_label_cancelled_student);
                        tintPill(binding.tvStatusPill, surfaceVariant, onSurfaceVariant);
                        accent = outline;
                        break;
                    case AttendanceRecord.STATUS_CANCELLED_BY_TUTOR:
                        binding.tvStatusPill.setText(R.string.attendance_label_cancelled_tutor);
                        tintPill(binding.tvStatusPill, surfaceVariant, onSurfaceVariant);
                        accent = outline;
                        break;
                    default:
                        binding.tvStatusPill.setText(R.string.attendance_label_rescheduled);
                        tintPill(binding.tvStatusPill, primaryContainer, onPrimaryContainer);
                        accent = primary;
                        break;
                }
            }

            // ---- billing pill ----
            if (item.needsAttendance || item.chargeDecision == null) {
                binding.tvBillingPill.setVisibility(View.GONE);
            } else {
                binding.tvBillingPill.setVisibility(View.VISIBLE);
                if (AttendanceRecord.DECISION_CHARGE.equals(item.chargeDecision)) {
                    binding.tvBillingPill.setText(item.feeMinor > 0
                            ? ctx.getString(R.string.attendance_billing_charged, formatMoney(item.feeMinor))
                            : ctx.getString(R.string.attendance_billing_charged_plain));
                    tintPill(binding.tvBillingPill, primaryContainer, onPrimaryContainer);
                } else if (AttendanceRecord.DECISION_INCLUDED_MONTHLY.equals(item.chargeDecision)) {
                    binding.tvBillingPill.setText(R.string.attendance_billing_monthly);
                    tintPill(binding.tvBillingPill, surfaceVariant, onSurfaceVariant);
                } else {
                    binding.tvBillingPill.setText(R.string.attendance_billing_waived);
                    tintPill(binding.tvBillingPill, surfaceVariant, onSurfaceVariant);
                }
            }

            // ---- extra pill: invoiced / makeup ----
            if (item.billed) {
                binding.tvExtraPill.setVisibility(View.VISIBLE);
                binding.tvExtraPill.setText(R.string.attendance_pill_invoiced);
                tintPill(binding.tvExtraPill, primary, MaterialColors.getColor(root,
                        com.google.android.material.R.attr.colorOnPrimary));
                accent = primary;
            } else if (item.makeupScheduled) {
                binding.tvExtraPill.setVisibility(View.VISIBLE);
                binding.tvExtraPill.setText(R.string.attendance_pill_makeup_scheduled);
                tintPill(binding.tvExtraPill, primaryContainer, onPrimaryContainer);
            } else if (item.makeupClass) {
                binding.tvExtraPill.setVisibility(View.VISIBLE);
                binding.tvExtraPill.setText(R.string.attendance_pill_makeup_class);
                tintPill(binding.tvExtraPill, primaryContainer, onPrimaryContainer);
            } else {
                binding.tvExtraPill.setVisibility(View.GONE);
            }

            binding.viewAccent.setBackgroundColor(accent);

            // ---- action button ----
            if (item.needsAttendance) {
                binding.btnAction.setText(R.string.attendance_action_record);
                binding.btnAction.setIconResource(R.drawable.ic_check_circle);
            } else if (item.billed) {
                binding.btnAction.setText(R.string.attendance_action_locked);
                binding.btnAction.setIconResource(R.drawable.ic_lock_outline);
            } else {
                binding.btnAction.setText(R.string.attendance_action_correct);
                binding.btnAction.setIconResource(R.drawable.ic_edit_outline);
            }

            View.OnClickListener click = v -> listener.onItemClick(item);
            binding.cardAttendanceItem.setOnClickListener(click);
            binding.btnAction.setOnClickListener(click);
        }
    }
}

package com.abdussamadcodes.tutorsync.ui.finance;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.repository.local.TransactionRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * M1: one shared refund flow for every screen.
 *
 * 1. shows how much can still be refunded and lets the tutor choose the amount and a reason,
 * 2. validates the amount (max 2 decimals, > 0, not more than what is still refundable),
 * 3. ALWAYS asks for confirmation (a stronger warning for a full refund),
 * 4. tells the tutor clearly why a refund could not be saved.
 */
public final class RefundDialogHelper {

    public interface Listener {
        /** The refund was saved; the caller should show a message and reload its data. */
        void onRefundSaved();
    }

    private RefundDialogHelper() {
    }

    public static void show(@NonNull Activity activity,
                            @NonNull TransactionRepository repo,
                            @NonNull String accountId,
                            @NonNull FinancialTransaction txn,
                            @NonNull Listener listener) {
        if (accountId.trim().isEmpty()) {
            showError(activity, R.string.refund_err_not_found);
            return;
        }

        final int remaining = repo.getRemainingRefundableMinor(accountId, txn.getId());
        if (remaining <= 0) {
            showError(activity, R.string.refund_err_already_refunded);
            return;
        }

        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        int gap = (int) (12 * activity.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, gap, pad, 0);

        TextInputLayout tilAmount = new TextInputLayout(activity, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        tilAmount.setHint(activity.getString(R.string.refund_hint_amount));
        tilAmount.setHelperText(activity.getString(R.string.refund_remaining_fmt, money(remaining)));
        TextInputEditText etAmount = new TextInputEditText(tilAmount.getContext());
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format(Locale.US, "%.2f", remaining / 100.0));
        etAmount.setSelectAllOnFocus(true);
        tilAmount.addView(etAmount);
        layout.addView(tilAmount, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextInputLayout tilReason = new TextInputLayout(activity, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        tilReason.setHint(activity.getString(R.string.refund_hint_reason));
        TextInputEditText etReason = new TextInputEditText(tilReason.getContext());
        etReason.setText(activity.getString(R.string.refund_default_reason));
        tilReason.addView(etReason);
        LinearLayout.LayoutParams reasonLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        reasonLp.topMargin = gap;
        layout.addView(tilReason, reasonLp);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.invoice_issue_refund))
                .setView(layout)
                .setPositiveButton(R.string.refund_action_continue, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((View v) -> {
                    tilAmount.setError(null);
                    int refundMinor = parseMinor(etAmount.getText() == null ? "" : etAmount.getText().toString());
                    if (refundMinor <= 0) {
                        tilAmount.setError(activity.getString(R.string.refund_err_invalid_amount));
                        return;
                    }
                    if (refundMinor > remaining) {
                        tilAmount.setError(activity.getString(R.string.refund_err_exceeds, money(remaining)));
                        return;
                    }
                    String reason = etReason.getText() == null ? "" : etReason.getText().toString().trim();
                    if (reason.isEmpty()) {
                        reason = activity.getString(R.string.refund_default_reason);
                    }
                    dialog.dismiss();
                    confirm(activity, repo, accountId, txn, refundMinor, remaining, reason, listener);
                }));
        dialog.show();
    }

    private static void confirm(@NonNull Activity activity,
                                @NonNull TransactionRepository repo,
                                @NonNull String accountId,
                                @NonNull FinancialTransaction txn,
                                int refundMinor,
                                int remaining,
                                @NonNull String reason,
                                @NonNull Listener listener) {
        boolean full = refundMinor == remaining;
        String message = full
                ? activity.getString(R.string.refund_confirm_full_msg, money(refundMinor))
                : activity.getString(R.string.refund_confirm_partial_msg, money(refundMinor), money(remaining));

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.refund_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.refund_confirm_action, (d, w) -> {
                    int result = repo.issueRefund(accountId, txn.getId(), refundMinor, reason);
                    if (result == TransactionRepository.REFUND_OK) {
                        listener.onRefundSaved();
                    } else {
                        showError(activity, errorFor(result));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @StringRes
    private static int errorFor(int result) {
        switch (result) {
            case TransactionRepository.REFUND_NOT_FOUND:
                return R.string.refund_err_not_found;
            case TransactionRepository.REFUND_NOT_ELIGIBLE:
                return R.string.refund_err_not_eligible;
            case TransactionRepository.REFUND_INVALID_AMOUNT:
                return R.string.refund_err_invalid_amount;
            default:
                return R.string.refund_err_failed;
        }
    }

    private static void showError(@NonNull Activity activity, @StringRes int messageRes) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.refund_failed_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.refund_ok, null)
                .show();
    }

    @NonNull
    private static String money(int minor) {
        return String.format(Locale.US, "৳ %.2f", minor / 100.0);
    }

    /**
     * Converts what the tutor typed (also Bengali digits) to minor units. Returns -1 when the text
     * is not a positive amount with at most 2 decimals, or is too large.
     */
    static int parseMinor(@NonNull String raw) {
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.trim().toCharArray()) {
            if (Character.isDigit(ch)) {
                sb.append(Character.digit(ch, 10));
            } else if (ch == '.' || ch == ',') {
                sb.append('.');
            } else if (!Character.isWhitespace(ch)) {
                return -1;
            }
        }
        try {
            BigDecimal amount = new BigDecimal(sb.toString());
            if (amount.signum() <= 0) return -1;
            amount = amount.stripTrailingZeros();
            if (amount.scale() > 2) return -1;
            return amount.movePointRight(2).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }
}

package com.abdussamadcodes.tutorsync.ui.finance;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.FeeAgreement;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository.UnbilledClassCharge;
import com.abdussamadcodes.tutorsync.repository.local.FeeAgreementRepository;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.TransactionRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.util.FinancialPdfGenerator;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class StudentTransactionsActivity extends BaseActivity {

    private String studentId;
    private Student currentStudent;
    private SessionManager sessionManager;
    private LocalAccountStore accountStore;
    private StudentRepository studentRepo;
    private ScheduleRepository scheduleRepo;
    private FeeAgreementRepository feeRepo;
    private AttendanceRepository attendanceRepo;
    private InvoiceRepository invoiceRepo;
    private TransactionRepository txnRepo;

    private LinearLayout contentContainer;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        studentId = getIntent().getStringExtra("student_id");
        sessionManager = new SessionManager(this);
        accountStore = new LocalAccountStore(this);
        studentRepo = new StudentRepository();
        scheduleRepo = new ScheduleRepository();
        feeRepo = new FeeAgreementRepository();
        attendanceRepo = new AttendanceRepository();
        invoiceRepo = new InvoiceRepository();
        txnRepo = new TransactionRepository();

        CoordinatorLayout root = new CoordinatorLayout(this);
        root.setBackgroundColor(resolveAttrColor(android.R.attr.colorBackground));

        AppBarLayout appBar = new AppBarLayout(this);
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.ledger_title));
        TypedValue navIcon = new TypedValue();
        if (getTheme().resolveAttribute(androidx.appcompat.R.attr.homeAsUpIndicator, navIcon, true)) {
            toolbar.setNavigationIcon(navIcon.resourceId);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        appBar.addView(toolbar, new AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(appBar, new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        CoordinatorLayout.LayoutParams scrollLp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scrollLp.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        scrollView.setLayoutParams(scrollLp);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(16), dp(12), dp(16), dp(32));
        scrollView.addView(contentContainer);
        root.addView(scrollView);

        setContentView(root);
        applySystemBarInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHistory();
    }

    @NonNull
    private String getActiveTutorName() {
        String accountId = sessionManager.getActiveAccountId();
        if (!accountId.isEmpty()) {
            Account account = accountStore.getAccountById(accountId);
            if (account != null && !account.getFullName().trim().isEmpty()) {
                return account.getFullName().trim();
            }
        }
        return "Tutor";
    }

    private String formatTimeMinutes(int startTimeMinutes) {
        int h = (startTimeMinutes / 60) % 12;
        if (h == 0) h = 12;
        String ampm = startTimeMinutes >= 720 ? "PM" : "AM";
        return String.format(Locale.getDefault(), "%02d:%02d %s", h, startTimeMinutes % 60, ampm);
    }

    private String formatBillingModelLabel(String billingModel) {
        return "PER_CLASS".equals(billingModel)
                ? getString(R.string.fee_type_per_class)
                : getString(R.string.fee_type_monthly);
    }

    private void refreshHistory() {
        contentContainer.removeAllViews();
        String accountId = sessionManager.getActiveAccountId();
        studentRepo.migrateLegacyStudentIds(accountId);
        currentStudent = studentRepo.getStudentById(studentId);
        if (currentStudent == null) {
            finish();
            return;
        }
        toolbar.setTitle(currentStudent.getFormattedDisplayName());

        // 1. Fetch schedules & fee agreements for this student
        List<RecurringSchedule> allSchedules = scheduleRepo.getSchedulesForAccount(accountId);
        Map<String, RecurringSchedule> studentScheduleGroups = new LinkedHashMap<>();
        for (RecurringSchedule rs : allSchedules) {
            if (rs.getStudentId().equals(studentId)) {
                String key = rs.getScheduleGroupId() != null ? rs.getScheduleGroupId() : ("TIME-" + rs.getStartTimeMinutes());
                if (!studentScheduleGroups.containsKey(key)) {
                    studentScheduleGroups.put(key, rs);
                }
            }
        }
        FeeAgreement fallbackAgreement = feeRepo.getAgreementForStudent(studentId);

        // 2. Fetch attended per-class charges
        List<UnbilledClassCharge> classCharges = attendanceRepo.getPerClassAttendanceChargesForStudent(accountId, studentId);
        int unbilledClassTotalMinor = 0;
        int unbilledClassCount = 0;
        for (UnbilledClassCharge cc : classCharges) {
            if (!cc.isBilled) {
                unbilledClassTotalMinor += cc.feeMinor;
                unbilledClassCount++;
            }
        }

        // 3. Fetch invoices for this student
        List<Invoice> allAccountInvoices = invoiceRepo.getFilteredInvoicesForAccount(accountId, "ALL");
        List<Invoice> studentInvoices = new ArrayList<>();
        List<Invoice> studentOpenInvoices = new ArrayList<>();
        int totalInvoicedMinor = 0;
        int totalPaidMinor = 0;
        int totalInvoiceDueMinor = 0;

        for (Invoice inv : allAccountInvoices) {
            if (inv.getStudentId().equals(studentId)) {
                studentInvoices.add(inv);
                totalInvoicedMinor += inv.getTotalAmount();
                totalPaidMinor += inv.getPaidAmount();
                totalInvoiceDueMinor += inv.getOutstandingBalance();
                if (inv.getOutstandingBalance() > 0) {
                    studentOpenInvoices.add(inv);
                }
            }
        }
        Collections.reverse(studentOpenInvoices);

        // --- CARD 1: Financial Summary Overview ---
        MaterialCardView summaryCard = createSectionCard();
        LinearLayout summaryCol = createCardBody(summaryCard);
        addCardHeader(summaryCol, getString(R.string.ledger_financial_overview));
        addBodyLine(summaryCol, getString(R.string.ledger_unbilled_classes_fmt,
                unbilledClassTotalMinor / 100.0, unbilledClassCount), true);
        addBodyLine(summaryCol, getString(R.string.ledger_invoiced_due_fmt,
                totalInvoiceDueMinor / 100.0), true);
        addBodyLine(summaryCol, getString(R.string.ledger_total_billed_paid_fmt,
                totalInvoicedMinor / 100.0, totalPaidMinor / 100.0), false);
        contentContainer.addView(summaryCard);

        // --- CARD 2: Active Fee Rate & Schedules ---
        MaterialCardView feeCard = createSectionCard();
        LinearLayout feeCol = createCardBody(feeCard);
        addCardHeader(feeCol, getString(R.string.ledger_fee_rate_header));

        if (!studentScheduleGroups.isEmpty()) {
            for (Map.Entry<String, RecurringSchedule> entry : studentScheduleGroups.entrySet()) {
                final String groupKey = entry.getKey();
                RecurringSchedule rs = entry.getValue();
                String timeStr = formatTimeMinutes(rs.getStartTimeMinutes());
                int feeVal = rs.getFeeAmountMinor();
                String modelCode = rs.getBillingModel();
                if (feeVal <= 0 && fallbackAgreement != null) {
                    feeVal = fallbackAgreement.getAmountMinorUnits();
                    modelCode = fallbackAgreement.getFeeType();
                }
                String modelStr = formatBillingModelLabel(modelCode);
                TextView row = addBodyLine(feeCol, getString(R.string.ledger_schedule_fee_row_fmt,
                        timeStr, feeVal / 100.0, modelStr), false);
                // Tapping a schedule row opens the fee sheet pre-selected to that schedule
                row.setOnClickListener(v -> showFeeRateBottomSheet(studentScheduleGroups, fallbackAgreement, groupKey));
            }
        } else if (fallbackAgreement != null) {
            String modelStr = formatBillingModelLabel(fallbackAgreement.getFeeType());
            addBodyLine(feeCol, getString(R.string.ledger_default_fee_row_fmt,
                    fallbackAgreement.getAmountMinorUnits() / 100.0, modelStr), false);
        } else {
            addBodyLine(feeCol, getString(R.string.ledger_no_fee_configured), false);
        }

        MaterialButton btnEditFee = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnEditFee.setText(getString(R.string.ledger_btn_set_update_fee));
        btnEditFee.setOnClickListener(v -> showFeeRateBottomSheet(studentScheduleGroups, fallbackAgreement, null));
        feeCol.addView(btnEditFee);
        contentContainer.addView(feeCard);

        // --- CARD 3: Attended Classes & Per-Class Charges ---
        MaterialCardView attendanceCard = createSectionCard();
        LinearLayout attCol = createCardBody(attendanceCard);
        addCardHeader(attCol, getString(R.string.ledger_attended_classes_header));

        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        if (classCharges.isEmpty()) {
            addBodyLine(attCol, getString(R.string.ledger_no_per_class_sessions), false);
        } else {
            for (UnbilledClassCharge cc : classCharges) {
                String timeStr = formatTimeMinutes(cc.startTimeMinutes);
                String badge = cc.isBilled ? getString(R.string.ledger_badge_billed) : getString(R.string.ledger_badge_unbilled);
                addBodyLine(attCol, String.format(Locale.getDefault(),
                        "%s · %s (%s) — ৳ %.2f  %s",
                        df.format(new Date(cc.dateMillis)), timeStr,
                        cc.status, cc.feeMinor / 100.0, badge), !cc.isBilled);
            }
        }

        MaterialButton btnGenInvoice = new MaterialButton(this);
        btnGenInvoice.setText(unbilledClassCount > 0
                ? getString(R.string.ledger_btn_generate_invoice_amt_fmt, unbilledClassTotalMinor / 100.0)
                : getString(R.string.ledger_btn_generate_due_invoice));
        btnGenInvoice.setOnClickListener(v -> {
            int created = invoiceRepo.generateDueInvoicesForStudent(accountId, studentId);
            if (created > 0) {
                showMessage(contentContainer, getString(R.string.invoice_generated_success));
                refreshHistory();
            } else {
                showMessage(contentContainer, getString(R.string.invoice_none_needed));
            }
        });
        attCol.addView(btnGenInvoice);
        contentContainer.addView(attendanceCard);

        // --- CARD 4: Student Invoices ---
        MaterialCardView invCard = createSectionCard();
        LinearLayout invCol = createCardBody(invCard);
        addCardHeader(invCol, getString(R.string.ledger_student_invoices_header));

        if (studentInvoices.isEmpty()) {
            addBodyLine(invCol, getString(R.string.invoice_empty), false);
        } else {
            for (Invoice inv : studentInvoices) {
                String summaryLine = getString(R.string.invoice_card_amounts_fmt,
                        inv.getTotalAmount() / 100.0, inv.getPaidAmount() / 100.0, inv.getOutstandingBalance() / 100.0);
                TextView row = addBodyLine(invCol, String.format(Locale.getDefault(),
                                "%s (%s) · %s\n%s   %s",
                                inv.getInvoiceIdDisplay(), inv.getStatus(), inv.getBillingPeriod(),
                                summaryLine, getString(R.string.ledger_tap_to_open)),
                        inv.getOutstandingBalance() > 0);
                row.setOnClickListener(v -> {
                    Intent intent = new Intent(this, InvoiceDetailActivity.class);
                    intent.putExtra("invoice_id", inv.getId());
                    startActivity(intent);
                });
            }
        }

        if (totalInvoiceDueMinor > 0) {
            final int maxPayableMinor = totalInvoiceDueMinor;
            MaterialButton btnPayMulti = new MaterialButton(this);
            btnPayMulti.setText(getString(R.string.ledger_btn_pay_multi));
            btnPayMulti.setOnClickListener(v -> showMultiInvoicePaymentDialog(studentOpenInvoices, maxPayableMinor));
            invCol.addView(btnPayMulti);
        }
        contentContainer.addView(invCard);

        // --- CARD 5: Transactions (Payments & Refunds) ---
        MaterialCardView txnCard = createSectionCard();
        LinearLayout txnCol = createCardBody(txnCard);
        addCardHeader(txnCol, getString(R.string.invoice_payment_history));

        List<FinancialTransaction> txns = txnRepo.getTransactionsForStudent(accountId, studentId);
        SimpleDateFormat dtf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        if (txns.isEmpty()) {
            addBodyLine(txnCol, getString(R.string.invoice_no_payments), false);
        } else {
            for (FinancialTransaction txn : txns) {
                String invoicesStr = txn.getRelatedInvoiceDisplayIds().isEmpty()
                        ? "N/A" : String.join(", ", txn.getRelatedInvoiceDisplayIds());
                String text = String.format(Locale.getDefault(),
                        "%s (%s) — ৳%.2f\n%s · %s | %s   %s",
                        txn.getTxnIdDisplay(), txn.getType(), txn.getAmountMinor() / 100.0,
                        dtf.format(new Date(txn.getTimestamp())), txn.getMethod(),
                        invoicesStr, getString(R.string.ledger_tap_for_receipt));
                TextView tv = addBodyLine(txnCol, text, false);
                tv.setOnClickListener(v -> showTransactionOptions(txn));
            }
        }
        contentContainer.addView(txnCard);
    }

    /**
     * Displays the bottom sheet to update a student's fee rate.
     * When the student has schedules, shows selectable chips so the tutor chooses which schedule to update.
     */
    private void showFeeRateBottomSheet(
            Map<String, RecurringSchedule> scheduleGroups,
            @Nullable FeeAgreement existing,
            @Nullable String preselectedGroupKey) {

        BottomSheetDialog feeDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_fee_agreement, null);
        feeDialog.setContentView(view);

        View layoutScheduleSelector = view.findViewById(R.id.layoutScheduleSelector);
        ChipGroup chipGroupSchedules = view.findViewById(R.id.chipGroupSchedules);
        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroupFeeType);
        TextInputEditText etAmount = view.findViewById(R.id.etFeeAmount);

        final List<RecurringSchedule> scheduleList = new ArrayList<>(scheduleGroups.values());
        final int[] selectedIndex = {0};

        if (!scheduleList.isEmpty()) {
            layoutScheduleSelector.setVisibility(View.VISIBLE);
            chipGroupSchedules.removeAllViews();

            for (int i = 0; i < scheduleList.size(); i++) {
                RecurringSchedule rs = scheduleList.get(i);
                String key = rs.getScheduleGroupId() != null ? rs.getScheduleGroupId() : ("TIME-" + rs.getStartTimeMinutes());
                if (preselectedGroupKey != null && preselectedGroupKey.equals(key)) {
                    selectedIndex[0] = i;
                }

                Chip chip = new Chip(this);
                chip.setId(View.generateViewId());
                chip.setCheckable(true);
                chip.setClickable(true);
                int feeVal = rs.getFeeAmountMinor();
                if (feeVal <= 0 && existing != null) feeVal = existing.getAmountMinorUnits();
                chip.setText(String.format(Locale.getDefault(), "%s (৳%.0f)",
                        formatTimeMinutes(rs.getStartTimeMinutes()), feeVal / 100.0));

                final int idx = i;
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedIndex[0] = idx;
                        RecurringSchedule chosen = scheduleList.get(idx);
                        String model = chosen.getBillingModel();
                        int minor = chosen.getFeeAmountMinor();
                        if (minor <= 0 && existing != null) {
                            model = existing.getFeeType();
                            minor = existing.getAmountMinorUnits();
                        }
                        if (FeeAgreement.TYPE_PER_CLASS.equals(model)) {
                            toggleGroup.check(R.id.btnFeePerClass);
                        } else {
                            toggleGroup.check(R.id.btnFeeMonthly);
                        }
                        etAmount.setText(minor > 0 ? String.valueOf(minor / 100) : "");
                    }
                });

                chipGroupSchedules.addView(chip);
                if (i == selectedIndex[0]) {
                    chip.setChecked(true);
                }
            }
        } else {
            layoutScheduleSelector.setVisibility(View.GONE);
            if (existing != null) {
                if (FeeAgreement.TYPE_PER_CLASS.equals(existing.getFeeType())) {
                    toggleGroup.check(R.id.btnFeePerClass);
                } else {
                    toggleGroup.check(R.id.btnFeeMonthly);
                }
                etAmount.setText(String.valueOf(existing.getAmountMinorUnits() / 100));
            } else {
                toggleGroup.check(R.id.btnFeeMonthly);
            }
        }

        view.findViewById(R.id.btnSaveFee).setOnClickListener(v -> {
            String amtStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
            if (amtStr.isEmpty()) {
                etAmount.setError(getString(R.string.auth_err_empty));
                return;
            }
            int amountMinor;
            try {
                amountMinor = (int) Math.round(Double.parseDouble(amtStr) * 100);
            } catch (NumberFormatException e) {
                etAmount.setError(getString(R.string.invoice_err_invalid_amount));
                return;
            }

            String type = toggleGroup.getCheckedButtonId() == R.id.btnFeePerClass
                    ? FeeAgreement.TYPE_PER_CLASS : FeeAgreement.TYPE_MONTHLY;
            String accountId = sessionManager.getActiveAccountId();

            if (!scheduleList.isEmpty()) {
                RecurringSchedule chosenSchedule = scheduleList.get(selectedIndex[0]);
                int anchorDay = chosenSchedule.getAnchorDay() > 0
                        ? chosenSchedule.getAnchorDay()
                        : Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                if (chosenSchedule.getScheduleGroupId() != null) {
                    scheduleRepo.updateScheduleGroupFee(
                            accountId, studentId, chosenSchedule.getScheduleGroupId(),
                            type, amountMinor, anchorDay);
                }
            } else {
                FeeAgreement agreement = new FeeAgreement(
                        existing != null ? existing.getId() : UUID.randomUUID().toString(),
                        accountId, studentId, type, amountMinor, System.currentTimeMillis()
                );
                feeRepo.saveAgreement(agreement);
            }

            feeDialog.dismiss();
            showMessage(contentContainer, getString(R.string.fee_saved_success));
            refreshHistory();
        });

        feeDialog.show();
    }

    private void showMultiInvoicePaymentDialog(List<Invoice> openInvoicesChronological, int totalDueMinor) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, 0);

        EditText etAmount = new EditText(this);
        etAmount.setHint(getString(R.string.invoice_payment_amount));
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format(Locale.US, "%.2f", totalDueMinor / 100.0));
        layout.addView(etAmount);

        Spinner spMethod = new Spinner(this);
        String[] methods = {"Cash", "bKash", "Nagad", "Bank Transfer"};
        spMethod.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, methods));
        layout.addView(spMethod);

        EditText etRef = new EditText(this);
        etRef.setHint(getString(R.string.invoice_ref_hint));
        layout.addView(etRef);

        EditText etNotes = new EditText(this);
        etNotes.setHint(getString(R.string.invoice_notes_hint));
        layout.addView(etNotes);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ledger_btn_pay_multi))
                .setView(layout)
                .setPositiveButton(getString(R.string.action_save), (d, w) -> {
                    try {
                        double bdt = Double.parseDouble(etAmount.getText().toString().trim());
                        int amountMinor = (int) Math.round(bdt * 100);
                        if (amountMinor <= 0 || amountMinor > totalDueMinor) {
                            showMessage(contentContainer, getString(R.string.invoice_err_invalid_amount));
                            return;
                        }
                        FinancialTransaction created = txnRepo.recordStudentLumpSumPaymentFIFO(
                                sessionManager.getActiveAccountId(),
                                studentId,
                                amountMinor,
                                spMethod.getSelectedItem().toString(),
                                etRef.getText().toString().trim(),
                                etNotes.getText().toString().trim(),
                                openInvoicesChronological
                        );
                        if (created != null) {
                            showMessage(contentContainer, getString(R.string.invoice_payment_saved_fmt, created.getTxnIdDisplay()));
                            refreshHistory();
                        }
                    } catch (NumberFormatException e) {
                        showMessage(contentContainer, getString(R.string.invoice_err_invalid_amount));
                    }
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void showTransactionOptions(FinancialTransaction txn) {
        String[] items = {
                getString(R.string.invoice_share_receipt_pdf),
                getString(R.string.invoice_issue_refund)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(txn.getTxnIdDisplay())
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        try {
                            File pdf = FinancialPdfGenerator.generatePaymentReceiptPdf(
                                    this, getActiveTutorName(), currentStudent, txn);
                            FinancialPdfGenerator.sharePdf(this, pdf, getString(R.string.invoice_share_receipt_pdf));
                        } catch (Exception e) {
                            showMessage(contentContainer, e.getMessage());
                        }
                    } else if (which == 1 && FinancialTransaction.TYPE_PAYMENT.equals(txn.getType())) {
                        boolean ok = txnRepo.recordRefundForTransaction(
                                sessionManager.getActiveAccountId(), txn.getId(), txn.getAmountMinor(), "Tutor refund");
                        if (ok) {
                            showMessage(contentContainer, getString(R.string.invoice_refund_saved_msg));
                            refreshHistory();
                        }
                    }
                })
                .show();
    }

    private MaterialCardView createSectionCard() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        card.setRadius(dp(14));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(resolveAttrColor(com.google.android.material.R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(resolveAttrColor(com.google.android.material.R.attr.colorSurface));
        return card;
    }

    private LinearLayout createCardBody(MaterialCardView card) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(col);
        return col;
    }

    private void addCardHeader(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(16f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
        tv.setPadding(0, 0, 0, dp(8));
        parent.addView(tv);
    }

    private TextView addBodyLine(LinearLayout parent, String text, boolean highlight) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14f);
        if (highlight) tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(resolveAttrColor(highlight
                ? com.google.android.material.R.attr.colorOnSurface
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
        tv.setPadding(0, dp(6), 0, dp(6));
        parent.addView(tv);
        return tv;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int resolveAttrColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
}
package com.abdussamadcodes.tutorsync.ui.finance;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.abdussamadcodes.tutorsync.R;
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
        toolbar.setTitle("Student Fee & Ledger");
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

        // 2. Fetch attended per-class charges (auto-heals any pending per-class records)
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
        addCardHeader(summaryCol, "Financial Overview");
        addBodyLine(summaryCol, String.format(Locale.US,
                "Unbilled Class Charges: ৳ %.2f (%d class%s)",
                unbilledClassTotalMinor / 100.0, unbilledClassCount, unbilledClassCount == 1 ? "" : "es"), true);
        addBodyLine(summaryCol, String.format(Locale.US,
                "Invoiced Outstanding Due: ৳ %.2f", totalInvoiceDueMinor / 100.0), true);
        addBodyLine(summaryCol, String.format(Locale.US,
                "Total Billed: ৳ %.2f   |   Total Paid: ৳ %.2f",
                totalInvoicedMinor / 100.0, totalPaidMinor / 100.0), false);
        contentContainer.addView(summaryCard);

        // --- CARD 2: Active Fee Rate & Schedules ---
        MaterialCardView feeCard = createSectionCard();
        LinearLayout feeCol = createCardBody(feeCard);
        addCardHeader(feeCol, "Fee Rate & Billing Model");

        if (!studentScheduleGroups.isEmpty()) {
            for (RecurringSchedule rs : studentScheduleGroups.values()) {
                int h = (rs.getStartTimeMinutes() / 60) % 12;
                if (h == 0) h = 12;
                String ampm = rs.getStartTimeMinutes() >= 720 ? "PM" : "AM";
                String modelStr = "PER_CLASS".equals(rs.getBillingModel()) ? "Per Class" : "Fixed Monthly";
                int feeVal = rs.getFeeAmountMinor();
                if (feeVal <= 0 && fallbackAgreement != null) {
                    feeVal = fallbackAgreement.getAmountMinorUnits();
                    modelStr = "PER_CLASS".equals(fallbackAgreement.getFeeType()) ? "Per Class" : "Fixed Monthly";
                }
                addBodyLine(feeCol, String.format(Locale.US,
                        "• Schedule (%02d:%02d %s): ৳ %.2f (%s)",
                        h, rs.getStartTimeMinutes() % 60, ampm, feeVal / 100.0, modelStr), false);
            }
        } else if (fallbackAgreement != null) {
            String modelStr = FeeAgreement.TYPE_PER_CLASS.equals(fallbackAgreement.getFeeType()) ? "Per Class" : "Fixed Monthly";
            addBodyLine(feeCol, String.format(Locale.US,
                    "• Default Fee Agreement: ৳ %.2f (%s)",
                    fallbackAgreement.getAmountMinorUnits() / 100.0, modelStr), false);
        } else {
            addBodyLine(feeCol, "No fee rate configured yet.", false);
        }

        MaterialButton btnEditFee = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnEditFee.setText("Set / Update Fee Rate");
        btnEditFee.setOnClickListener(v -> showFeeRateBottomSheet(studentScheduleGroups, fallbackAgreement));
        feeCol.addView(btnEditFee);
        contentContainer.addView(feeCard);

        // --- CARD 3: Attended Classes & Per-Class Charges ---
        MaterialCardView attendanceCard = createSectionCard();
        LinearLayout attCol = createCardBody(attendanceCard);
        addCardHeader(attCol, "Attended Classes & Per-Class Charges");

        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        if (classCharges.isEmpty()) {
            addBodyLine(attCol, "No chargeable per-class sessions recorded yet.", false);
        } else {
            for (UnbilledClassCharge cc : classCharges) {
                int h = (cc.startTimeMinutes / 60) % 12;
                if (h == 0) h = 12;
                String ampm = cc.startTimeMinutes >= 720 ? "PM" : "AM";
                String badge = cc.isBilled ? "[BILLED]" : "[UNBILLED DUE]";
                addBodyLine(attCol, String.format(Locale.US,
                        "%s · %02d:%02d %s (%s) — ৳ %.2f  %s",
                        df.format(new Date(cc.dateMillis)), h, cc.startTimeMinutes % 60, ampm,
                        cc.status, cc.feeMinor / 100.0, badge), !cc.isBilled);
            }
        }

        MaterialButton btnGenInvoice = new MaterialButton(this);
        btnGenInvoice.setText(unbilledClassCount > 0
                ? String.format(Locale.US, "Generate Invoice Now (৳ %.2f)", unbilledClassTotalMinor / 100.0)
                : "Generate Due Invoice");
        btnGenInvoice.setOnClickListener(v -> {
            int created = invoiceRepo.generateDueInvoicesForStudent(accountId, studentId);
            if (created > 0) {
                showMessage(contentContainer, "Invoice generated successfully!");
                refreshHistory();
            } else {
                showMessage(contentContainer, "No unbilled classes or due monthly cycles to invoice.");
            }
        });
        attCol.addView(btnGenInvoice);
        contentContainer.addView(attendanceCard);

        // --- CARD 4: Student Invoices ---
        MaterialCardView invCard = createSectionCard();
        LinearLayout invCol = createCardBody(invCard);
        addCardHeader(invCol, "Student Invoices");

        if (studentInvoices.isEmpty()) {
            addBodyLine(invCol, "No invoices generated yet.", false);
        } else {
            for (Invoice inv : studentInvoices) {
                TextView row = addBodyLine(invCol, String.format(Locale.US,
                                "%s (%s) · %s\nTotal: ৳%.2f | Paid: ৳%.2f | Due: ৳%.2f   [Tap to Open]",
                                inv.getInvoiceIdDisplay(), inv.getStatus(), inv.getBillingPeriod(),
                                inv.getTotalAmount() / 100.0, inv.getPaidAmount() / 100.0, inv.getOutstandingBalance() / 100.0),
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
            btnPayMulti.setText("Record Payment Across Open Invoices");
            btnPayMulti.setOnClickListener(v -> showMultiInvoicePaymentDialog(studentOpenInvoices, maxPayableMinor));
            invCol.addView(btnPayMulti);
        }
        contentContainer.addView(invCard);

        // --- CARD 5: Transactions (Payments & Refunds) ---
        MaterialCardView txnCard = createSectionCard();
        LinearLayout txnCol = createCardBody(txnCard);
        addCardHeader(txnCol, "Payment & Refund History");

        List<FinancialTransaction> txns = txnRepo.getTransactionsForStudent(accountId, studentId);
        SimpleDateFormat dtf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        if (txns.isEmpty()) {
            addBodyLine(txnCol, "No payments or refunds recorded yet.", false);
        } else {
            for (FinancialTransaction txn : txns) {
                String invoicesStr = txn.getRelatedInvoiceDisplayIds().isEmpty()
                        ? "N/A" : String.join(", ", txn.getRelatedInvoiceDisplayIds());
                String text = String.format(Locale.US,
                        "%s (%s) — ৳%.2f\n%s · %s | Invoices: %s   [Tap for Receipt / Refund]",
                        txn.getTxnIdDisplay(), txn.getType(), txn.getAmountMinor() / 100.0,
                        dtf.format(new Date(txn.getTimestamp())), txn.getMethod(), invoicesStr);
                TextView tv = addBodyLine(txnCol, text, false);
                tv.setOnClickListener(v -> showTransactionOptions(txn));
            }
        }
        contentContainer.addView(txnCard);
    }

    private void showFeeRateBottomSheet(Map<String, RecurringSchedule> scheduleGroups, @Nullable FeeAgreement existing) {
        BottomSheetDialog feeDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_fee_agreement, null);
        feeDialog.setContentView(view);

        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroupFeeType);
        TextInputEditText etAmount = view.findViewById(R.id.etFeeAmount);

        String currentModel = FeeAgreement.TYPE_MONTHLY;
        int currentMinor = 0;
        if (!scheduleGroups.isEmpty()) {
            RecurringSchedule first = scheduleGroups.values().iterator().next();
            currentModel = first.getBillingModel();
            currentMinor = first.getFeeAmountMinor();
        }
        if (currentMinor <= 0 && existing != null) {
            currentModel = existing.getFeeType();
            currentMinor = existing.getAmountMinorUnits();
        }

        if (FeeAgreement.TYPE_PER_CLASS.equals(currentModel)) {
            toggleGroup.check(R.id.btnFeePerClass);
        } else {
            toggleGroup.check(R.id.btnFeeMonthly);
        }
        if (currentMinor > 0) {
            etAmount.setText(String.valueOf(currentMinor / 100));
        }

        view.findViewById(R.id.btnSaveFee).setOnClickListener(v -> {
            String amtStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
            if (amtStr.isEmpty()) {
                etAmount.setError("Required");
                return;
            }
            int amountMinor;
            try {
                amountMinor = (int) Math.round(Double.parseDouble(amtStr) * 100);
            } catch (NumberFormatException e) {
                etAmount.setError("Invalid amount");
                return;
            }

            String type = toggleGroup.getCheckedButtonId() == R.id.btnFeePerClass
                    ? FeeAgreement.TYPE_PER_CLASS : FeeAgreement.TYPE_MONTHLY;
            String accountId = sessionManager.getActiveAccountId();

            // Save to legacy fee_agreements AND update all active schedule groups for this student
            FeeAgreement agreement = new FeeAgreement(
                    existing != null ? existing.getId() : UUID.randomUUID().toString(),
                    accountId, studentId, type, amountMinor, System.currentTimeMillis()
            );
            feeRepo.saveAgreement(agreement);

            int anchorDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            for (RecurringSchedule rs : scheduleGroups.values()) {
                if (rs.getScheduleGroupId() != null) {
                    scheduleRepo.updateScheduleGroupFee(accountId, studentId, rs.getScheduleGroupId(), type, amountMinor, anchorDay);
                }
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
        etAmount.setHint("Payment Amount in BDT");
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format(Locale.US, "%.2f", totalDueMinor / 100.0));
        layout.addView(etAmount);

        Spinner spMethod = new Spinner(this);
        String[] methods = {"Cash", "bKash", "Nagad", "Bank Transfer"};
        spMethod.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, methods));
        layout.addView(spMethod);

        EditText etRef = new EditText(this);
        etRef.setHint("Reference / TrxID (Optional)");
        layout.addView(etRef);

        EditText etNotes = new EditText(this);
        etNotes.setHint("Notes (Optional)");
        layout.addView(etNotes);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Allocate Payment Across Open Invoices")
                .setView(layout)
                .setPositiveButton("Save Payment", (d, w) -> {
                    try {
                        double bdt = Double.parseDouble(etAmount.getText().toString().trim());
                        int amountMinor = (int) Math.round(bdt * 100);
                        if (amountMinor <= 0 || amountMinor > totalDueMinor) {
                            showMessage(contentContainer, "Amount must be between ৳0.01 and total outstanding balance.");
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
                            showMessage(contentContainer, "Payment recorded: " + created.getTxnIdDisplay());
                            refreshHistory();
                        }
                    } catch (NumberFormatException e) {
                        showMessage(contentContainer, "Invalid amount.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTransactionOptions(FinancialTransaction txn) {
        String[] items = {"Export / Share Payment Receipt PDF", "Issue Refund / Correction"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(txn.getTxnIdDisplay())
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        try {
                            File pdf = FinancialPdfGenerator.generatePaymentReceiptPdf(this, "Tutor", currentStudent, txn);
                            FinancialPdfGenerator.sharePdf(this, pdf, "Share Payment Receipt PDF");
                        } catch (Exception e) {
                            showMessage(contentContainer, "Failed to generate receipt PDF.");
                        }
                    } else if (which == 1 && FinancialTransaction.TYPE_PAYMENT.equals(txn.getType())) {
                        boolean ok = txnRepo.recordRefundForTransaction(
                                sessionManager.getActiveAccountId(), txn.getId(), txn.getAmountMinor(), "Tutor refund");
                        if (ok) {
                            showMessage(contentContainer, "Refund recorded and invoice balances updated.");
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
package com.abdussamadcodes.tutorsync.ui.finance;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
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
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.InvoiceItem;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.TransactionRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.FinancialPdfGenerator;
import com.abdussamadcodes.tutorsync.util.PaymentReminderHelper;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InvoiceDetailActivity extends BaseActivity {

    private String invoiceId;
    private Invoice currentInvoice;
    private Student currentStudent;
    private List<InvoiceItem> currentItems;
    private List<FinancialTransaction> currentTransactions;

    private InvoiceRepository invoiceRepo;
    private TransactionRepository txnRepo;
    private StudentRepository studentRepo;
    private SessionManager sessionManager;
    private LinearLayout rootContent;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        invoiceId = getIntent().getStringExtra("invoice_id");
        sessionManager = new SessionManager(this);
        invoiceRepo = new InvoiceRepository();
        txnRepo = new TransactionRepository();
        studentRepo = new StudentRepository();

        CoordinatorLayout root = new CoordinatorLayout(this);
        root.setBackgroundColor(resolveAttrColor(android.R.attr.colorBackground));

        AppBarLayout appBar = new AppBarLayout(this);
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.invoice_detail_title));
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

        rootContent = new LinearLayout(this);
        rootContent.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        rootContent.setPadding(pad, pad, pad, pad);
        scrollView.addView(rootContent);
        root.addView(scrollView);

        setContentView(root);
        applySystemBarInsets(root);

        refreshDetails();
    }

    private void refreshDetails() {
        rootContent.removeAllViews();
        currentInvoice = invoiceRepo.getInvoiceById(invoiceId);
        if (currentInvoice == null) {
            finish();
            return;
        }
        currentStudent = studentRepo.getStudentById(currentInvoice.getStudentId());
        currentItems = invoiceRepo.getItemsForInvoice(invoiceId);
        currentTransactions = txnRepo.getTransactionsForInvoice(invoiceId);

        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        SimpleDateFormat dtf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        toolbar.setTitle(currentInvoice.getInvoiceIdDisplay());
        addHeader(currentInvoice.getInvoiceIdDisplay() + " · " + currentInvoice.getStatus());
        addLine(currentStudent != null ? currentStudent.getFormattedDisplayName() : getString(R.string.unknown_student));
        addLine(getString(R.string.invoice_card_dates_fmt,
                currentInvoice.getBillingPeriod(),
                df.format(new Date(currentInvoice.getIssueDate())),
                df.format(new Date(currentInvoice.getDueDate()))));
        addLine(getString(R.string.invoice_card_amounts_fmt,
                currentInvoice.getTotalAmount() / 100.0,
                currentInvoice.getPaidAmount() / 100.0,
                currentInvoice.getOutstandingBalance() / 100.0));

        addSectionTitle(getString(R.string.invoice_itemized_charges));
        for (InvoiceItem item : currentItems) {
            String modelLabel = "PER_CLASS".equals(item.getBillingModel())
                    ? getString(R.string.fee_type_per_class)
                    : getString(R.string.fee_type_monthly);
            addLine(String.format(Locale.getDefault(), "• %s [%s] — ৳ %.2f",
                    item.getDescription(), modelLabel, item.getAmountMinor() / 100.0));
        }

        addSectionTitle(getString(R.string.invoice_payment_history));
        if (currentTransactions.isEmpty()) {
            addLine(getString(R.string.invoice_no_payments));
        } else {
            for (FinancialTransaction txn : currentTransactions) {
                TextView tv = addLine(String.format(Locale.getDefault(), "%s · %s · %s: ৳ %.2f (%s)",
                        txn.getTxnIdDisplay(), dtf.format(new Date(txn.getTimestamp())),
                        txn.getMethod(), txn.getAmountMinor() / 100.0, txn.getType()));
                tv.setOnClickListener(v -> showTransactionActionsDialog(txn));
            }
        }

        if (currentInvoice.getOutstandingBalance() > 0) {
            MaterialButton btnPay = new MaterialButton(this);
            btnPay.setText(getString(R.string.invoice_record_payment));
            btnPay.setOnClickListener(v -> showRecordPaymentDialog());
            rootContent.addView(btnPay);

            MaterialButton btnRemind = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnRemind.setText(getString(R.string.invoice_send_reminder));
            btnRemind.setOnClickListener(v -> {
                if (currentStudent != null) {
                    boolean isBn = AppPreferences.LANG_BANGLA.equals(appPreferences.getLanguageCode());
                    PaymentReminderHelper.showEditableReminderDialog(this, currentStudent, currentInvoice, isBn);
                }
            });
            rootContent.addView(btnRemind);
        }

        MaterialButton btnPdf = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnPdf.setText(getString(R.string.invoice_export_pdf));
        btnPdf.setOnClickListener(v -> exportInvoicePdf());
        rootContent.addView(btnPdf);
    }

    private void showRecordPaymentDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, 0);

        EditText etAmount = new EditText(this);
        etAmount.setHint(getString(R.string.invoice_payment_amount));
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format(Locale.US, "%.2f", currentInvoice.getOutstandingBalance() / 100.0));
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
                .setTitle(getString(R.string.invoice_record_payment))
                .setView(layout)
                .setPositiveButton(getString(R.string.action_save), (d, w) -> {
                    try {
                        double bdt = Double.parseDouble(etAmount.getText().toString().trim());
                        int amountMinor = (int) Math.round(bdt * 100);
                        if (amountMinor <= 0 || amountMinor > currentInvoice.getOutstandingBalance()) {
                            showMessage(rootContent, getString(R.string.invoice_err_invalid_amount));
                            return;
                        }
                        FinancialTransaction txn = txnRepo.recordPaymentWithAllocations(
                                sessionManager.getActiveAccountId(),
                                currentInvoice.getStudentId(),
                                amountMinor,
                                spMethod.getSelectedItem().toString(),
                                etRef.getText().toString().trim(),
                                etNotes.getText().toString().trim(),
                                Collections.singletonMap(currentInvoice.getId(), amountMinor)
                        );
                        if (txn != null) {
                            showMessage(rootContent, getString(R.string.invoice_payment_saved_fmt, txn.getTxnIdDisplay()));
                            refreshDetails();
                        }
                    } catch (NumberFormatException e) {
                        showMessage(rootContent, getString(R.string.invoice_err_invalid_amount));
                    }
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void showTransactionActionsDialog(FinancialTransaction txn) {
        String[] options = {
                getString(R.string.invoice_share_receipt_pdf),
                getString(R.string.invoice_issue_refund)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(txn.getTxnIdDisplay())
                .setItems(options, (d, which) -> {
                    if (which == 0 && currentStudent != null) {
                        try {
                            File file = FinancialPdfGenerator.generatePaymentReceiptPdf(this, "Tutor", currentStudent, txn);
                            FinancialPdfGenerator.sharePdf(this, file, getString(R.string.invoice_share_receipt_pdf));
                        } catch (Exception e) {
                            showMessage(rootContent, e.getMessage());
                        }
                    } else if (which == 1 && FinancialTransaction.TYPE_PAYMENT.equals(txn.getType())) {
                        boolean ok = txnRepo.recordRefundForTransaction(
                                sessionManager.getActiveAccountId(), txn.getId(), txn.getAmountMinor(), "Tutor refund");
                        if (ok) {
                            showMessage(rootContent, getString(R.string.invoice_refund_saved_msg));
                            refreshDetails();
                        }
                    }
                })
                .show();
    }

    private void exportInvoicePdf() {
        if (currentStudent == null) return;
        try {
            File pdf = FinancialPdfGenerator.generateInvoicePdf(
                    this, "Tutor", currentStudent, currentInvoice, currentItems, currentTransactions);
            FinancialPdfGenerator.sharePdf(this, pdf, getString(R.string.invoice_export_pdf));
        } catch (Exception e) {
            showMessage(rootContent, e.getMessage());
        }
    }

    private void addHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(20f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
        tv.setPadding(0, 0, 0, dp(10));
        rootContent.addView(tv);
    }

    private void addSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
        tv.setPadding(0, dp(20), 0, dp(6));
        rootContent.addView(tv);
    }

    private TextView addLine(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
        tv.setPadding(0, dp(6), 0, dp(6));
        rootContent.addView(tv);
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
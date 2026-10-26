package com.abdussamadcodes.tutorsync.ui.finance;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        invoiceId = getIntent().getStringExtra("invoice_id");
        sessionManager = new SessionManager(this);
        invoiceRepo = new InvoiceRepository();
        txnRepo = new TransactionRepository();
        studentRepo = new StudentRepository();

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        rootContent = new LinearLayout(this);
        rootContent.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        rootContent.setPadding(pad, pad, pad, pad);
        scrollView.addView(rootContent);
        setContentView(scrollView);
        applySystemBarInsets(scrollView);

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

        addHeader(currentInvoice.getInvoiceIdDisplay() + " · " + currentInvoice.getStatus());
        addLine("Student: " + (currentStudent != null ? currentStudent.getFormattedDisplayName() : "Unknown"));
        addLine("Billing Period: " + currentInvoice.getBillingPeriod());
        addLine("Issue Date: " + df.format(new Date(currentInvoice.getIssueDate())) +
                " | Due: " + df.format(new Date(currentInvoice.getDueDate())));
        addLine(String.format(Locale.US, "Total: ৳ %.2f | Paid: ৳ %.2f | Balance: ৳ %.2f",
                currentInvoice.getTotalAmount() / 100.0,
                currentInvoice.getPaidAmount() / 100.0,
                currentInvoice.getOutstandingBalance() / 100.0));

        addSectionTitle("Itemized Charges");
        for (InvoiceItem item : currentItems) {
            addLine(String.format(Locale.US, "• %s [%s] — ৳ %.2f",
                    item.getDescription(), item.getBillingModel(), item.getAmountMinor() / 100.0));
        }

        addSectionTitle("Payments & Refunds");
        if (currentTransactions.isEmpty()) {
            addLine("No payments recorded yet.");
        } else {
            for (FinancialTransaction txn : currentTransactions) {
                TextView tv = addLine(String.format(Locale.US, "%s · %s · %s: ৳ %.2f (%s)",
                        txn.getTxnIdDisplay(), dtf.format(new Date(txn.getTimestamp())),
                        txn.getMethod(), txn.getAmountMinor() / 100.0, txn.getType()));
                tv.setOnClickListener(v -> showTransactionActionsDialog(txn));
            }
        }

        if (currentInvoice.getOutstandingBalance() > 0) {
            MaterialButton btnPay = new MaterialButton(this);
            btnPay.setText("Record Payment");
            btnPay.setOnClickListener(v -> showRecordPaymentDialog());
            rootContent.addView(btnPay);

            MaterialButton btnRemind = new MaterialButton(this);
            btnRemind.setText("Send Reminder (Bangla / English)");
            btnRemind.setOnClickListener(v -> {
                if (currentStudent != null) {
                    boolean isBn = AppPreferences.LANG_BANGLA.equals(appPreferences.getLanguageCode());
                    PaymentReminderHelper.showEditableReminderDialog(this, currentStudent, currentInvoice, isBn);
                }
            });
            rootContent.addView(btnRemind);
        }

        MaterialButton btnPdf = new MaterialButton(this);
        btnPdf.setText("Export / Share Invoice PDF");
        btnPdf.setOnClickListener(v -> exportInvoicePdf());
        rootContent.addView(btnPdf);
    }

    private void showRecordPaymentDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText etAmount = new EditText(this);
        etAmount.setHint("Amount in BDT");
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format(Locale.US, "%.2f", currentInvoice.getOutstandingBalance() / 100.0));
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
                .setTitle("Record Payment")
                .setView(layout)
                .setPositiveButton("Save Payment", (d, w) -> {
                    try {
                        double bdt = Double.parseDouble(etAmount.getText().toString().trim());
                        int amountMinor = (int) Math.round(bdt * 100);
                        if (amountMinor <= 0 || amountMinor > currentInvoice.getOutstandingBalance()) {
                            showMessage(rootContent, "Amount must be between ৳0.01 and outstanding balance.");
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
                            showMessage(rootContent, "Payment recorded: " + txn.getTxnIdDisplay());
                            refreshDetails();
                        }
                    } catch (NumberFormatException e) {
                        showMessage(rootContent, "Invalid amount.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTransactionActionsDialog(FinancialTransaction txn) {
        String[] options = {"Share Payment Receipt PDF", "Issue Refund / Correction"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(txn.getTxnIdDisplay())
                .setItems(options, (d, which) -> {
                    if (which == 0 && currentStudent != null) {
                        try {
                            File file = FinancialPdfGenerator.generatePaymentReceiptPdf(this, "Tutor", currentStudent, txn);
                            FinancialPdfGenerator.sharePdf(this, file, "Share Payment Receipt");
                        } catch (Exception e) {
                            showMessage(rootContent, "Failed to generate receipt PDF.");
                        }
                    } else if (which == 1 && FinancialTransaction.TYPE_PAYMENT.equals(txn.getType())) {
                        boolean ok = txnRepo.recordRefundForTransaction(
                                sessionManager.getActiveAccountId(), txn.getId(), txn.getAmountMinor(), "Tutor refund");
                        if (ok) {
                            showMessage(rootContent, "Refund recorded and invoice balance updated.");
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
            FinancialPdfGenerator.sharePdf(this, pdf, "Share Invoice PDF");
        } catch (Exception e) {
            showMessage(rootContent, "PDF generation failed: " + e.getMessage());
        }
    }

    private void addHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(20f);
        tv.setPadding(0, 0, 0, 12);
        rootContent.addView(tv);
    }

    private void addSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setPadding(0, 24, 0, 8);
        rootContent.addView(tv);
    }

    private TextView addLine(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setPadding(0, 6, 0, 6);
        rootContent.addView(tv);
        return tv;
    }
}
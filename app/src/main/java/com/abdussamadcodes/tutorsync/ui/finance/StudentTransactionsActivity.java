package com.abdussamadcodes.tutorsync.ui.finance;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.TransactionRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.util.FinancialPdfGenerator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StudentTransactionsActivity extends BaseActivity {

    private String studentId;
    private Student currentStudent;
    private SessionManager sessionManager;
    private StudentRepository studentRepo;
    private InvoiceRepository invoiceRepo;
    private TransactionRepository txnRepo;
    private LinearLayout rootContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        studentId = getIntent().getStringExtra("student_id");
        sessionManager = new SessionManager(this);
        studentRepo = new StudentRepository();
        invoiceRepo = new InvoiceRepository();
        txnRepo = new TransactionRepository();

        ScrollView scrollView = new ScrollView(this);
        rootContent = new LinearLayout(this);
        rootContent.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        rootContent.setPadding(pad, pad, pad, pad);
        scrollView.addView(rootContent);
        setContentView(scrollView);
        applySystemBarInsets(scrollView);

        refreshHistory();
    }

    private void refreshHistory() {
        rootContent.removeAllViews();
        String accountId = sessionManager.getActiveAccountId();
        currentStudent = studentRepo.getStudentById(studentId);
        if (currentStudent == null) {
            finish();
            return;
        }

        List<Invoice> allAccountInvoices = invoiceRepo.getFilteredInvoicesForAccount(accountId, "ALL");
        List<Invoice> studentOpenInvoices = new ArrayList<>();
        int totalBilled = 0;
        int totalPaid = 0;
        int totalDue = 0;

        for (Invoice inv : allAccountInvoices) {
            if (inv.getStudentId().equals(studentId)) {
                totalBilled += inv.getTotalAmount();
                totalPaid += inv.getPaidAmount();
                totalDue += inv.getOutstandingBalance();
                if (inv.getOutstandingBalance() > 0) {
                    studentOpenInvoices.add(inv);
                }
            }
        }
        // Sort oldest first for FIFO allocation across multiple invoices
        Collections.reverse(studentOpenInvoices);

        addTitle("Financial History · " + currentStudent.getFormattedDisplayName());
        addLine(String.format(Locale.US, "Total Billed: ৳%.2f | Total Paid: ৳%.2f | Outstanding Balance: ৳%.2f",
                totalBilled / 100.0, totalPaid / 100.0, totalDue / 100.0));

        if (totalDue > 0) {
            final int maxPayableMinor = totalDue;
            MaterialButton btnPayMulti = new MaterialButton(this);
            btnPayMulti.setText("Record Payment (Auto-Allocate Across Open Invoices)");
            btnPayMulti.setOnClickListener(v -> showMultiInvoicePaymentDialog(studentOpenInvoices, maxPayableMinor));
            rootContent.addView(btnPayMulti);
        }

        addSection("Transactions (Payments & Refunds)");
        List<FinancialTransaction> txns = txnRepo.getTransactionsForStudent(accountId, studentId);
        SimpleDateFormat dtf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        if (txns.isEmpty()) {
            addLine("No transactions recorded for this student yet.");
        } else {
            for (FinancialTransaction txn : txns) {
                String invoicesStr = txn.getRelatedInvoiceDisplayIds().isEmpty()
                        ? "N/A" : String.join(", ", txn.getRelatedInvoiceDisplayIds());
                String refStr = (txn.getReferenceNo() != null && !txn.getReferenceNo().isEmpty())
                        ? " | Ref: " + txn.getReferenceNo() : "";
                String text = String.format(Locale.US,
                        "%s (%s) — ৳%.2f\n%s · %s%s\nInvoices: %s",
                        txn.getTxnIdDisplay(), txn.getType(), txn.getAmountMinor() / 100.0,
                        dtf.format(new Date(txn.getTimestamp())), txn.getMethod(), refStr, invoicesStr);
                if (txn.getNotes() != null && !txn.getNotes().isEmpty()) {
                    text += "\nNote: " + txn.getNotes();
                }
                TextView tv = addLine(text);
                tv.setOnClickListener(v -> showTransactionOptions(txn));
            }
        }
    }

    private void showMultiInvoicePaymentDialog(List<Invoice> openInvoicesChronological, int totalDueMinor) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
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
                            showMessage(rootContent, "Amount must be between ৳0.01 and total outstanding balance.");
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
                            showMessage(rootContent, "Payment allocated across invoices: " + created.getTxnIdDisplay());
                            refreshHistory();
                        }
                    } catch (NumberFormatException e) {
                        showMessage(rootContent, "Invalid amount.");
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
                            showMessage(rootContent, "Failed to generate receipt PDF.");
                        }
                    } else if (which == 1 && FinancialTransaction.TYPE_PAYMENT.equals(txn.getType())) {
                        boolean ok = txnRepo.recordRefundForTransaction(
                                sessionManager.getActiveAccountId(), txn.getId(), txn.getAmountMinor(), "Tutor refund");
                        if (ok) {
                            showMessage(rootContent, "Refund recorded and invoice balances updated.");
                            refreshHistory();
                        }
                    }
                })
                .show();
    }

    private void addTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(19f);
        tv.setPadding(0, 0, 0, 12);
        rootContent.addView(tv);
    }

    private void addSection(String text) {
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
        tv.setPadding(0, 10, 0, 10);
        rootContent.addView(tv);
        return tv;
    }
}
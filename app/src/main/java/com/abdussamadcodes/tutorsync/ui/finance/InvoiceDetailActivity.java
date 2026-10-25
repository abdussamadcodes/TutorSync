package com.abdussamadcodes.tutorsync.ui.finance;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityInvoiceDetailBinding;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Payment;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.PaymentRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class InvoiceDetailActivity extends BaseActivity {

    private ActivityInvoiceDetailBinding binding;
    private InvoiceRepository invoiceRepo;
    private PaymentRepository paymentRepo;
    private SessionManager sessionManager;
    private String invoiceId;
    private String studentName;
    private String billingPeriod;
    private int totalAmount;
    private int currentPaidAmount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInvoiceDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.invoiceDetailRoot);

        sessionManager = new SessionManager(this);
        invoiceRepo = new InvoiceRepository();
        paymentRepo = new PaymentRepository();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Extract passed data
        invoiceId = getIntent().getStringExtra("invoice_id");
        studentName = getIntent().getStringExtra("student_name");
        billingPeriod = getIntent().getStringExtra("billing_period");
        totalAmount = getIntent().getIntExtra("total_amount", 0);
        currentPaidAmount = getIntent().getIntExtra("paid_amount", 0);
        String status = getIntent().getStringExtra("status");

        if (invoiceId == null) {
            finish();
            return;
        }

        setupUI(status);
        loadPayments();

        binding.btnSavePayment.setOnClickListener(v -> recordPayment());
    }

    private void setupUI(String status) {
        binding.tvStudentName.setText(studentName);
        binding.tvBillingPeriod.setText(billingPeriod);
        binding.tvTotalAmount.setText(String.format(Locale.getDefault(), "৳ %.2f", totalAmount / 100.0));
        updateDueAmountUI(status);
    }

    private void updateDueAmountUI(String status) {
        binding.tvPaidAmount.setText(String.format(Locale.getDefault(), "৳ %.2f", currentPaidAmount / 100.0));
        int dueAmount = totalAmount - currentPaidAmount;
        binding.tvDueAmount.setText(String.format(Locale.getDefault(), "৳ %.2f", dueAmount / 100.0));

        binding.chipStatus.setText(status);
        if (Invoice.STATUS_PAID.equals(status)) {
            binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            binding.chipStatus.setTextColor(Color.WHITE);
            binding.cardRecordPayment.setVisibility(View.GONE);
        } else if (Invoice.STATUS_PARTIAL.equals(status)) {
            binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FF9800")));
            binding.chipStatus.setTextColor(Color.WHITE);
            binding.cardRecordPayment.setVisibility(View.VISIBLE);
        } else {
            binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F44336")));
            binding.chipStatus.setTextColor(Color.WHITE);
            binding.cardRecordPayment.setVisibility(View.VISIBLE);
        }
    }

    private void loadPayments() {
        List<Payment> payments = paymentRepo.getPaymentsForInvoice(invoiceId);
        binding.layoutPaymentHistory.removeAllViews();

        if (payments.isEmpty()) {
            binding.tvEmptyPayments.setVisibility(View.VISIBLE);
            binding.layoutPaymentHistory.setVisibility(View.GONE);
        } else {
            binding.tvEmptyPayments.setVisibility(View.GONE);
            binding.layoutPaymentHistory.setVisibility(View.VISIBLE);

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            for (Payment p : payments) {
                View paymentView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, binding.layoutPaymentHistory, false);
                TextView text1 = paymentView.findViewById(android.R.id.text1);
                TextView text2 = paymentView.findViewById(android.R.id.text2);

                text1.setText(String.format(Locale.getDefault(), "৳ %.2f", p.getAmount() / 100.0));
                text1.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorPrimary));
                text1.setTypeface(null, android.graphics.Typeface.BOLD);
                text2.setText(sdf.format(new Date(p.getPaymentDate())));

                binding.layoutPaymentHistory.addView(paymentView);

                // Add a simple divider
                View divider = new View(this);
                divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant));
                binding.layoutPaymentHistory.addView(divider);
            }
        }
    }

    private int resolveThemeColor(int attrRes) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    private void recordPayment() {
        String amountStr = binding.etPaymentAmount.getText() != null ? binding.etPaymentAmount.getText().toString().trim() : "";
        if (amountStr.isEmpty()) {
            binding.etPaymentAmount.setError(getString(R.string.auth_err_empty));
            return;
        }

        double enteredAmount;
        try {
            enteredAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            binding.etPaymentAmount.setError("Invalid amount");
            return;
        }

        int amountMinor = (int) (enteredAmount * 100);
        if (amountMinor <= 0) {
            binding.etPaymentAmount.setError("Amount must be greater than 0");
            return;
        }

        int remainingDue = totalAmount - currentPaidAmount;
        if (amountMinor > remainingDue) {
            binding.etPaymentAmount.setError("Amount exceeds due balance");
            return;
        }

        Payment newPayment = new Payment(
                UUID.randomUUID().toString(),
                sessionManager.getActiveAccountId(),
                invoiceId,
                amountMinor,
                System.currentTimeMillis(),
                "Cash" // Simplified for now
        );

        if (paymentRepo.insertPayment(newPayment)) {
            currentPaidAmount += amountMinor;
            String newStatus = (currentPaidAmount >= totalAmount) ? Invoice.STATUS_PAID : Invoice.STATUS_PARTIAL;

            if (invoiceRepo.updateInvoicePayment(invoiceId, currentPaidAmount, newStatus)) {
                binding.etPaymentAmount.setText("");
                updateDueAmountUI(newStatus);
                loadPayments();
                showMessage(binding.getRoot(), "Payment recorded successfully");
            } else {
                showMessage(binding.getRoot(), "Failed to update invoice status");
            }
        } else {
            showMessage(binding.getRoot(), "Failed to save payment");
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.finance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityInvoiceListBinding;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class InvoiceListActivity extends BaseActivity {

    private ActivityInvoiceListBinding binding;
    private InvoiceAdapter adapter;
    private InvoiceRepository invoiceRepo;
    private StudentRepository studentRepo;
    private SessionManager sessionManager;
    private String activeFilter = "ALL";
    private final AtomicBoolean isGeneratingInvoices = new AtomicBoolean(false);
    private boolean legacyIdsMigrated = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInvoiceListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.invoiceListRoot);

        sessionManager = new SessionManager(this);
        invoiceRepo = new InvoiceRepository();
        studentRepo = new StudentRepository();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new InvoiceAdapter(invoice -> {
            Intent intent = new Intent(this, InvoiceDetailActivity.class);
            intent.putExtra("invoice_id", invoice.getId());
            startActivity(intent);
        });

        binding.rvInvoices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvInvoices.setAdapter(adapter);

        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipFilterPaid) activeFilter = Invoice.STATUS_PAID;
            else if (id == R.id.chipFilterUnpaid) activeFilter = Invoice.STATUS_UNPAID;
            else if (id == R.id.chipFilterPartial) activeFilter = Invoice.STATUS_PARTIAL;
            else if (id == R.id.chipFilterOverdue) activeFilter = Invoice.STATUS_OVERDUE;
            else activeFilter = "ALL";
            loadInvoices();
        });

        binding.fabGenerate.setOnClickListener(v -> generateDueInvoicesAsync());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInvoices();
    }

    private void generateDueInvoicesAsync() {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        if (!isGeneratingInvoices.compareAndSet(false, true)) {
            return;
        }
        binding.fabGenerate.setEnabled(false);

        AppExecutors.getInstance().diskIO().execute(() -> {
            int count = 0;
            Exception error = null;
            try {
                count = invoiceRepo.generateDueInvoicesForAccount(accountId);
            } catch (Exception e) {
                error = e;
            } finally {
                isGeneratingInvoices.set(false);
            }

            final int generatedCount = count;
            final Exception finalError = error;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed() || binding == null) return;
                binding.fabGenerate.setEnabled(true);

                if (finalError != null) {
                    String msg = finalError.getMessage() != null ? finalError.getMessage() : "Failed to generate invoices";
                    showMessage(binding.getRoot(), msg);
                    return;
                }

                if (generatedCount > 0) {
                    showMessage(binding.getRoot(), getString(R.string.invoice_generated_count_fmt, generatedCount));
                    loadInvoices();
                } else {
                    showMessage(binding.getRoot(), getString(R.string.invoice_none_needed));
                }
            });
        });
    }

    private void loadInvoices() {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        final String filterSnapshot = activeFilter;
        final boolean shouldMigrate = !legacyIdsMigrated;

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                if (shouldMigrate) {
                    studentRepo.migrateLegacyStudentIds(accountId);
                    invoiceRepo.migrateLegacyInvoiceIds(accountId);
                }

                final List<Invoice> allInvoices = invoiceRepo.getFilteredInvoicesForAccount(accountId, "ALL");
                int billedSum = 0;
                int outstandingSum = 0;
                int overdueCounter = 0;
                final List<Invoice> filteredInvoices = new ArrayList<>();

                for (Invoice inv : allInvoices) {
                    billedSum += inv.getTotalAmount();
                    outstandingSum += inv.getOutstandingBalance();
                    if (inv.isOverdue()) overdueCounter++;

                    if ("ALL".equalsIgnoreCase(filterSnapshot)) {
                        filteredInvoices.add(inv);
                    } else if ("OVERDUE".equalsIgnoreCase(filterSnapshot) && inv.isOverdue()) {
                        filteredInvoices.add(inv);
                    } else if (filterSnapshot.equalsIgnoreCase(inv.getStatus())) {
                        filteredInvoices.add(inv);
                    }
                }

                final int totalBilledMinor = billedSum;
                final int totalOutstandingMinor = outstandingSum;
                final int overdueCount = overdueCounter;
                final int totalReceivedMinor = invoiceRepo.getTotalRevenueForAccount(accountId);
                final List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);

                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (isFinishing() || isDestroyed() || binding == null) return;
                    if (!accountId.equals(sessionManager.getActiveAccountId())) return;
                    legacyIdsMigrated = true;

                    binding.tvFinancialSummary.setText(getString(R.string.invoice_summary_dashboard_fmt,
                            totalBilledMinor / 100.0,
                            totalReceivedMinor / 100.0,
                            totalOutstandingMinor / 100.0,
                            overdueCount));

                    adapter.setData(filteredInvoices, students);

                    if (filteredInvoices.isEmpty()) {
                        binding.tvEmptyState.setVisibility(View.VISIBLE);
                        binding.rvInvoices.setVisibility(View.GONE);
                    } else {
                        binding.tvEmptyState.setVisibility(View.GONE);
                        binding.rvInvoices.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }
}
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
import java.util.ArrayList;
import java.util.List;

public class InvoiceListActivity extends BaseActivity {

    private ActivityInvoiceListBinding binding;
    private InvoiceAdapter adapter;
    private InvoiceRepository invoiceRepo;
    private StudentRepository studentRepo;
    private SessionManager sessionManager;
    private String activeFilter = "ALL";

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

        binding.fabGenerate.setOnClickListener(v -> {
            String accountId = sessionManager.getActiveAccountId();
            int count = invoiceRepo.generateDueInvoicesForAccount(accountId);
            if (count > 0) {
                showMessage(binding.getRoot(), getString(R.string.invoice_generated_count_fmt, count));
                loadInvoices();
            } else {
                showMessage(binding.getRoot(), getString(R.string.invoice_none_needed));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String accountId = sessionManager.getActiveAccountId();
        if (!accountId.isEmpty()) {
            studentRepo.migrateLegacyStudentIds(accountId);
            invoiceRepo.migrateLegacyInvoiceIds(accountId);
            loadInvoices();
        }
    }

    private void loadInvoices() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        List<Invoice> allInvoices = invoiceRepo.getFilteredInvoicesForAccount(accountId, "ALL");
        int totalBilledMinor = 0;
        int totalOutstandingMinor = 0;
        int overdueCount = 0;
        List<Invoice> filteredInvoices = new ArrayList<>();

        for (Invoice inv : allInvoices) {
            totalBilledMinor += inv.getTotalAmount();
            totalOutstandingMinor += inv.getOutstandingBalance();
            if (inv.isOverdue()) overdueCount++;

            if ("ALL".equalsIgnoreCase(activeFilter)) {
                filteredInvoices.add(inv);
            } else if ("OVERDUE".equalsIgnoreCase(activeFilter) && inv.isOverdue()) {
                filteredInvoices.add(inv);
            } else if (activeFilter.equalsIgnoreCase(inv.getStatus())) {
                filteredInvoices.add(inv);
            }
        }

        int totalReceivedMinor = invoiceRepo.getTotalRevenueForAccount(accountId);

        binding.tvFinancialSummary.setText(getString(R.string.invoice_summary_dashboard_fmt,
                totalBilledMinor / 100.0,
                totalReceivedMinor / 100.0,
                totalOutstandingMinor / 100.0,
                overdueCount));

        List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);

        adapter.setData(filteredInvoices, students);

        if (filteredInvoices.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvInvoices.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvInvoices.setVisibility(View.VISIBLE);
        }
    }
}
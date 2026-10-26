package com.abdussamadcodes.tutorsync.ui.finance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.abdussamadcodes.tutorsync.databinding.ActivityInvoiceListBinding;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.List;

public class InvoiceListActivity extends BaseActivity {

    private ActivityInvoiceListBinding binding;
    private InvoiceAdapter adapter;
    private InvoiceRepository invoiceRepo;
    private StudentRepository studentRepo;
    private SessionManager sessionManager;
    private String activeFilter = "ALL"; // ALL, PAID, UNPAID, PARTIAL, OVERDUE

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

        binding.fabGenerate.setOnClickListener(v -> {
            String accountId = sessionManager.getActiveAccountId();
            int count = invoiceRepo.generateDueInvoicesForAccount(accountId);
            if (count > 0) {
                showMessage(binding.getRoot(), "Generated " + count + " new invoice(s).");
                loadInvoices();
            } else {
                showMessage(binding.getRoot(), "All active schedules and confirmed classes are already billed.");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String accountId = sessionManager.getActiveAccountId();
        if (!accountId.isEmpty()) {
            invoiceRepo.migrateLegacyInvoiceIds(accountId);
            loadInvoices();
        }
    }

    public void setFilter(String filter) {
        this.activeFilter = filter;
        loadInvoices();
    }

    private void loadInvoices() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        List<Invoice> invoices = invoiceRepo.getFilteredInvoicesForAccount(accountId, activeFilter);
        List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);

        adapter.setData(invoices, students);

        if (invoices.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvInvoices.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvInvoices.setVisibility(View.VISIBLE);
        }
    }
}
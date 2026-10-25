package com.abdussamadcodes.tutorsync.ui.finance;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.abdussamadcodes.tutorsync.databinding.ActivityInvoiceListBinding;
import com.abdussamadcodes.tutorsync.model.FeeAgreement;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository;
import com.abdussamadcodes.tutorsync.repository.local.FeeAgreementRepository;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class InvoiceListActivity extends BaseActivity {

    private ActivityInvoiceListBinding binding;
    private InvoiceAdapter adapter;
    private InvoiceRepository invoiceRepo;
    private FeeAgreementRepository feeRepo;
    private StudentRepository studentRepo;
    private AttendanceRepository attendanceRepo;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInvoiceListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.invoiceListRoot);

        sessionManager = new SessionManager(this);
        invoiceRepo = new InvoiceRepository();
        feeRepo = new FeeAgreementRepository();
        studentRepo = new StudentRepository();
        attendanceRepo = new AttendanceRepository();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new InvoiceAdapter(invoice -> {
            String studentName = "Unknown Student";
            // Get student name (we have to search the list we already fetched)
            List<Student> students = studentRepo.getActiveStudentsForAccount(sessionManager.getActiveAccountId());
            for (Student s : students) {
                if (s.getId().equals(invoice.getStudentId())) {
                    studentName = s.getName();
                    break;
                }
            }

            android.content.Intent intent = new android.content.Intent(this, InvoiceDetailActivity.class);
            intent.putExtra("invoice_id", invoice.getId());
            intent.putExtra("student_name", studentName);
            intent.putExtra("billing_period", invoice.getBillingPeriod());
            intent.putExtra("total_amount", invoice.getTotalAmount());
            intent.putExtra("paid_amount", invoice.getPaidAmount());
            intent.putExtra("status", invoice.getStatus());
            startActivity(intent);
        });
        binding.rvInvoices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvInvoices.setAdapter(adapter);

        binding.fabGenerate.setOnClickListener(v -> generateMonthlyInvoices());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInvoices();
    }

    private void loadInvoices() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        List<Invoice> invoices = invoiceRepo.getInvoicesForAccount(accountId);
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

    private void generateMonthlyInvoices() {
        String accountId = sessionManager.getActiveAccountId();
        String currentMonth = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date());
        List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);
        int generatedCount = 0;

        for (Student student : students) {
            FeeAgreement agreement = feeRepo.getAgreementForStudent(student.getId());
            if (agreement == null) continue; // No fee set for this student

            // Check if we already generated an invoice for this student for this month
            boolean alreadyExists = false;
            for (Invoice existing : invoiceRepo.getInvoicesForAccount(accountId)) {
                if (existing.getStudentId().equals(student.getId()) && existing.getBillingPeriod().equals(currentMonth)) {
                    alreadyExists = true;
                    break;
                }
            }
            if (alreadyExists) continue;

            int totalAmountMinor = 0;

            if (FeeAgreement.TYPE_MONTHLY.equals(agreement.getFeeType())) {
                totalAmountMinor = agreement.getAmountMinorUnits();
            } else if (FeeAgreement.TYPE_PER_CLASS.equals(agreement.getFeeType())) {
                // Per class logic: count present classes this month
                int presentCount = attendanceRepo.getPresentCountForStudentThisMonth(student.getId());
                totalAmountMinor = presentCount * agreement.getAmountMinorUnits();
                if (totalAmountMinor == 0) continue; // Skip if they had 0 classes
            }

            Invoice newInvoice = new Invoice(
                    UUID.randomUUID().toString(),
                    accountId,
                    student.getId(),
                    currentMonth,
                    totalAmountMinor,
                    0,
                    Invoice.STATUS_UNPAID,
                    System.currentTimeMillis() + 604800000L, // Due in 7 days
                    System.currentTimeMillis()
            );

            if (invoiceRepo.insertInvoice(newInvoice)) {
                generatedCount++;
            }
        }

        if (generatedCount > 0) {
            showMessage(binding.getRoot(), "Generated " + generatedCount + " invoices!");
            loadInvoices();
        } else {
            showMessage(binding.getRoot(), "No new invoices needed this month.");
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.finance;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.databinding.ItemInvoiceBinding;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.ViewHolder> {

    private final List<Invoice> invoices = new ArrayList<>();
    private final List<Student> students = new ArrayList<>();
    private final OnInvoiceClickListener listener;
    private final SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public interface OnInvoiceClickListener {
        void onInvoiceClick(Invoice invoice);
    }

    public InvoiceAdapter(OnInvoiceClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<Invoice> newInvoices, List<Student> newStudents) {
        invoices.clear();
        invoices.addAll(newInvoices);
        students.clear();
        students.addAll(newStudents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemInvoiceBinding binding = ItemInvoiceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(invoices.get(position));
    }

    @Override
    public int getItemCount() {
        return invoices.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemInvoiceBinding binding;

        ViewHolder(ItemInvoiceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onInvoiceClick(invoices.get(pos));
                }
            });
        }

        void bind(Invoice invoice) {
            binding.tvInvoiceId.setText(invoice.getInvoiceIdDisplay());

            String studentName = "Unknown Student";
            for (Student s : students) {
                if (s.getId().equals(invoice.getStudentId())) {
                    studentName = s.getFormattedDisplayName();
                    break;
                }
            }
            binding.tvStudentName.setText(studentName);

            String datesInfo = invoice.getBillingPeriod() +
                    "\nIssued: " + df.format(new Date(invoice.getIssueDate())) +
                    " · Due: " + df.format(new Date(invoice.getDueDate()));
            binding.tvBillingPeriod.setText(datesInfo);

            String amountText = String.format(Locale.US,
                    "Total: ৳%.2f | Paid: ৳%.2f | Due: ৳%.2f",
                    invoice.getTotalAmount() / 100.0,
                    invoice.getPaidAmount() / 100.0,
                    invoice.getOutstandingBalance() / 100.0);
            binding.tvAmount.setText(amountText);

            String status = invoice.getStatus();
            binding.chipStatus.setText(status);
            if (Invoice.STATUS_PAID.equals(status)) {
                binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#2E7D32")));
                binding.chipStatus.setTextColor(Color.WHITE);
            } else if (Invoice.STATUS_PARTIAL.equals(status)) {
                binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#EF6C00")));
                binding.chipStatus.setTextColor(Color.WHITE);
            } else if (Invoice.STATUS_OVERDUE.equals(status)) {
                binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#B71C1C")));
                binding.chipStatus.setTextColor(Color.WHITE);
            } else {
                binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#D32F2F")));
                binding.chipStatus.setTextColor(Color.WHITE);
            }
        }
    }
}
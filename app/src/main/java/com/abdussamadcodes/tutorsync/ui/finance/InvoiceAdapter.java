package com.abdussamadcodes.tutorsync.ui.finance;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ItemInvoiceBinding;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import java.util.ArrayList;
import java.util.List;

public class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.ViewHolder> {

    private final List<Invoice> invoices = new ArrayList<>();
    private final List<Student> students = new ArrayList<>();
    private final OnInvoiceClickListener listener;

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
            String studentName = "Unknown Student";
            for (Student s : students) {
                if (s.getId().equals(invoice.getStudentId())) {
                    // Use the new centralized helper
                    studentName = s.getFormattedDisplayName();
                    break;
                }
            }
            binding.tvStudentName.setText(studentName);
            binding.tvBillingPeriod.setText(invoice.getBillingPeriod());

            String amountText = String.format("৳ %.2f", invoice.getTotalAmount() / 100.0);
            if (invoice.getPaidAmount() > 0 && invoice.getPaidAmount() < invoice.getTotalAmount()) {
                amountText += String.format(" (Paid: ৳ %.2f)", invoice.getPaidAmount() / 100.0);
            }
            binding.tvAmount.setText(amountText);

            binding.chipStatus.setText(invoice.getStatus());
            if (Invoice.STATUS_PAID.equals(invoice.getStatus())) {
                binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Green
                binding.chipStatus.setTextColor(Color.WHITE);
            } else if (Invoice.STATUS_PARTIAL.equals(invoice.getStatus())) {
                binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FF9800"))); // Orange
                binding.chipStatus.setTextColor(Color.WHITE);
            } else {
                binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F44336"))); // Red
                binding.chipStatus.setTextColor(Color.WHITE);
            }
        }
    }
}
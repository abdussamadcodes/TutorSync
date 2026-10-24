package com.abdussamadcodes.tutorsync.ui.schedule;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.databinding.ItemOccurrenceBinding;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.Student;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OccurrenceAdapter extends RecyclerView.Adapter<OccurrenceAdapter.ViewHolder> {

    private final List<ClassOccurrence> occurrences = new ArrayList<>();
    private final List<Student> students = new ArrayList<>();
    private final OnOccurrenceClickListener listener;

    public interface OnOccurrenceClickListener {
        void onOccurrenceClick(ClassOccurrence occurrence);
    }

    public OccurrenceAdapter(OnOccurrenceClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<ClassOccurrence> newOccurrences, List<Student> newStudents) {
        occurrences.clear();
        occurrences.addAll(newOccurrences);
        students.clear();
        students.addAll(newStudents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOccurrenceBinding binding = ItemOccurrenceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(occurrences.get(position));
    }

    @Override
    public int getItemCount() {
        return occurrences.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemOccurrenceBinding binding;

        ViewHolder(ItemOccurrenceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onOccurrenceClick(occurrences.get(pos));
                }
            });
        }

        void bind(ClassOccurrence occurrence) {
            String studentName = "Unknown Student";
            for (Student s : students) {
                if (s.getId().equals(occurrence.getStudentId())) {
                    studentName = s.getName();
                    break;
                }
            }
            binding.tvStudentName.setText(studentName);

            // Format date and time
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            String dateStr = sdf.format(new Date(occurrence.getDate()));

            int hours = occurrence.getStartTimeMinutes() / 60;
            int mins = occurrence.getStartTimeMinutes() % 60;
            String ampm = hours >= 12 ? "PM" : "AM";
            hours = hours % 12;
            if (hours == 0) hours = 12;
            String timeStr = String.format(Locale.getDefault(), "%02d:%02d %s", hours, mins, ampm);

            binding.tvDateTime.setText(dateStr + " at " + timeStr + " (" + occurrence.getDurationMinutes() + " mins)");
        }
    }
}
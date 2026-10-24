package com.abdussamadcodes.tutorsync.ui.schedule;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.databinding.ItemOccurrenceBinding;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OccurrenceAdapter extends RecyclerView.Adapter<OccurrenceAdapter.ViewHolder> {

    private final List<ClassOccurrence> occurrences = new ArrayList<>();
    private final List<Student> students = new ArrayList<>();
    private final List<Subject> subjects = new ArrayList<>();
    private final OnOccurrenceClickListener listener;

    public interface OnOccurrenceClickListener {
        void onOccurrenceClick(ClassOccurrence occurrence);
    }

    public OccurrenceAdapter(OnOccurrenceClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<ClassOccurrence> newOccurrences, List<Student> newStudents, List<Subject> newSubjects) {
        occurrences.clear();
        occurrences.addAll(newOccurrences);
        students.clear();
        students.addAll(newStudents);
        subjects.clear();
        subjects.addAll(newSubjects);
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

            String subjectName = "Unknown Subject";
            for (Subject s : subjects) {
                if (s.getId().equals(occurrence.getSubjectId())) {
                    subjectName = s.getTitle();
                    break;
                }
            }
            binding.tvSubjectName.setText(subjectName);

            int hours = occurrence.getStartTimeMinutes() / 60;
            int mins = occurrence.getStartTimeMinutes() % 60;
            String ampm = hours >= 12 ? "PM" : "AM";
            hours = hours % 12;
            if (hours == 0) hours = 12;

            binding.tvDateTime.setText(String.format(Locale.getDefault(), "Today at %02d:%02d %s (%d mins)", hours, mins, ampm, occurrence.getDurationMinutes()));
        }
    }
}
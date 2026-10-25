package com.abdussamadcodes.tutorsync.ui.schedule;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ItemOccurrenceBinding;
import com.abdussamadcodes.tutorsync.model.OccurrenceGroup;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OccurrenceAdapter extends RecyclerView.Adapter<OccurrenceAdapter.ViewHolder> {

    private final List<OccurrenceGroup> groups = new ArrayList<>();
    private final List<Student> students = new ArrayList<>();
    private final List<Subject> subjects = new ArrayList<>();
    private final OnOccurrenceClickListener listener;

    public interface OnOccurrenceClickListener {
        void onOccurrenceClick(OccurrenceGroup group);
    }

    public OccurrenceAdapter(OnOccurrenceClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<OccurrenceGroup> newGroups, List<Student> newStudents, List<Subject> newSubjects) {
        groups.clear();
        groups.addAll(newGroups);
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
        holder.bind(groups.get(position));
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemOccurrenceBinding binding;

        ViewHolder(ItemOccurrenceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onOccurrenceClick(groups.get(pos));
                }
            });
        }

        void bind(OccurrenceGroup group) {
            Context context = binding.getRoot().getContext();
            String studentName = context.getString(R.string.unknown_student);
            for (Student s : students) {
                if (s.getId().equals(group.getStudentId())) {
                    // Use the new centralized helper
                    studentName = s.getFormattedDisplayName();
                    break;
                }
            }
            binding.tvStudentName.setText(studentName);

            List<String> subjectNamesList = new ArrayList<>();
            for (String subId : group.getSubjectIds()) {
                for (Subject s : subjects) {
                    if (s.getId().equals(subId)) {
                        subjectNamesList.add(s.getTitle());
                        break;
                    }
                }
            }
            String subjectName = subjectNamesList.isEmpty() ? context.getString(R.string.subject_level_general) : TextUtils.join(", ", subjectNamesList);
            binding.tvSubjectName.setText(subjectName);

            int hours = group.getStartTimeMinutes() / 60;
            int mins = group.getStartTimeMinutes() % 60;
            String ampm = hours >= 12 ? "PM" : "AM";
            hours = hours % 12;
            if (hours == 0) hours = 12;

            String timeStr = String.format(Locale.getDefault(), "%02d:%02d %s", hours, mins, ampm);
            binding.tvDateTime.setText(context.getString(R.string.occurrence_rule_today, timeStr, group.getDurationMinutes()));
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.schedule;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.databinding.ItemScheduleBinding;
import com.abdussamadcodes.tutorsync.model.ScheduleGroup;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private final List<ScheduleGroup> groups = new ArrayList<>();
    private final List<Student> students = new ArrayList<>();
    private final List<Subject> subjects = new ArrayList<>();
    private final OnScheduleClickListener listener;

    public interface OnScheduleClickListener {
        void onScheduleClick(ScheduleGroup group);
    }

    public ScheduleAdapter(OnScheduleClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<ScheduleGroup> newGroups, List<Student> newStudents, List<Subject> newSubjects) {
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
        ItemScheduleBinding binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
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
        private final ItemScheduleBinding binding;

        ViewHolder(ItemScheduleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onScheduleClick(groups.get(pos));
                }
            });
        }

        void bind(ScheduleGroup group) {
            String studentName = "Unknown Student";
            for (Student s : students) {
                if (s.getId().equals(group.getStudentId())) {
                    String displayId = s.getStudentIdDisplay() != null && !s.getStudentIdDisplay().isEmpty() ? s.getStudentIdDisplay() : "N/A";
                    studentName = s.getName() + " (ID: " + displayId + ")";
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
            String subjectName = subjectNamesList.isEmpty() ? "General" : TextUtils.join(", ", subjectNamesList);
            binding.tvSubjectName.setText(subjectName);

            int hours = group.getStartTimeMinutes() / 60;
            int mins = group.getStartTimeMinutes() % 60;
            String ampm = hours >= 12 ? "PM" : "AM";
            hours = hours % 12;
            if (hours == 0) hours = 12;
            String timeStr = String.format(Locale.getDefault(), "%02d:%02d %s", hours, mins, ampm);

            StringBuilder daysStr = new StringBuilder();
            if (group.getDaysOfWeek().size() == 7) {
                daysStr.append("Everyday");
            } else {
                for (int i = 0; i < group.getDaysOfWeek().size(); i++) {
                    daysStr.append(getDayShortName(group.getDaysOfWeek().get(i)));
                    if (i < group.getDaysOfWeek().size() - 1) daysStr.append(", ");
                }
            }

            binding.tvRule.setText("Every " + daysStr.toString() + " at " + timeStr + " (" + group.getDurationMinutes() + "m)");
        }

        private String getDayShortName(int dayOfWeek) {
            switch (dayOfWeek) {
                case 1: return "Sun";
                case 2: return "Mon";
                case 3: return "Tue";
                case 4: return "Wed";
                case 5: return "Thu";
                case 6: return "Fri";
                case 7: return "Sat";
                default: return "";
            }
        }
    }
}
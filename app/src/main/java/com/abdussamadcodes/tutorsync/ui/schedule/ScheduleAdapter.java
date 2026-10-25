package com.abdussamadcodes.tutorsync.ui.schedule;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.R;
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

            String daysStr;
            if (group.getDaysOfWeek().size() == 7) {
                daysStr = context.getString(R.string.schedule_rule_everyday);
            } else {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < group.getDaysOfWeek().size(); i++) {
                    builder.append(getDayShortName(context, group.getDaysOfWeek().get(i)));
                    if (i < group.getDaysOfWeek().size() - 1) builder.append(", ");
                }
                daysStr = builder.toString();
            }

            binding.tvRule.setText(context.getString(R.string.schedule_rule_every, daysStr, timeStr, group.getDurationMinutes()));
        }

        private String getDayShortName(Context context, int dayOfWeek) {
            switch (dayOfWeek) {
                case 1: return context.getString(R.string.day_sun);
                case 2: return context.getString(R.string.day_mon);
                case 3: return context.getString(R.string.day_tue);
                case 4: return context.getString(R.string.day_wed);
                case 5: return context.getString(R.string.day_thu);
                case 6: return context.getString(R.string.day_fri);
                case 7: return context.getString(R.string.day_sat);
                default: return "";
            }
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.schedule;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.databinding.ItemScheduleBinding;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private final List<RecurringSchedule> schedules = new ArrayList<>();
    private final List<Student> students = new ArrayList<>();
    private final List<Subject> subjects = new ArrayList<>();
    private final OnScheduleClickListener listener;

    public interface OnScheduleClickListener {
        void onScheduleClick(RecurringSchedule schedule);
    }

    public ScheduleAdapter(OnScheduleClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<RecurringSchedule> newSchedules, List<Student> newStudents, List<Subject> newSubjects) {
        schedules.clear();
        schedules.addAll(newSchedules);
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
        holder.bind(schedules.get(position));
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemScheduleBinding binding;

        ViewHolder(ItemScheduleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onScheduleClick(schedules.get(pos));
                }
            });
        }

        void bind(RecurringSchedule schedule) {
            String studentName = "Unknown Student";
            for (Student s : students) {
                if (s.getId().equals(schedule.getStudentId())) {
                    studentName = s.getName();
                    break;
                }
            }
            binding.tvStudentName.setText(studentName);

            String subjectName = "Unknown Subject";
            for (Subject s : subjects) {
                if (s.getId().equals(schedule.getSubjectId())) {
                    subjectName = s.getTitle();
                    break;
                }
            }
            binding.tvSubjectName.setText(subjectName);

            String dayName = getDayName(schedule.getDayOfWeek());

            int hours = schedule.getStartTimeMinutes() / 60;
            int mins = schedule.getStartTimeMinutes() % 60;
            String ampm = hours >= 12 ? "PM" : "AM";
            hours = hours % 12;
            if (hours == 0) hours = 12;

            String timeStr = String.format(Locale.getDefault(), "%02d:%02d %s", hours, mins, ampm);
            binding.tvRule.setText("Every " + dayName + " at " + timeStr + " (" + schedule.getDurationMinutes() + "m)");
        }

        private String getDayName(int dayOfWeek) {
            switch (dayOfWeek) {
                case 1: return "Sunday";
                case 2: return "Monday";
                case 3: return "Tuesday";
                case 4: return "Wednesday";
                case 5: return "Thursday";
                case 6: return "Friday";
                case 7: return "Saturday";
                default: return "Unknown";
            }
        }
    }
}
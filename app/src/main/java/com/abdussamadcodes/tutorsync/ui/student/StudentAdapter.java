package com.abdussamadcodes.tutorsync.ui.student;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ItemStudentBinding;
import com.abdussamadcodes.tutorsync.model.Student;
import java.util.ArrayList;
import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.StudentViewHolder> {

    private final List<Student> students = new ArrayList<>();
    private final OnStudentClickListener listener;

    public interface OnStudentClickListener {
        void onStudentClick(Student student);
    }

    public StudentAdapter(OnStudentClickListener listener) {
        this.listener = listener;
    }

    public void setStudents(List<Student> newStudents) {
        students.clear();
        students.addAll(newStudents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudentBinding binding = ItemStudentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new StudentViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        holder.bind(students.get(position));
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    class StudentViewHolder extends RecyclerView.ViewHolder {
        private final ItemStudentBinding binding;

        StudentViewHolder(ItemStudentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onStudentClick(students.get(position));
                }
            });
        }

        void bind(Student student) {
            Context context = binding.getRoot().getContext();

            // Use the new centralized helper
            binding.tvStudentName.setText(student.getFormattedDisplayName());

            String grade = student.getGrade() != null ? student.getGrade() : "";
            String school = student.getSchool() != null ? student.getSchool() : "";
            String subtitle = grade;

            if (!grade.isEmpty() && !school.isEmpty()) {
                subtitle += " • " + school;
            } else if (grade.isEmpty()) {
                subtitle = school;
            }

            binding.tvGradeSchool.setText(subtitle.isEmpty() ? context.getString(R.string.student_no_details) : subtitle);
            binding.ivActiveStatus.setVisibility(student.isActive() ? View.VISIBLE : View.GONE);
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.student;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityStudentListBinding;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.EnrollmentRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.subject.SubjectAdapter;
import java.util.List;

public class StudentListActivity extends BaseActivity {

    private ActivityStudentListBinding binding;
    private StudentAdapter adapter;
    private StudentRepository studentRepo;
    private SubjectRepository subjectRepo;
    private EnrollmentRepository enrollmentRepo;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.studentListRoot);

        studentRepo = new StudentRepository();
        subjectRepo = new SubjectRepository();
        enrollmentRepo = new EnrollmentRepository();
        sessionManager = new SessionManager(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new StudentAdapter(student -> showEnrollmentDialog(student));

        binding.rvStudents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvStudents.setAdapter(adapter);

        binding.fabAddStudent.setOnClickListener(v ->
                startActivity(new Intent(this, StudentFormActivity.class))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStudents();
    }

    private void loadStudents() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);
        adapter.setStudents(students);

        if (students.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvStudents.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvStudents.setVisibility(View.VISIBLE);
        }
    }

    private void showEnrollmentDialog(Student student) {
        String accountId = sessionManager.getActiveAccountId();
        List<Subject> availableSubjects = subjectRepo.getActiveSubjectsForAccount(accountId);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enroll_subject, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        RecyclerView rvAvailableSubjects = dialogView.findViewById(R.id.rvAvailableSubjects);
        View tvEmptySubjects = dialogView.findViewById(R.id.tvEmptySubjects);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);

        if (availableSubjects.isEmpty()) {
            tvEmptySubjects.setVisibility(View.VISIBLE);
            rvAvailableSubjects.setVisibility(View.GONE);
        } else {
            SubjectAdapter subjectAdapter = new SubjectAdapter(subject -> {
                if (enrollmentRepo.enrollStudentInSubject(student.getId(), subject.getId())) {
                    showMessage(binding.getRoot(), student.getName() + " enrolled in " + subject.getTitle());
                } else {
                    showMessage(binding.getRoot(), "Failed to enroll or already enrolled.");
                }
                dialog.dismiss();
            });
            subjectAdapter.setSubjects(availableSubjects);
            rvAvailableSubjects.setLayoutManager(new LinearLayoutManager(this));
            rvAvailableSubjects.setAdapter(subjectAdapter);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
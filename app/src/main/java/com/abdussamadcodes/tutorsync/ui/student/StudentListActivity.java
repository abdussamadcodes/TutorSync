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

        adapter = new StudentAdapter(this::showStudentOptionsDialog);
        binding.rvStudents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvStudents.setAdapter(adapter);

        binding.fabAddStudent.setOnClickListener(v -> startActivity(new Intent(this, StudentFormActivity.class)));
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

    private void showStudentOptionsDialog(Student student) {
        String[] options = {
                "Edit Student Profile",
                getString(R.string.dialog_manage_enrollments),
                getString(R.string.dialog_delete_student)
        };
        new AlertDialog.Builder(this)
                .setTitle(student.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, StudentFormActivity.class);
                        intent.putExtra("student_id", student.getId());
                        startActivity(intent);
                    } else if (which == 1) {
                        showEnrollmentDialog(student);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.dialog_delete_student_title)
                                .setMessage(R.string.dialog_delete_student_msg)
                                .setPositiveButton(R.string.dialog_delete, (d, w) -> {
                                    if (studentRepo.deleteStudent(student.getId())) {
                                        loadStudents();
                                    }
                                })
                                .setNegativeButton(R.string.action_cancel, null)
                                .show();
                    }
                }).show();
    }

    private void showEnrollmentDialog(Student student) {
        String accountId = sessionManager.getActiveAccountId();
        List<Subject> availableSubjects = subjectRepo.getActiveSubjectsForAccount(accountId);
        List<String> enrolledIds = enrollmentRepo.getEnrolledSubjectIdsForStudent(student.getId());

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enroll_subject, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        RecyclerView rvAvailableSubjects = dialogView.findViewById(R.id.rvAvailableSubjects);
        View tvEmptySubjects = dialogView.findViewById(R.id.tvEmptySubjects);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);
        View btnSave = dialogView.findViewById(R.id.btnSaveEnrollments);

        if (availableSubjects.isEmpty()) {
            tvEmptySubjects.setVisibility(View.VISIBLE);
            rvAvailableSubjects.setVisibility(View.GONE);
            btnSave.setVisibility(View.GONE);
        } else {
            SubjectAdapter subjectAdapter = new SubjectAdapter(null);
            subjectAdapter.setSubjects(availableSubjects);
            subjectAdapter.setEnrollmentMode(enrolledIds);
            rvAvailableSubjects.setLayoutManager(new LinearLayoutManager(this));
            rvAvailableSubjects.setAdapter(subjectAdapter);

            btnSave.setOnClickListener(v -> {
                java.util.Set<String> finalSelectedIds = subjectAdapter.getSelectedSubjectIds();
                enrollmentRepo.syncStudentEnrollments(student.getId(), finalSelectedIds);
                dialog.dismiss();
            });
        }
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
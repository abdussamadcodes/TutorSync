package com.abdussamadcodes.tutorsync.ui.student;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.databinding.ActivityStudentFormBinding;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.UUID;

public class StudentFormActivity extends BaseActivity {

    private ActivityStudentFormBinding binding;
    private StudentRepository repository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.studentFormRoot);

        repository = new StudentRepository();
        sessionManager = new SessionManager(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnSaveStudent.setOnClickListener(v -> saveStudent());
    }

    private void saveStudent() {
        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        String grade = binding.etGrade.getText() != null ? binding.etGrade.getText().toString().trim() : "";
        String school = binding.etSchool.getText() != null ? binding.etSchool.getText().toString().trim() : "";
        String guardianName = binding.etGuardianName.getText() != null ? binding.etGuardianName.getText().toString().trim() : "";
        String guardianPhone = binding.etGuardianPhone.getText() != null ? binding.etGuardianPhone.getText().toString().trim() : "";

        if (name.isEmpty()) {
            binding.etName.setError("Student name is required");
            return;
        }

        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) {
            showMessage(binding.getRoot(), "Session error. Please log in again.");
            return;
        }

        Student newStudent = new Student(
                UUID.randomUUID().toString(),
                accountId,
                name,
                grade.isEmpty() ? null : grade,
                school.isEmpty() ? null : school,
                guardianName.isEmpty() ? null : guardianName,
                guardianPhone.isEmpty() ? null : guardianPhone,
                null,
                true
        );

        if (repository.insertStudent(newStudent)) {
            finish();
        } else {
            showMessage(binding.getRoot(), "Failed to save student.");
        }
    }
}
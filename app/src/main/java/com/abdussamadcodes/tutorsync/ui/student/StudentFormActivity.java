package com.abdussamadcodes.tutorsync.ui.student;

import android.os.Bundle;
import android.view.View;
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
    private String existingStudentId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        repository = new StudentRepository();
        sessionManager = new SessionManager(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        existingStudentId = getIntent().getStringExtra("student_id");
        if (existingStudentId != null) {
            binding.toolbar.setTitle("Edit Student");
            binding.btnSave.setText("Update Student");
            loadExistingStudentData();
        } else {
            // Hide the ID field completely for new students (it's auto-generated)
            binding.etStudentId.setVisibility(View.GONE);
            ((View)binding.etStudentId.getParent().getParent()).setVisibility(View.GONE);
        }

        binding.btnSave.setOnClickListener(v -> saveStudent());
    }

    private void loadExistingStudentData() {
        Student student = repository.getStudentById(existingStudentId);
        if (student != null) {
            binding.etStudentId.setText(student.getStudentIdDisplay());
            binding.etStudentId.setEnabled(false);
            binding.etName.setText(student.getName());
            binding.etGrade.setText(student.getGrade());
            binding.etSchool.setText(student.getSchool());
            binding.etGuardianName.setText(student.getGuardianName());
            binding.etGuardianPhone.setText(student.getGuardianPhone());
            binding.etAddress.setText(student.getAddress());
        }
    }

    private void saveStudent() {
        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            binding.etName.setError("Name is required");
            return;
        }

        String accountId = sessionManager.getActiveAccountId();
        // displayId is ignored on insert (auto-generated) and ignored on update (permanent)
        String displayId = binding.etStudentId.getText() != null ? binding.etStudentId.getText().toString().trim() : "";
        String grade = binding.etGrade.getText() != null ? binding.etGrade.getText().toString().trim() : "";
        String school = binding.etSchool.getText() != null ? binding.etSchool.getText().toString().trim() : "";
        String guardianName = binding.etGuardianName.getText() != null ? binding.etGuardianName.getText().toString().trim() : "";
        String guardianPhone = binding.etGuardianPhone.getText() != null ? binding.etGuardianPhone.getText().toString().trim() : "";
        String address = binding.etAddress.getText() != null ? binding.etAddress.getText().toString().trim() : "";

        if (existingStudentId != null) {
            Student student = new Student(existingStudentId, accountId, displayId, name, grade, school, guardianName, guardianPhone, address, true);
            if (repository.updateStudent(student)) {
                showMessage(binding.getRoot(), "Student updated successfully");
                finish();
            } else {
                showMessage(binding.getRoot(), "Failed to update student");
            }
        } else {
            Student student = new Student(UUID.randomUUID().toString(), accountId, null, name, grade, school, guardianName, guardianPhone, address, true);
            int result = repository.insertStudent(student);
            if (result == 1) {
                showMessage(binding.getRoot(), "Student added successfully");
                finish();
            } else {
                showMessage(binding.getRoot(), "Failed to add student.");
            }
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.student;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.abdussamadcodes.tutorsync.databinding.ActivityStudentListBinding;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.List;

public class StudentListActivity extends BaseActivity {

    private ActivityStudentListBinding binding;
    private StudentAdapter adapter;
    private StudentRepository repository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.studentListRoot);

        repository = new StudentRepository();
        sessionManager = new SessionManager(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new StudentAdapter(student -> {
            // Future step: Open student details/edit screen
            showMessage(binding.getRoot(), "Selected: " + student.getName());
        });

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

        List<Student> students = repository.getActiveStudentsForAccount(accountId);
        adapter.setStudents(students);

        if (students.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvStudents.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvStudents.setVisibility(View.VISIBLE);
        }
    }
}
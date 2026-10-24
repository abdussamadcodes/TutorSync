package com.abdussamadcodes.tutorsync.ui.subject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.abdussamadcodes.tutorsync.databinding.ActivitySubjectListBinding;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.List;

public class SubjectListActivity extends BaseActivity {

    private ActivitySubjectListBinding binding;
    private SubjectAdapter adapter;
    private SubjectRepository repository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubjectListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.subjectListRoot);

        repository = new SubjectRepository();
        sessionManager = new SessionManager(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new SubjectAdapter(subject -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Subject?")
                    .setMessage("Are you sure you want to delete " + subject.getTitle() + "?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (repository.deleteSubject(subject.getId())) {
                            showMessage(binding.getRoot(), "Subject deleted");
                            loadSubjects();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSubjects.setAdapter(adapter);

        binding.fabAddSubject.setOnClickListener(v -> startActivity(new Intent(this, SubjectFormActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSubjects();
    }

    private void loadSubjects() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        List<Subject> subjects = repository.getActiveSubjectsForAccount(accountId);
        adapter.setSubjects(subjects);

        if (subjects.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvSubjects.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvSubjects.setVisibility(View.VISIBLE);
        }
    }
}
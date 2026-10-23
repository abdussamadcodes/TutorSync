package com.abdussamadcodes.tutorsync.ui.subject;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.databinding.ActivitySubjectFormBinding;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.UUID;

public class SubjectFormActivity extends BaseActivity {

    private ActivitySubjectFormBinding binding;
    private SubjectRepository repository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubjectFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.subjectFormRoot);

        repository = new SubjectRepository();
        sessionManager = new SessionManager(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnSaveSubject.setOnClickListener(v -> saveSubject());
    }

    private void saveSubject() {
        String title = binding.etTitle.getText() != null ? binding.etTitle.getText().toString().trim() : "";
        String level = binding.etLevel.getText() != null ? binding.etLevel.getText().toString().trim() : "";
        String colorTag = binding.etColorTag.getText() != null ? binding.etColorTag.getText().toString().trim() : "";

        if (title.isEmpty()) {
            binding.etTitle.setError("Subject title is required");
            return;
        }

        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) {
            showMessage(binding.getRoot(), "Session error. Please log in again.");
            return;
        }

        Subject newSubject = new Subject(
                UUID.randomUUID().toString(),
                accountId,
                title,
                level.isEmpty() ? null : level,
                colorTag.isEmpty() ? null : colorTag,
                true
        );

        if (repository.insertSubject(newSubject)) {
            finish();
        } else {
            showMessage(binding.getRoot(), "Failed to save subject.");
        }
    }
}
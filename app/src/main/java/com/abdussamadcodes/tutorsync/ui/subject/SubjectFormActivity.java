package com.abdussamadcodes.tutorsync.ui.subject;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.databinding.ActivitySubjectFormBinding;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Locale;
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
        binding.etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.etTitle.setError(null);
                TextInputLayout til = findParentTextInputLayout();
                if (til != null) {
                    til.setError(null);
                    til.setErrorEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnSaveSubject.setOnClickListener(v -> saveSubject());
    }

    private void saveSubject() {
        String title = binding.etTitle.getText() != null ? binding.etTitle.getText().toString().trim() : "";
        String level = binding.etLevel.getText() != null ? binding.etLevel.getText().toString().trim() : "";

        if (title.isEmpty()) {
            String errMsg = tr("Subject title is required", "বিষয়ের নাম আবশ্যক");
            binding.etTitle.setError(errMsg);
            binding.etTitle.requestFocus();
            return;
        }

        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) {
            showMessage(binding.getRoot(), tr(
                    "Session error. Please log in again.",
                    "সেশন ত্রুটি। অনুগ্রহ করে আবার লগ ইন করুন।"));
            return;
        }

        Subject newSubject = new Subject(
                UUID.randomUUID().toString(),
                accountId,
                title,
                level.isEmpty() ? null : level,
                true
        );

        if (repository.insertSubject(newSubject)) {
            finish();
        } else {
            showMessage(binding.getRoot(), tr(
                    "Failed to save subject.",
                    "বিষয় সেভ করতে ব্যর্থ হয়েছে।"));
        }
    }

    @Nullable
    private TextInputLayout findParentTextInputLayout() {
        ViewParent parent = binding.etTitle.getParent();
        while (parent instanceof ViewGroup) {
            if (parent instanceof TextInputLayout) {
                return (TextInputLayout) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private boolean isBangla() {
        if (appPreferences != null && AppPreferences.LANG_BANGLA.equals(appPreferences.getLanguageCode())) {
            return true;
        }
        return Locale.getDefault().getLanguage().startsWith("bn");
    }

    @NonNull
    private String tr(@NonNull String en, @NonNull String bn) {
        return isBangla() ? bn : en;
    }
}
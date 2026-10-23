package com.abdussamadcodes.tutorsync.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivityMainBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.account.PinLockActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.onboarding.WelcomeActivity;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;

public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private SessionManager sessionManager;
    private LocalAccountStore accountStore;
    private StudentRepository studentRepo;
    private SubjectRepository subjectRepo;
    private boolean isPinVerifiedThisSession = false;
    private static final String STATE_PIN_VERIFIED = "state_pin_verified";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            isPinVerifiedThisSession = savedInstanceState.getBoolean(STATE_PIN_VERIFIED, false);
        }

        sessionManager = new SessionManager(this);
        accountStore = new LocalAccountStore(this);
        studentRepo = new StudentRepository();
        subjectRepo = new SubjectRepository();

        if (!sessionManager.hasActiveSession()) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.mainRoot);

        setupUI();
        setupListeners();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PIN_VERIFIED, isPinVerifiedThisSession);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager.hasActiveSession()) {
            refreshStatusLabels();
            loadDashboardData();

            if (!isPinVerifiedThisSession && sessionManager.hasPinSetup()) {
                startActivity(new Intent(this, PinLockActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                isPinVerifiedThisSession = true;
            }
        }
    }

    private void setupUI() {
        // Load User Name
        String accId = sessionManager.getActiveAccountId();
        // Since our LocalAccountStore fetches by username, this is slightly tricky.
        // For now, we will set a generic greeting, we can fetch the actual name in Phase 12 if needed.
        binding.tvGreeting.setText("Hello, Tutor!");

        // Setup Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            binding.tabHome.setVisibility(View.GONE);
            binding.tabDirectory.setVisibility(View.GONE);
            binding.tabSettings.setVisibility(View.GONE);

            int id = item.getItemId();
            if (id == R.id.nav_home) {
                binding.tabHome.setVisibility(View.VISIBLE);
                return true;
            } else if (id == R.id.nav_directory) {
                binding.tabDirectory.setVisibility(View.VISIBLE);
                return true;
            } else if (id == R.id.nav_settings) {
                binding.tabSettings.setVisibility(View.VISIBLE);
                return true;
            }
            return false;
        });
    }

    private void loadDashboardData() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        // Fetch actual counts from SQLite!
        int studentCount = studentRepo.getActiveStudentsForAccount(accountId).size();
        int subjectCount = subjectRepo.getActiveSubjectsForAccount(accountId).size();

        binding.tvStudentCount.setText(String.valueOf(studentCount));
        binding.tvSubjectCount.setText(String.valueOf(subjectCount));
    }

    private void refreshStatusLabels() {
        binding.tvActiveLanguageValue.setText(R.string.main_current_language_value);
        String themeMode = appPreferences.getThemeMode();
        if (AppPreferences.THEME_LIGHT.equals(themeMode)) binding.tvActiveThemeValue.setText(R.string.theme_light);
        else if (AppPreferences.THEME_DARK.equals(themeMode)) binding.tvActiveThemeValue.setText(R.string.theme_dark);
        else binding.tvActiveThemeValue.setText(R.string.theme_system);
    }

    private void setupListeners() {
        // Dashboard Quick Actions
        binding.btnQuickAddStudent.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.student.StudentFormActivity.class))
        );

        binding.btnQuickAddSubject.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.subject.SubjectFormActivity.class))
        );

        // Directory Tab
        binding.cardManageStudents.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.student.StudentListActivity.class))
        );

        binding.cardManageSubjects.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.subject.SubjectListActivity.class))
        );

        // Settings Tab
        binding.btnOpenLanguageSettings.setOnClickListener(v -> startActivity(WelcomeActivity.createReopenIntent(this)));

        binding.btnCycleTheme.setOnClickListener(v -> {
            String current = appPreferences.getThemeMode();
            String next = AppPreferences.THEME_SYSTEM.equals(current) ? AppPreferences.THEME_LIGHT :
                    AppPreferences.THEME_LIGHT.equals(current) ? AppPreferences.THEME_DARK : AppPreferences.THEME_SYSTEM;
            appPreferences.setThemeMode(next);
            refreshStatusLabels();
            LocaleThemeHelper.applyThemeMode(next);
            showMessage(binding.getRoot(), R.string.msg_theme_updated);
        });

        binding.btnSignOut.setOnClickListener(v -> {
            sessionManager.logout();
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
    }
}
package com.abdussamadcodes.tutorsync.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivityMainBinding;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.OccurrenceGroup;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.account.PinLockActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.attendance.AttendanceBottomSheetFragment;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.onboarding.WelcomeActivity;
import com.abdussamadcodes.tutorsync.ui.schedule.OccurrenceAdapter;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private SessionManager sessionManager;
    private LocalAccountStore accountStore;
    private StudentRepository studentRepo;
    private SubjectRepository subjectRepo;
    private OccurrenceRepository occurrenceRepo;
    private OccurrenceAdapter occurrenceAdapter;
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
        occurrenceRepo = new OccurrenceRepository();

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
        occurrenceAdapter = new OccurrenceAdapter(group -> {
            AttendanceBottomSheetFragment bottomSheet = AttendanceBottomSheetFragment.newInstance(
                    sessionManager.getActiveAccountId(),
                    group.getStudentId(),
                    new ArrayList<>(group.getOccurrenceIds())
            );
            bottomSheet.show(getSupportFragmentManager(), AttendanceBottomSheetFragment.TAG);
        });

        binding.rvOccurrences.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOccurrences.setAdapter(occurrenceAdapter);

        getSupportFragmentManager().setFragmentResultListener("attendance_request_key", this, (requestKey, result) -> {
            if (result.getBoolean("attendance_recorded", false)) {
                loadDashboardData();
            }
        });

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

        List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);
        List<com.abdussamadcodes.tutorsync.model.Subject> subjects = subjectRepo.getActiveSubjectsForAccount(accountId);

        binding.tvStudentCount.setText(String.valueOf(students.size()));
        binding.tvSubjectCount.setText(String.valueOf(subjects.size()));

        List<ClassOccurrence> occurrences = occurrenceRepo.getOccurrencesForToday(accountId);

        // Group occurrences by student, start time, and duration
        List<OccurrenceGroup> groups = new ArrayList<>();
        for (ClassOccurrence occ : occurrences) {
            boolean found = false;
            for (OccurrenceGroup g : groups) {
                if (g.getStudentId().equals(occ.getStudentId()) &&
                        g.getStartTimeMinutes() == occ.getStartTimeMinutes() &&
                        g.getDurationMinutes() == occ.getDurationMinutes()) {

                    if (occ.getSubjectId() != null && !g.getSubjectIds().contains(occ.getSubjectId())) {
                        g.getSubjectIds().add(occ.getSubjectId());
                    }
                    if (!g.getOccurrenceIds().contains(occ.getId())) {
                        g.getOccurrenceIds().add(occ.getId());
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                OccurrenceGroup newGroup = new OccurrenceGroup(occ.getStudentId(), occ.getDate(), occ.getStartTimeMinutes(), occ.getDurationMinutes());
                if (occ.getSubjectId() != null) newGroup.getSubjectIds().add(occ.getSubjectId());
                newGroup.getOccurrenceIds().add(occ.getId());
                groups.add(newGroup);
            }
        }

        occurrenceAdapter.setData(groups, students, subjects);

        if (groups.isEmpty()) {
            binding.tvEmptyUpcoming.setVisibility(View.VISIBLE);
            binding.rvOccurrences.setVisibility(View.GONE);
        } else {
            binding.tvEmptyUpcoming.setVisibility(View.GONE);
            binding.rvOccurrences.setVisibility(View.VISIBLE);
        }
    }

    private void refreshStatusLabels() {
        String currentLang = appPreferences.getLanguageCode();
        if (AppPreferences.LANG_BANGLA.equals(currentLang)) {
            binding.tvActiveLanguageValue.setText("‡¶¨‡¶æ‡¶Ç‡¶≤‡¶æ");
        } else {
            binding.tvActiveLanguageValue.setText("English");
        }

        String themeMode = appPreferences.getThemeMode();
        if (AppPreferences.THEME_LIGHT.equals(themeMode)) {
            binding.tvActiveThemeValue.setText(R.string.theme_light);
        } else if (AppPreferences.THEME_DARK.equals(themeMode)) {
            binding.tvActiveThemeValue.setText(R.string.theme_dark);
        } else {
            binding.tvActiveThemeValue.setText(R.string.theme_system);
        }
    }

    private void setupListeners() {
        binding.btnQuickAddStudent.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.student.StudentFormActivity.class))
        );

        binding.btnQuickAddSubject.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.subject.SubjectFormActivity.class))
        );

        binding.btnQuickAddSchedule.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.schedule.ScheduleFormActivity.class))
        );

        binding.cardManageStudents.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.student.StudentListActivity.class))
        );

        binding.cardManageSubjects.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.subject.SubjectListActivity.class))
        );

        binding.cardManageSchedules.setOnClickListener(v ->
                startActivity(new Intent(this, com.abdussamadcodes.tutorsync.ui.schedule.ScheduleListActivity.class))
        );

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
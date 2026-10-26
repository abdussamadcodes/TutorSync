package com.abdussamadcodes.tutorsync.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivityMainBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.OccurrenceGroup;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.AppLockManager;
import com.abdussamadcodes.tutorsync.security.BiometricAuthHelper;
import com.abdussamadcodes.tutorsync.security.PasswordHasher;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.account.TutorProfileActivity;
import com.abdussamadcodes.tutorsync.ui.attendance.AttendanceBottomSheetFragment;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.finance.InvoiceListActivity;
import com.abdussamadcodes.tutorsync.ui.onboarding.WelcomeActivity;
import com.abdussamadcodes.tutorsync.ui.schedule.OccurrenceAdapter;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.BackupRestoreManager;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private SessionManager sessionManager;
    private LocalAccountStore accountStore;
    private StudentRepository studentRepo;
    private SubjectRepository subjectRepo;
    private OccurrenceRepository occurrenceRepo;
    private InvoiceRepository invoiceRepo;
    private OccurrenceAdapter occurrenceAdapter;

    private int defaultSettingsPicPaddingLeft;
    private int defaultSettingsPicPaddingTop;
    private int defaultSettingsPicPaddingRight;
    private int defaultSettingsPicPaddingBottom;
    private ImageView.ScaleType defaultSettingsPicScaleType;
    private ColorStateList defaultSettingsPicTintList;

    private final ActivityResultLauncher<String> exportBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::onExportBackupUriSelected);

    private final ActivityResultLauncher<String[]> importBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportBackupUriSelected);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        accountStore = new LocalAccountStore(this);
        studentRepo = new StudentRepository();
        subjectRepo = new SubjectRepository();
        occurrenceRepo = new OccurrenceRepository();
        invoiceRepo = new InvoiceRepository();

        if (!sessionManager.hasActiveSession()) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.mainRoot);

        defaultSettingsPicPaddingLeft = binding.ivSettingsProfilePic.getPaddingLeft();
        defaultSettingsPicPaddingTop = binding.ivSettingsProfilePic.getPaddingTop();
        defaultSettingsPicPaddingRight = binding.ivSettingsProfilePic.getPaddingRight();
        defaultSettingsPicPaddingBottom = binding.ivSettingsProfilePic.getPaddingBottom();
        defaultSettingsPicScaleType = binding.ivSettingsProfilePic.getScaleType();
        defaultSettingsPicTintList = ImageViewCompat.getImageTintList(binding.ivSettingsProfilePic);

        setupUI();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager.hasActiveSession()) {
            refreshStatusLabels();
            loadDashboardData();
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

        Account account = accountStore.getAccountById(accountId);
        String accountName = account != null ? account.getFullName() : "Tutor";

        String currentLang = appPreferences.getLanguageCode();
        if (AppPreferences.LANG_BANGLA.equals(currentLang)) {
            binding.tvGreeting.setText("হ্যালো, " + accountName + "!");
        } else {
            binding.tvGreeting.setText("Hello, " + accountName + "!");
        }

        // Populate Settings Profile Card
        binding.tvSettingsProfileName.setText(accountName);
        if (account != null) {
            String subtitle = account.getEmail() != null && !account.getEmail().isEmpty()
                    ? account.getEmail()
                    : (account.getPhone() != null && !account.getPhone().isEmpty()
                    ? account.getPhone()
                    : "@" + account.getUsername());
            binding.tvSettingsProfileSubtitle.setText(subtitle);

            String picPath = account.getProfilePicPath();
            boolean loadedPic = false;
            if (picPath != null && !picPath.isEmpty()) {
                File f = new File(picPath);
                if (f.exists() && f.length() > 0) {
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (bmp != null) {
                        binding.ivSettingsProfilePic.setImageDrawable(null);
                        ImageViewCompat.setImageTintList(binding.ivSettingsProfilePic, null);
                        binding.ivSettingsProfilePic.clearColorFilter();
                        binding.ivSettingsProfilePic.setPadding(0, 0, 0, 0);
                        binding.ivSettingsProfilePic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        binding.ivSettingsProfilePic.setImageBitmap(bmp);
                        loadedPic = true;
                    }
                }
            }
            if (!loadedPic) {
                binding.ivSettingsProfilePic.setImageBitmap(null);
                binding.ivSettingsProfilePic.setPadding(
                        defaultSettingsPicPaddingLeft,
                        defaultSettingsPicPaddingTop,
                        defaultSettingsPicPaddingRight,
                        defaultSettingsPicPaddingBottom
                );
                binding.ivSettingsProfilePic.setScaleType(
                        defaultSettingsPicScaleType != null ? defaultSettingsPicScaleType : ImageView.ScaleType.CENTER_INSIDE
                );
                binding.ivSettingsProfilePic.setImageResource(R.drawable.ic_tutor_logo);
                ImageViewCompat.setImageTintList(binding.ivSettingsProfilePic, defaultSettingsPicTintList);
            }
        }

        List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);
        List<com.abdussamadcodes.tutorsync.model.Subject> subjects = subjectRepo.getActiveSubjectsForAccount(accountId);

        binding.tvStudentCount.setText(String.valueOf(students.size()));
        binding.tvSubjectCount.setText(String.valueOf(subjects.size()));

        int totalRevenueMinor = invoiceRepo.getTotalRevenueForAccount(accountId);
        binding.tvRevenue.setText(String.format(Locale.US, "৳ %.2f", totalRevenueMinor / 100.0));

        List<ClassOccurrence> occurrences = occurrenceRepo.getOccurrencesForToday(accountId);

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
            binding.tvActiveLanguageValue.setText("বাংলা");
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

        if (sessionManager.hasPinSetup()) {
            if (sessionManager.isBiometricEnabled() && BiometricAuthHelper.isBiometricReady(this)) {
                binding.tvAppLockStatusValue.setText(R.string.app_lock_status_pin_bio);
            } else {
                binding.tvAppLockStatusValue.setText(R.string.app_lock_status_pin);
            }
        } else {
            binding.tvAppLockStatusValue.setText(R.string.app_lock_status_off);
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

        binding.cardRevenue.setOnClickListener(v ->
                startActivity(new Intent(this, InvoiceListActivity.class))
        );

        // Tutor Profile
        View.OnClickListener openProfileListener = v ->
                startActivity(new Intent(this, TutorProfileActivity.class));
        binding.cardTutorProfile.setOnClickListener(openProfileListener);
        binding.btnEditProfile.setOnClickListener(openProfileListener);

        // App Lock Configuration
        binding.btnConfigureAppLock.setOnClickListener(v -> showAppLockDialog());

        // Local Backup & Restore
        binding.btnExportBackup.setOnClickListener(v -> startBackupExport());
        binding.btnImportBackup.setOnClickListener(v -> {
            AppLockManager.getInstance().markExternalFlowStarted();
            importBackupLauncher.launch(new String[]{"application/json", "application/octet-stream", "*/*"});
        });

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
            AppLockManager.getInstance().resetLockState();
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
    }

    private void showAppLockDialog() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);

        final EditText etCurrentPin = new EditText(this);
        if (sessionManager.hasPinSetup()) {
            etCurrentPin.setHint(R.string.app_lock_current_pin_hint);
            etCurrentPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            etCurrentPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
            container.addView(etCurrentPin);
        }

        final EditText etNewPin = new EditText(this);
        etNewPin.setHint(R.string.app_lock_new_pin_hint);
        etNewPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etNewPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        container.addView(etNewPin);

        final CheckBox cbBiometric = new CheckBox(this);
        cbBiometric.setText(R.string.app_lock_enable_biometric);
        boolean bioReady = BiometricAuthHelper.isBiometricReady(this);
        cbBiometric.setEnabled(bioReady);
        cbBiometric.setChecked(bioReady && sessionManager.isBiometricEnabled());
        container.addView(cbBiometric);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_app_lock)
                .setView(container)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    if (sessionManager.hasPinSetup()) {
                        String curr = etCurrentPin.getText().toString().trim();
                        if (!sessionManager.verifyPin(curr)) {
                            showMessage(binding.getRoot(), R.string.app_lock_incorrect_pin);
                            return;
                        }
                    }

                    String newPin = etNewPin.getText().toString().trim();
                    boolean useBio = cbBiometric.isChecked();

                    try {
                        if (!newPin.isEmpty()) {
                            if (newPin.length() < 4) {
                                showMessage(binding.getRoot(), R.string.app_lock_pin_length_err);
                                return;
                            }
                            String hashed = PasswordHasher.hashPassword(newPin, SessionManager.PIN_SALT_BASE64);
                            sessionManager.savePinSetup(hashed, useBio);
                        } else if (sessionManager.hasPinSetup()) {
                            sessionManager.setBiometricEnabled(useBio);
                        } else {
                            showMessage(binding.getRoot(), R.string.app_lock_pin_length_err);
                            return;
                        }
                        AppLockManager.getInstance().markUnlocked();
                        refreshStatusLabels();
                        showMessage(binding.getRoot(), R.string.app_lock_saved_msg);
                    } catch (Exception e) {
                        showMessage(binding.getRoot(), R.string.auth_err_hashing);
                    }
                });

        if (sessionManager.hasPinSetup()) {
            builder.setNeutralButton(R.string.app_lock_disable_action, (dialog, which) -> {
                String curr = etCurrentPin.getText().toString().trim();
                if (!sessionManager.verifyPin(curr)) {
                    showMessage(binding.getRoot(), R.string.app_lock_incorrect_pin);
                    return;
                }
                sessionManager.savePinSetup(null, false);
                refreshStatusLabels();
                showMessage(binding.getRoot(), R.string.app_lock_disabled_msg);
            });
        }

        builder.show();
    }

    private void startBackupExport() {
        String accountId = sessionManager.getActiveAccountId();
        Account account = accountStore.getAccountById(accountId);
        String username = account != null ? account.getUsername() : "account";
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
        String filename = "tutorsync_backup_" + username + "_" + timestamp + ".json";
        AppLockManager.getInstance().markExternalFlowStarted();
        exportBackupLauncher.launch(filename);
    }

    private void onExportBackupUriSelected(@Nullable Uri uri) {
        if (uri == null) return;
        boolean ok = BackupRestoreManager.exportAccountData(this, sessionManager.getActiveAccountId(), uri);
        showMessage(binding.getRoot(), ok ? R.string.backup_export_success : R.string.backup_export_failed);
    }

    private void onImportBackupUriSelected(@Nullable Uri uri) {
        if (uri == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(R.string.backup_import_confirm_msg)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_continue, (dialog, which) -> {
                    boolean ok = BackupRestoreManager.importAccountData(this, sessionManager.getActiveAccountId(), uri);
                    if (ok) {
                        loadDashboardData();
                        showMessage(binding.getRoot(), R.string.backup_import_success);
                    } else {
                        showMessage(binding.getRoot(), R.string.backup_import_failed);
                    }
                })
                .show();
    }
}
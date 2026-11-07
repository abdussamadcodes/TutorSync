package com.abdussamadcodes.tutorsync.ui.main;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.GoogleAuthHelper;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.databinding.ActivityMainBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.OccurrenceGroup;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.AppLockManager;
import com.abdussamadcodes.tutorsync.security.BiometricAuthHelper;
import com.abdussamadcodes.tutorsync.security.PasswordHasher;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.attendance.AttendanceBottomSheetFragment;
import com.abdussamadcodes.tutorsync.ui.attendance.AttendanceHistoryActivity;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.ui.finance.InvoiceListActivity;
import com.abdussamadcodes.tutorsync.ui.onboarding.WelcomeActivity;
import com.abdussamadcodes.tutorsync.ui.schedule.OccurrenceAdapter;
import com.abdussamadcodes.tutorsync.ui.schedule.ScheduleFormActivity;
import com.abdussamadcodes.tutorsync.ui.schedule.ScheduleListActivity;
import com.abdussamadcodes.tutorsync.ui.student.StudentFormActivity;
import com.abdussamadcodes.tutorsync.ui.student.StudentListActivity;
import com.abdussamadcodes.tutorsync.ui.subject.SubjectFormActivity;
import com.abdussamadcodes.tutorsync.ui.subject.SubjectListActivity;
import com.abdussamadcodes.tutorsync.util.AppExecutors;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.BackupCrypto;
import com.abdussamadcodes.tutorsync.util.BackupRestoreManager;
import com.abdussamadcodes.tutorsync.util.ImageUtils;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private SessionManager sessionManager;
    private LocalAccountStore accountStore;
    private StudentRepository studentRepo;
    private SubjectRepository subjectRepo;
    private OccurrenceRepository occurrenceRepo;
    private InvoiceRepository invoiceRepo;
    private OccurrenceAdapter occurrenceAdapter;
    private final AtomicBoolean isBackupTaskRunning = new AtomicBoolean(false);
    private static final String STATE_ACTIVE_TAB = "state_active_tab";

    // Remembers the active tab across theme/language recreations
    private static int lastActiveTabId = R.id.nav_home;
    @Nullable
    private static volatile String lastReconciledAccountId = null;

    @Nullable
    private ImageView activeDialogProfileImageView;
    @Nullable
    private String pendingProfilePicPath;

    private final ActivityResultLauncher<String> pickProfilePhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String accountId = sessionManager.getActiveAccountId();
                    if (accountId.isEmpty()) return;

                    // Compress new photo and delete any previous unsaved pending photo from this dialog session
                    String savedPath = ImageUtils.compressAndSaveImage(
                            this,
                            uri,
                            "profile_" + accountId,
                            pendingProfilePicPath
                    );
                    if (savedPath != null) {
                        pendingProfilePicPath = savedPath;
                        if (activeDialogProfileImageView != null) {
                            ImageUtils.loadProfileImageIntoView(savedPath, activeDialogProfileImageView);
                        }
                    } else {
                        showMessage(binding.getRoot(), R.string.profile_photo_error);
                    }
                }
            });

    private final ActivityResultLauncher<String> exportBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) {
                    exportBackupToUri(uri);
                }
            });

    // M8: password-protected backups are written to a file the tutor picks; the password waits here.
    @Nullable private char[] pendingBackupPassword = null;
    private final ActivityResultLauncher<String> encryptedExportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                char[] pw = pendingBackupPassword;
                pendingBackupPassword = null;
                if (pw == null) return;
                if (uri != null) {
                    exportEncryptedBackupToUri(uri, pw);
                } else {
                    Arrays.fill(pw, '\0');
                }
            });

    private final ActivityResultLauncher<String[]> importBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importBackupFromUri(uri);
                }
            });

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
            lastActiveTabId = R.id.nav_home;
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.mainRoot);

        // 1. Attach listeners and adapters FIRST
        setupUI();
        setupListeners();

        // 2. Restore the active tab AFTER setupUI() so the tab visibility updates properly
        int restoredTabId = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_ACTIVE_TAB, lastActiveTabId)
                : lastActiveTabId;
        binding.bottomNavigation.setSelectedItemId(restoredTabId);
        switchToTab(restoredTabId);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int restoredTabId = savedInstanceState.getInt(STATE_ACTIVE_TAB, lastActiveTabId);
        binding.bottomNavigation.setSelectedItemId(restoredTabId);
        switchToTab(restoredTabId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ACTIVE_TAB, lastActiveTabId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager.hasActiveSession()) {
            switchToTab(lastActiveTabId);
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

        binding.bottomNavigation.setOnItemSelectedListener(item -> switchToTab(item.getItemId()));
    }

    private boolean switchToTab(int itemId) {
        if (itemId != R.id.nav_home && itemId != R.id.nav_directory && itemId != R.id.nav_settings) {
            return false;
        }
        lastActiveTabId = itemId;
        binding.tabHome.setVisibility(itemId == R.id.nav_home ? View.VISIBLE : View.GONE);
        binding.tabDirectory.setVisibility(itemId == R.id.nav_directory ? View.VISIBLE : View.GONE);
        binding.tabSettings.setVisibility(itemId == R.id.nav_settings ? View.VISIBLE : View.GONE);
        return true;
    }

    private void setChangePasswordRowVisible(boolean visible) {
        binding.rowChangePassword.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (binding.rowChangePassword.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) binding.rowChangePassword.getParent();
            int index = parent.indexOfChild(binding.rowChangePassword);
            if (index > 0) {
                View dividerAbove = parent.getChildAt(index - 1);
                if (dividerAbove != null && dividerAbove.getId() == View.NO_ID) {
                    dividerAbove.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
        }
    }

    private void loadDashboardData() {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        final String currentLang = appPreferences.getLanguageCode();

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                if (!accountId.equals(lastReconciledAccountId)) {
                    studentRepo.migrateLegacyStudentIds(accountId);
                    invoiceRepo.migrateLegacyInvoiceIds(accountId);
                    invoiceRepo.reconcileAccountFinancialData(accountId);
                    lastReconciledAccountId = accountId;
                }

                // H1: keep recurring classes rolling forward (idempotent; never recreates removed classes)
                new ScheduleRepository().topUpRecurringOccurrences(accountId);

                final Account account = accountStore.getAccountById(accountId);
                final boolean isGoogleAccount = GoogleAuthHelper.isGoogleSignInAccount(account);
                final List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);
                final List<Subject> subjects = subjectRepo.getActiveSubjectsForAccount(accountId);
                final int totalRevenueMinor = invoiceRepo.getTotalRevenueForAccount(accountId);
                final List<ClassOccurrence> occurrences = occurrenceRepo.getOccurrencesForToday(accountId);
                final int needsAttendanceCount = new AttendanceRepository().countClassesNeedingAttendance(accountId);

                final List<OccurrenceGroup> groups = new ArrayList<>();
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
                        OccurrenceGroup newGroup = new OccurrenceGroup(
                                occ.getStudentId(), occ.getDate(), occ.getStartTimeMinutes(), occ.getDurationMinutes());
                        if (occ.getSubjectId() != null) newGroup.getSubjectIds().add(occ.getSubjectId());
                        newGroup.getOccurrenceIds().add(occ.getId());
                        groups.add(newGroup);
                    }
                }

                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (isFinishing() || isDestroyed() || binding == null) return;
                    if (!accountId.equals(sessionManager.getActiveAccountId())) return;

                    // Hide Change Password row & divider if logged in with Google Sign-In
                    setChangePasswordRowVisible(!isGoogleAccount);

                    // H2: attendance-history card shows how many recent classes still need attendance
                    if (needsAttendanceCount > 0) {
                        binding.tvAttendanceHistoryBadge.setVisibility(View.VISIBLE);
                        binding.tvAttendanceHistoryBadge.setText(needsAttendanceCount > 99 ? "99+" : String.valueOf(needsAttendanceCount));
                        binding.tvAttendanceHistorySub.setText(getString(R.string.dashboard_attendance_history_sub_pending, needsAttendanceCount));
                    } else {
                        binding.tvAttendanceHistoryBadge.setVisibility(View.GONE);
                        binding.tvAttendanceHistorySub.setText(R.string.dashboard_attendance_history_sub);
                    }

                    String accountName = (account != null && !account.getFullName().trim().isEmpty())
                            ? account.getFullName() : "Tutor";
                    String username = (account != null) ? account.getUsername() : "";
                    String phone = (account != null && account.getPhone() != null) ? account.getPhone() : "";
                    String email = (account != null && account.getEmail() != null) ? account.getEmail() : "";
                    String profilePicPath = (account != null && account.getProfilePicPath() != null)
                            ? account.getProfilePicPath() : "";

                    if (AppPreferences.LANG_BANGLA.equals(currentLang)) {
                        binding.tvGreeting.setText("হ্যালো, " + accountName + "!");
                    } else {
                        binding.tvGreeting.setText("Hello, " + accountName + "!");
                    }

                    // Populate Settings Profile Card
                    binding.tvSettingsProfileName.setText(accountName);
                    if (!username.isEmpty()) {
                        binding.tvSettingsUsername.setText("@" + username);
                        binding.tvSettingsUsername.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvSettingsUsername.setVisibility(View.GONE);
                    }

                    if (!phone.isEmpty() && !email.isEmpty()) {
                        binding.tvSettingsProfileSubtitle.setText(phone + " • " + email);
                    } else if (!phone.isEmpty()) {
                        binding.tvSettingsProfileSubtitle.setText(phone);
                    } else if (!email.isEmpty()) {
                        binding.tvSettingsProfileSubtitle.setText(email);
                    } else {
                        binding.tvSettingsProfileSubtitle.setText(R.string.profile_tap_to_edit);
                    }

                    loadProfileImageIntoView(profilePicPath, binding.ivSettingsProfilePic);

                    binding.tvStudentCount.setText(String.valueOf(students.size()));
                    binding.tvSubjectCount.setText(String.valueOf(subjects.size()));
                    binding.tvRevenue.setText(String.format(Locale.US, "৳ %.2f", totalRevenueMinor / 100.0));

                    occurrenceAdapter.setData(groups, students, subjects);

                    if (groups.isEmpty()) {
                        binding.tvEmptyUpcoming.setVisibility(View.VISIBLE);
                        binding.rvOccurrences.setVisibility(View.GONE);
                    } else {
                        binding.tvEmptyUpcoming.setVisibility(View.GONE);
                        binding.rvOccurrences.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {
            }
        });
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
            if (sessionManager.isBiometricEnabled()) {
                binding.tvAppLockStatusValue.setText("PIN + Biometric");
            } else {
                binding.tvAppLockStatusValue.setText("PIN Enabled");
            }
        } else {
            binding.tvAppLockStatusValue.setText(R.string.app_lock_status_off);
        }
    }

    private void setupListeners() {
        binding.btnQuickAddStudent.setOnClickListener(v ->
                startActivity(new Intent(this, StudentFormActivity.class))
        );

        binding.btnQuickAddSubject.setOnClickListener(v ->
                startActivity(new Intent(this, SubjectFormActivity.class))
        );

        binding.btnQuickAddSchedule.setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleFormActivity.class))
        );

        binding.cardManageStudents.setOnClickListener(v ->
                startActivity(new Intent(this, StudentListActivity.class))
        );

        binding.cardManageSubjects.setOnClickListener(v ->
                startActivity(new Intent(this, SubjectListActivity.class))
        );

        binding.cardManageSchedules.setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleListActivity.class))
        );

        binding.cardAttendanceHistory.setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceHistoryActivity.class))
        );

        binding.cardRevenue.setOnClickListener(v ->
                startActivity(new Intent(this, InvoiceListActivity.class))
        );

        // 1. Tutor Profile Card & Edit Button
        binding.cardTutorProfile.setOnClickListener(v -> showEditProfileBottomSheet());
        binding.btnEditProfile.setOnClickListener(v -> showEditProfileBottomSheet());

        // 2. Security Section: Existing PIN/Biometric + Change Password
        binding.btnConfigureAppLock.setOnClickListener(v -> showConfigureAppLockDialog());
        binding.rowAppLock.setOnClickListener(v -> showConfigureAppLockDialog());

        binding.btnChangePassword.setOnClickListener(v -> showChangePasswordBottomSheet());
        binding.rowChangePassword.setOnClickListener(v -> showChangePasswordBottomSheet());

        // 3. Preferences Section
        binding.btnOpenLanguageSettings.setOnClickListener(v -> {
            lastActiveTabId = R.id.nav_settings;
            startActivity(WelcomeActivity.createReopenIntent(this));
        });
        binding.rowLanguageSettings.setOnClickListener(v ->
                binding.btnOpenLanguageSettings.performClick()
        );

        binding.btnCycleTheme.setOnClickListener(v -> {
            lastActiveTabId = R.id.nav_settings;
            String current = appPreferences.getThemeMode();
            String next = AppPreferences.THEME_SYSTEM.equals(current) ? AppPreferences.THEME_LIGHT :
                    AppPreferences.THEME_LIGHT.equals(current) ? AppPreferences.THEME_DARK : AppPreferences.THEME_SYSTEM;
            appPreferences.setThemeMode(next);
            refreshStatusLabels();
            LocaleThemeHelper.applyThemeMode(next);
            showMessage(binding.getRoot(), R.string.msg_theme_updated);
        });
        binding.rowThemeSettings.setOnClickListener(v ->
                binding.btnCycleTheme.performClick()
        );

        // 4. Local Backup & Restore
        binding.btnExportBackup.setOnClickListener(v ->
                BackupDialogs.askNewPassword(this,
                        pw -> {
                            pendingBackupPassword = pw;
                            encryptedExportLauncher.launch("tutorsync_backup_" + System.currentTimeMillis() + ".tsbackup");
                        },
                        () -> exportBackupLauncher.launch("tutorsync_backup_" + System.currentTimeMillis() + ".json"))
        );
        binding.btnImportBackup.setOnClickListener(v -> startRestoreFlow()
        );

        // 5. Sign Out
        binding.btnSignOut.setOnClickListener(v -> {
            lastActiveTabId = R.id.nav_home;
            lastReconciledAccountId = null;
            GoogleAuthHelper.signOut(this);
            sessionManager.logout();
            AppLockManager.getInstance().resetLockState();
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
    }

    // =========================================================================
    // 1. TUTOR PROFILE SETTINGS (WITH READ-ONLY USERNAME)
    // =========================================================================
    private void showEditProfileBottomSheet() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_tutor_profile, null);
        bottomSheet.setContentView(sheetView);

        ImageView ivDialogProfilePic = sheetView.findViewById(R.id.ivDialogProfilePic);
        TextInputLayout tilFullName = sheetView.findViewById(R.id.tilProfileFullName);
        TextInputEditText etUsername = sheetView.findViewById(R.id.etProfileUsername);
        TextInputEditText etFullName = sheetView.findViewById(R.id.etProfileFullName);
        TextInputEditText etPhone = sheetView.findViewById(R.id.etProfilePhone);
        TextInputEditText etEmail = sheetView.findViewById(R.id.etProfileEmail);
        TextInputEditText etDob = sheetView.findViewById(R.id.etProfileDob);
        TextInputEditText etAddress = sheetView.findViewById(R.id.etProfileAddress);

        activeDialogProfileImageView = ivDialogProfilePic;
        pendingProfilePicPath = null;

        // Enforce read-only username
        etUsername.setKeyListener(null);
        etUsername.setEnabled(false);
        etUsername.setFocusable(false);

        Account account = accountStore.getAccountById(accountId);
        String currentFullName = account != null ? account.getFullName() : "";
        String currentUsername = account != null ? account.getUsername() : "";
        String currentPhone = (account != null && account.getPhone() != null) ? account.getPhone() : "";
        String currentEmail = (account != null && account.getEmail() != null) ? account.getEmail() : "";
        String currentDob = (account != null && account.getDob() != null) ? account.getDob() : "";
        String currentAddress = (account != null && account.getAddress() != null) ? account.getAddress() : "";
        final String currentPicPath = (account != null && account.getProfilePicPath() != null) ? account.getProfilePicPath() : "";

        etUsername.setText(currentUsername);
        etFullName.setText(currentFullName);
        etPhone.setText(currentPhone);
        etEmail.setText(currentEmail);
        etDob.setText(currentDob);
        etAddress.setText(currentAddress);
        loadProfileImageIntoView(currentPicPath, ivDialogProfilePic);

        sheetView.findViewById(R.id.btnPickProfilePhoto).setOnClickListener(v -> {
            AppLockManager.getInstance().markExternalFlowStarted();
            pickProfilePhotoLauncher.launch("image/*");
        });

        etDob.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String formattedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                etDob.setText(formattedDate);
            }, cal.get(Calendar.YEAR) - 22, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        sheetView.findViewById(R.id.btnCancelProfile).setOnClickListener(v -> bottomSheet.dismiss());

        sheetView.findViewById(R.id.btnSaveProfile).setOnClickListener(v -> {
            tilFullName.setError(null);
            String newFullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
            String newPhone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
            String newEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String newDob = etDob.getText() != null ? etDob.getText().toString().trim() : "";
            String newAddress = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";

            if (newFullName.isEmpty()) {
                tilFullName.setError(getString(R.string.auth_err_empty));
                return;
            }

            if (pendingProfilePicPath != null) {
                accountStore.updateProfilePicPath(accountId, pendingProfilePicPath);
                if (!currentPicPath.isEmpty() && !currentPicPath.equals(pendingProfilePicPath)) {
                    ImageUtils.deleteProfileImage(this, currentPicPath);
                }
                pendingProfilePicPath = null;
            }

            boolean updated = accountStore.updateProfile(
                    accountId, newFullName, newPhone, newEmail, newAddress, newDob
            );

            if (updated) {
                bottomSheet.dismiss();
                loadDashboardData();
            } else {
                showMessage(binding.getRoot(), "Failed to save profile");
            }
        });

        bottomSheet.setOnDismissListener(d -> {
            activeDialogProfileImageView = null;
            // Clean up any unsaved pending image if the user cancelled/dismissed
            if (pendingProfilePicPath != null && !pendingProfilePicPath.equals(currentPicPath)) {
                ImageUtils.deleteProfileImage(this, pendingProfilePicPath);
                pendingProfilePicPath = null;
            }
        });
        bottomSheet.show();
    }

    // =========================================================================
    // 2. SECURITY: CHANGE PASSWORD (WITH CURRENT PASSWORD VERIFICATION & FIREBASE SYNC)
    // =========================================================================
    private void showChangePasswordBottomSheet() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        Account currentAccount = accountStore.getAccountById(accountId);
        if (currentAccount == null || GoogleAuthHelper.isGoogleSignInAccount(currentAccount)) {
            setChangePasswordRowVisible(false);
            return;
        }

        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_change_password, null);
        bottomSheet.setContentView(sheetView);

        TextInputLayout tilCurrent = sheetView.findViewById(R.id.tilCurrentPassword);
        TextInputLayout tilNew = sheetView.findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirm = sheetView.findViewById(R.id.tilConfirmNewPassword);

        TextInputEditText etCurrent = sheetView.findViewById(R.id.etCurrentPassword);
        TextInputEditText etNew = sheetView.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = sheetView.findViewById(R.id.etConfirmNewPassword);

        View btnCancel = sheetView.findViewById(R.id.btnCancelChangePassword);
        MaterialButton btnSubmit = sheetView.findViewById(R.id.btnSubmitChangePassword);

        btnCancel.setOnClickListener(v -> bottomSheet.dismiss());

        btnSubmit.setOnClickListener(v -> {
            tilCurrent.setError(null);
            tilNew.setError(null);
            tilConfirm.setError(null);

            String currentPass = etCurrent.getText() != null ? etCurrent.getText().toString() : "";
            String newPass = etNew.getText() != null ? etNew.getText().toString() : "";
            String confirmPass = etConfirm.getText() != null ? etConfirm.getText().toString() : "";

            if (currentPass.isEmpty()) {
                tilCurrent.setError(getString(R.string.auth_err_empty));
                return;
            }
            if (newPass.isEmpty()) {
                tilNew.setError(getString(R.string.auth_err_empty));
                return;
            }
            if (confirmPass.isEmpty()) {
                tilConfirm.setError(getString(R.string.auth_err_empty));
                return;
            }

            Account account = accountStore.getAccountById(accountId);
            if (account == null) {
                showMessage(binding.getRoot(), R.string.auth_err_hashing);
                return;
            }

            try {
                String currentComputedHash = PasswordHasher.hashPassword(currentPass, account.getSalt());
                if (!currentComputedHash.equals(account.getPasswordHash())) {
                    tilCurrent.setError(getString(R.string.err_incorrect_current_password));
                    return;
                }

                if (newPass.length() < 8) {
                    tilNew.setError(getString(R.string.auth_err_pass_length));
                    return;
                }
                if (!newPass.matches(".*[A-Z].*")) {
                    tilNew.setError(getString(R.string.auth_err_pass_upper));
                    return;
                }
                if (!newPass.matches(".*[a-z].*")) {
                    tilNew.setError(getString(R.string.auth_err_pass_lower));
                    return;
                }
                if (!newPass.matches(".*[0-9].*")) {
                    tilNew.setError(getString(R.string.auth_err_pass_digit));
                    return;
                }
                if (!newPass.matches(".*[^A-Za-z0-9].*")) {
                    tilNew.setError(getString(R.string.auth_err_pass_special));
                    return;
                }
                if (newPass.equals(currentPass)) {
                    tilNew.setError(getString(R.string.err_new_password_same));
                    return;
                }
                if (!newPass.equals(confirmPass)) {
                    tilConfirm.setError(getString(R.string.auth_err_password_mismatch));
                    return;
                }

                final String newSalt = PasswordHasher.generateSalt();
                final String newHash = PasswordHasher.hashPassword(newPass, newSalt);

                // Check if this account uses an email address (Firebase Email/Password account)
                String accountEmail = null;
                if (Patterns.EMAIL_ADDRESS.matcher(account.getUsername()).matches()) {
                    accountEmail = account.getUsername();
                } else if (account.getEmail() != null
                        && Patterns.EMAIL_ADDRESS.matcher(account.getEmail().trim()).matches()) {
                    accountEmail = account.getEmail().trim();
                }

                if (accountEmail != null) {
                    btnSubmit.setEnabled(false);
                    btnCancel.setEnabled(false);

                    GoogleAuthHelper.updateFirebasePassword(accountEmail, currentPass, newPass,
                            new GoogleAuthHelper.PasswordUpdateCallback() {
                                @Override
                                public void onUpdated() {
                                    btnSubmit.setEnabled(true);
                                    btnCancel.setEnabled(true);
                                    if (accountStore.updatePassword(accountId, newHash, newSalt)) {
                                        bottomSheet.dismiss();
                                        showMessage(binding.getRoot(), R.string.msg_password_updated);
                                    } else {
                                        showMessage(binding.getRoot(), R.string.auth_err_hashing);
                                    }
                                }

                                @Override
                                public void onError(@NonNull String message) {
                                    btnSubmit.setEnabled(true);
                                    btnCancel.setEnabled(true);
                                    tilCurrent.setError(message);
                                }
                            });
                } else {
                    // Offline-only username account: update local SQLite directly
                    if (accountStore.updatePassword(accountId, newHash, newSalt)) {
                        bottomSheet.dismiss();
                        showMessage(binding.getRoot(), R.string.msg_password_updated);
                    } else {
                        showMessage(binding.getRoot(), R.string.auth_err_hashing);
                    }
                }
            } catch (Exception e) {
                showMessage(binding.getRoot(), R.string.auth_err_hashing);
            }
        });

        bottomSheet.show();
    }

    // =========================================================================
    // 3. SECURITY: EXISTING PIN & BIOMETRIC APP LOCK CONFIGURATION
    // =========================================================================
    private void showConfigureAppLockDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);

        final EditText etPin = new EditText(this);
        etPin.setHint("4 to 6 digit PIN");
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        container.addView(etPin);

        final CheckBox cbBio = new CheckBox(this);
        cbBio.setText("Enable Biometric Unlock");
        cbBio.setChecked(sessionManager.isBiometricEnabled());
        boolean bioReady = BiometricAuthHelper.isBiometricReady(this);
        cbBio.setEnabled(bioReady);
        container.addView(cbBio);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_app_lock)
                .setView(container)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String rawPin = etPin.getText().toString().trim();
                    if (rawPin.length() < 4 || rawPin.length() > 6) {
                        Toast.makeText(this, "PIN must be 4 to 6 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        // M9: salted, slow hash; the attempt counter is reset
                        sessionManager.setPin(rawPin, cbBio.isChecked() && bioReady);
                        AppLockManager.getInstance().markUnlocked();
                        refreshStatusLabels();
                    } catch (Exception e) {
                        showMessage(binding.getRoot(), R.string.auth_err_hashing);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null);

        if (sessionManager.hasPinSetup()) {
            builder.setNeutralButton("Disable Lock", (dialog, which) -> {
                sessionManager.savePinSetup(null, false);
                AppLockManager.getInstance().markUnlocked();
                refreshStatusLabels();
            });
        }

        builder.show();
    }

    // =========================================================================
    // 4. PROFILE IMAGE & BACKUP HELPERS
    // =========================================================================
    private void loadProfileImageIntoView(@Nullable String path, @NonNull ImageView target) {
        if (!ImageUtils.loadProfileImageIntoView(path, target)) {
            target.setImageResource(R.drawable.ic_tutor_logo);
        }
    }

    private void exportBackupToUri(@NonNull Uri uri) {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        if (!isBackupTaskRunning.compareAndSet(false, true)) {
            Toast.makeText(this, "Backup operation already in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        final Context appContext = getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            String errorMsg = null;
            boolean exported = false;
            try (OutputStream out = appContext.getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    BackupRestoreManager.exportAccountBackupToStream(appContext, db, accountId, out);
                    exported = true;
                } else {
                    errorMsg = "Unable to open destination file.";
                }
            } catch (Exception e) {
                errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            } finally {
                DatabaseManager.getInstance().closeDatabase();
                isBackupTaskRunning.set(false);
            }

            final boolean success = exported;
            final String finalError = errorMsg;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (success) {
                    Toast.makeText(MainActivity.this, "Backup exported successfully!", Toast.LENGTH_SHORT).show();
                } else if (finalError != null) {
                    Toast.makeText(MainActivity.this, "Export failed: " + finalError, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // =========================================================================
    // RESTORE FLOW (M6): choose source -> (password) -> preview + confirmation -> safety backup -> merge
    // =========================================================================

    private void startRestoreFlow() {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;
        final List<File> safetyBackups = BackupRestoreManager.listSafetyBackups(getApplicationContext(), accountId);
        if (safetyBackups.isEmpty()) {
            importBackupLauncher.launch(new String[]{"*/*"});
            return;
        }
        BackupDialogs.chooseRestoreSource(this, safetyBackups.get(0).lastModified(),
                () -> importBackupLauncher.launch(new String[]{"*/*"}),
                () -> beginParseBackup(null, safetyBackups.get(0), null));
    }

    private void importBackupFromUri(@NonNull Uri uri) {
        beginParseBackup(uri, null, null);
    }

    /** Reads the backup in the background; nothing in the database is touched yet. */
    private void beginParseBackup(@Nullable Uri uri, @Nullable File file, @Nullable char[] password) {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) {
            if (password != null) Arrays.fill(password, '\0');
            return;
        }
        if (!isBackupTaskRunning.compareAndSet(false, true)) {
            if (password != null) Arrays.fill(password, '\0');
            Toast.makeText(this, "Backup operation already in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        final Context appContext = getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            BackupRestoreManager.ParsedBackup parsed = null;
            Exception error = null;
            try (InputStream in = file != null
                    ? new FileInputStream(file)
                    : appContext.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new IOException("Unable to open the backup file.");
                }
                parsed = BackupRestoreManager.parseBackup(in, password);
            } catch (Exception e) {
                error = e;
            } finally {
                if (password != null) Arrays.fill(password, '\0');
                isBackupTaskRunning.set(false);
            }

            final BackupRestoreManager.ParsedBackup finalParsed = parsed;
            final Exception finalError = error;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalError instanceof BackupCrypto.PasswordRequiredException) {
                    BackupDialogs.askExistingPassword(this, false, pw -> beginParseBackup(uri, file, pw), null);
                } else if (finalError instanceof BackupCrypto.WrongPasswordException) {
                    BackupDialogs.askExistingPassword(this, true, pw -> beginParseBackup(uri, file, pw), null);
                } else if (finalError != null) {
                    Toast.makeText(this, getString(R.string.backup_restore_read_failed_fmt,
                                    finalError.getMessage() != null ? finalError.getMessage() : "Invalid backup"),
                            Toast.LENGTH_LONG).show();
                } else if (finalParsed != null) {
                    BackupDialogs.confirmRestore(this, finalParsed, accountId,
                            () -> performRestore(finalParsed, accountId));
                }
            });
        });
    }

    /** Saves a safety backup first (restore is cancelled if that fails), then merges the backup. */
    private void performRestore(@NonNull BackupRestoreManager.ParsedBackup backup, @NonNull String accountId) {
        if (!isBackupTaskRunning.compareAndSet(false, true)) {
            Toast.makeText(this, "Backup operation already in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        final Context appContext = getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            String message;
            boolean restoreSucceeded = false;
            try {
                boolean safetySaved = true;
                try {
                    BackupRestoreManager.createSafetyBackup(appContext, db, accountId);
                } catch (Exception safetyError) {
                    safetySaved = false;
                }

                if (!safetySaved) {
                    message = getString(R.string.backup_safety_failed);
                } else {
                    try {
                        BackupRestoreManager.ImportResult result =
                                BackupRestoreManager.importParsedBackup(appContext, db, accountId, backup);
                        studentRepo.migrateLegacyStudentIds(accountId);
                        invoiceRepo.migrateLegacyInvoiceIds(accountId);
                        lastReconciledAccountId = accountId;
                        restoreSucceeded = true;
                        message = result.hasConflicts()
                                ? getString(R.string.backup_restore_done_conflicts_fmt, result.insertedCount,
                                result.updatedCount, result.skippedCount, result.conflictCount)
                                : getString(R.string.backup_restore_done_fmt, result.insertedCount,
                                result.updatedCount, result.skippedCount);
                    } catch (Exception e) {
                        // the merge runs in one transaction, so a failure leaves the data untouched
                        message = getString(R.string.backup_restore_failed_fmt,
                                e.getMessage() != null ? e.getMessage() : "Invalid backup");
                    }
                }
            } finally {
                DatabaseManager.getInstance().closeDatabase();
                isBackupTaskRunning.set(false);
            }

            final String finalMsg = message;
            final boolean finalSuccess = restoreSucceeded;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(MainActivity.this, finalMsg, Toast.LENGTH_LONG).show();
                if (finalSuccess) {
                    refreshStatusLabels();
                    loadDashboardData();
                }
            });
        });
    }

    /** M8: writes a password-protected, compressed backup. */
    private void exportEncryptedBackupToUri(@NonNull Uri uri, @NonNull char[] password) {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) {
            Arrays.fill(password, '\0');
            return;
        }
        if (!isBackupTaskRunning.compareAndSet(false, true)) {
            Arrays.fill(password, '\0');
            Toast.makeText(this, "Backup operation already in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        final Context appContext = getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            String errorMsg = null;
            boolean exported = false;
            try (OutputStream out = appContext.getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    BackupRestoreManager.exportEncryptedBackupToStream(appContext, db, accountId, out, password);
                    exported = true;
                } else {
                    errorMsg = "Unable to open destination file.";
                }
            } catch (Exception e) {
                errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            } finally {
                Arrays.fill(password, '\0');
                DatabaseManager.getInstance().closeDatabase();
                isBackupTaskRunning.set(false);
            }

            final boolean success = exported;
            final String finalError = errorMsg;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (success) {
                    Toast.makeText(MainActivity.this, R.string.backup_export_encrypted_ok, Toast.LENGTH_LONG).show();
                } else if (finalError != null) {
                    Toast.makeText(MainActivity.this, "Export failed: " + finalError, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
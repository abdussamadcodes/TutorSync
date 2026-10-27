package com.abdussamadcodes.tutorsync.ui.main;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.databinding.ActivityMainBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.OccurrenceGroup;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.BiometricAuthHelper;
import com.abdussamadcodes.tutorsync.security.PasswordHasher;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.account.PinLockActivity;
import com.abdussamadcodes.tutorsync.ui.account.SignInActivity;
import com.abdussamadcodes.tutorsync.ui.attendance.AttendanceBottomSheetFragment;
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
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
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

    private boolean isPinVerifiedThisSession = false;
    private static final String STATE_PIN_VERIFIED = "state_pin_verified";

    @Nullable
    private ImageView activeDialogProfileImageView;
    @Nullable
    private String pendingProfilePicPath;

    private final ActivityResultLauncher<String> pickProfilePhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String savedPath = copyProfileImageToInternalStorage(uri);
                    if (savedPath != null) {
                        pendingProfilePicPath = savedPath;
                        if (activeDialogProfileImageView != null) {
                            Bitmap bmp = BitmapFactory.decodeFile(savedPath);
                            if (bmp != null) {
                                activeDialogProfileImageView.setImageBitmap(bmp);
                            }
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> exportBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) {
                    exportBackupToUri(uri);
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

        if (savedInstanceState != null) {
            isPinVerifiedThisSession = savedInstanceState.getBoolean(STATE_PIN_VERIFIED, false);
        }

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

        String accountName = "Tutor";
        String username = "";
        String phone = "";
        String email = "";
        String profilePicPath = "";

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor cursor = db.query(
                AccountEntry.TABLE_NAME,
                null,
                AccountEntry.COLUMN_ID + " = ?",
                new String[]{accountId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                accountName = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_FULL_NAME);
                username = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_USERNAME);
                phone = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_PHONE);
                email = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_EMAIL);
                profilePicPath = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_PROFILE_PIC_PATH);
            }
        } catch (Exception ignored) {
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }

        if (accountName.isEmpty()) {
            accountName = "Tutor";
        }

        String currentLang = appPreferences.getLanguageCode();
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

        List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);
        List<Subject> subjects = subjectRepo.getActiveSubjectsForAccount(accountId);

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
        binding.btnOpenLanguageSettings.setOnClickListener(v ->
                startActivity(WelcomeActivity.createReopenIntent(this))
        );
        binding.rowLanguageSettings.setOnClickListener(v ->
                binding.btnOpenLanguageSettings.performClick()
        );

        binding.btnCycleTheme.setOnClickListener(v -> {
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
                exportBackupLauncher.launch("tutorsync_backup_" + System.currentTimeMillis() + ".json")
        );
        binding.btnImportBackup.setOnClickListener(v ->
                importBackupLauncher.launch(new String[]{"application/json", "*/*"})
        );

        // 5. Sign Out
        binding.btnSignOut.setOnClickListener(v -> {
            sessionManager.logout();
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

        String currentFullName = "";
        String currentUsername = "";
        String currentPhone = "";
        String currentEmail = "";
        String currentDob = "";
        String currentAddress = "";
        String currentPicPath = "";

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try (Cursor cursor = db.query(
                AccountEntry.TABLE_NAME,
                null,
                AccountEntry.COLUMN_ID + " = ?",
                new String[]{accountId}, null, null, null)) {
            if (cursor.moveToFirst()) {
                currentFullName = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_FULL_NAME);
                currentUsername = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_USERNAME);
                currentPhone = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_PHONE);
                currentEmail = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_EMAIL);
                currentDob = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_DOB);
                currentAddress = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_ADDRESS);
                currentPicPath = getColumnStringOrEmpty(cursor, AccountEntry.COLUMN_PROFILE_PIC_PATH);
            }
        } catch (Exception ignored) {
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }

        etUsername.setText(currentUsername);
        etFullName.setText(currentFullName);
        etPhone.setText(currentPhone);
        etEmail.setText(currentEmail);
        etDob.setText(currentDob);
        etAddress.setText(currentAddress);
        loadProfileImageIntoView(currentPicPath, ivDialogProfilePic);

        sheetView.findViewById(R.id.btnPickProfilePhoto).setOnClickListener(v ->
                pickProfilePhotoLauncher.launch("image/*")
        );

        etDob.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String formattedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                etDob.setText(formattedDate);
            }, cal.get(Calendar.YEAR) - 22, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        sheetView.findViewById(R.id.btnCancelProfile).setOnClickListener(v -> bottomSheet.dismiss());

        final String existingPicPath = currentPicPath;
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

            SQLiteDatabase writeDb = DatabaseManager.getInstance().openDatabase();
            try {
                ContentValues values = new ContentValues();
                // Username is intentionally never updated (read-only)
                values.put(AccountEntry.COLUMN_FULL_NAME, newFullName);
                values.put(AccountEntry.COLUMN_PHONE, newPhone);
                values.put(AccountEntry.COLUMN_EMAIL, newEmail);
                values.put(AccountEntry.COLUMN_DOB, newDob);
                values.put(AccountEntry.COLUMN_ADDRESS, newAddress);
                values.put(AccountEntry.COLUMN_PROFILE_PIC_PATH,
                        pendingProfilePicPath != null ? pendingProfilePicPath : existingPicPath);
                values.put(AccountEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

                writeDb.update(
                        AccountEntry.TABLE_NAME,
                        values,
                        AccountEntry.COLUMN_ID + " = ?",
                        new String[]{accountId}
                );
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }

            bottomSheet.dismiss();
            loadDashboardData();
        });

        bottomSheet.setOnDismissListener(d -> activeDialogProfileImageView = null);
        bottomSheet.show();
    }

    // =========================================================================
    // 2. SECURITY: CHANGE PASSWORD (WITH CURRENT PASSWORD VERIFICATION)
    // =========================================================================
    private void showChangePasswordBottomSheet() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_change_password, null);
        bottomSheet.setContentView(sheetView);

        TextInputLayout tilCurrent = sheetView.findViewById(R.id.tilCurrentPassword);
        TextInputLayout tilNew = sheetView.findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirm = sheetView.findViewById(R.id.tilConfirmNewPassword);

        TextInputEditText etCurrent = sheetView.findViewById(R.id.etCurrentPassword);
        TextInputEditText etNew = sheetView.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = sheetView.findViewById(R.id.etConfirmNewPassword);

        sheetView.findViewById(R.id.btnCancelChangePassword).setOnClickListener(v -> bottomSheet.dismiss());

        sheetView.findViewById(R.id.btnSubmitChangePassword).setOnClickListener(v -> {
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
                // 1. Verify current password against stored PBKDF2 hash & salt
                String currentComputedHash = PasswordHasher.hashPassword(currentPass, account.getSalt());
                if (!currentComputedHash.equals(account.getPasswordHash())) {
                    tilCurrent.setError(getString(R.string.err_incorrect_current_password));
                    return;
                }

                // 2. Enforce password strength rules (same as SignUpActivity)
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
                if (!newPass.matches(".*[@#$%^&+=!*()~_\\-].*")) {
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

                // 3. Hash new password with a fresh salt and update SQLite
                String newSalt = PasswordHasher.generateSalt();
                String newHash = PasswordHasher.hashPassword(newPass, newSalt);

                if (accountStore.updatePassword(accountId, newHash, newSalt)) {
                    bottomSheet.dismiss();
                    showMessage(binding.getRoot(), R.string.msg_password_updated);
                } else {
                    showMessage(binding.getRoot(), R.string.auth_err_hashing);
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
                        String hashedPin = PasswordHasher.hashPassword(rawPin, SessionManager.PIN_SALT_BASE64);
                        sessionManager.savePinSetup(hashedPin, cbBio.isChecked() && bioReady);
                        isPinVerifiedThisSession = true;
                        refreshStatusLabels();
                    } catch (Exception e) {
                        showMessage(binding.getRoot(), R.string.auth_err_hashing);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null);

        if (sessionManager.hasPinSetup()) {
            builder.setNeutralButton("Disable Lock", (dialog, which) -> {
                sessionManager.savePinSetup(null, false);
                refreshStatusLabels();
            });
        }

        builder.show();
    }

    // =========================================================================
    // 4. PROFILE IMAGE & BACKUP HELPERS
    // =========================================================================
    @NonNull
    private String getColumnStringOrEmpty(@NonNull Cursor cursor, @NonNull String columnName) {
        int idx = cursor.getColumnIndex(columnName);
        if (idx != -1 && !cursor.isNull(idx)) {
            String val = cursor.getString(idx);
            return val != null ? val : "";
        }
        return "";
    }

    private void loadProfileImageIntoView(@Nullable String path, @NonNull ImageView target) {
        if (path != null && !path.isEmpty()) {
            File file = new File(path);
            if (file.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap != null) {
                    target.setImageBitmap(bitmap);
                    return;
                }
            }
        }
        target.setImageResource(R.drawable.ic_tutor_logo);
    }

    @Nullable
    private String copyProfileImageToInternalStorage(@NonNull Uri sourceUri) {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return null;

        File destFile = new File(getFilesDir(), "profile_" + accountId + ".jpg");
        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) return null;
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void exportBackupToUri(@NonNull Uri uri) {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            JSONObject root = new JSONObject();
            root.put("backup_version", 1);
            root.put("exported_at", System.currentTimeMillis());

            // Export Tutor Profile metadata & Base64 avatar
            try (Cursor c = db.query(AccountEntry.TABLE_NAME, null,
                    AccountEntry.COLUMN_ID + " = ?", new String[]{accountId}, null, null, null)) {
                if (c.moveToFirst()) {
                    JSONObject profile = cursorRowToJson(c);
                    String picPath = getColumnStringOrEmpty(c, AccountEntry.COLUMN_PROFILE_PIC_PATH);
                    if (!picPath.isEmpty()) {
                        File picFile = new File(picPath);
                        if (picFile.exists()) {
                            byte[] bytes = readFileBytes(picFile);
                            if (bytes != null) {
                                profile.put("profile_pic_base64", Base64.encodeToString(bytes, Base64.NO_WRAP));
                            }
                        }
                    }
                    root.put("profile", profile);
                }
            }

            String[] accountScopedTables = new String[]{
                    TutorSyncContract.StudentEntry.TABLE_NAME,
                    TutorSyncContract.SubjectEntry.TABLE_NAME,
                    TutorSyncContract.ScheduleEntry.TABLE_NAME,
                    TutorSyncContract.ScheduleFeeHistoryEntry.TABLE_NAME,
                    TutorSyncContract.OccurrenceEntry.TABLE_NAME,
                    TutorSyncContract.AttendanceEntry.TABLE_NAME,
                    TutorSyncContract.LessonEntry.TABLE_NAME,
                    TutorSyncContract.FeeAgreementEntry.TABLE_NAME,
                    TutorSyncContract.InvoiceEntry.TABLE_NAME,
                    TutorSyncContract.InvoiceItemEntry.TABLE_NAME,
                    TutorSyncContract.PaymentEntry.TABLE_NAME,
                    TutorSyncContract.TransactionEntry.TABLE_NAME,
                    TutorSyncContract.PaymentAllocationEntry.TABLE_NAME,
                    TutorSyncContract.AuditLogEntry.TABLE_NAME
            };

            JSONObject tablesJson = new JSONObject();
            for (String table : accountScopedTables) {
                try (Cursor c = db.query(table, null, "account_id = ?", new String[]{accountId}, null, null, null)) {
                    JSONArray arr = new JSONArray();
                    while (c.moveToNext()) {
                        arr.put(cursorRowToJson(c));
                    }
                    tablesJson.put(table, arr);
                } catch (Exception ignored) {
                }
            }

            // Export student_subjects for this account's students
            try (Cursor c = db.rawQuery(
                    "SELECT ss.* FROM " + TutorSyncContract.StudentSubjectEntry.TABLE_NAME + " ss " +
                            "INNER JOIN " + TutorSyncContract.StudentEntry.TABLE_NAME + " s ON ss.student_id = s.id " +
                            "WHERE s.account_id = ?", new String[]{accountId})) {
                JSONArray arr = new JSONArray();
                while (c.moveToNext()) {
                    arr.put(cursorRowToJson(c));
                }
                tablesJson.put(TutorSyncContract.StudentSubjectEntry.TABLE_NAME, arr);
            } catch (Exception ignored) {
            }

            root.put("tables", tablesJson);

            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Backup exported successfully!", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    private void importBackupFromUri(@NonNull Uri uri) {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        db.beginTransaction();
        try {
            StringBuilder sb = new StringBuilder();
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return;
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            }

            JSONObject root = new JSONObject(sb.toString());

            // Restore Profile details (keeping current account ID, username, and password intact)
            JSONObject profile = root.optJSONObject("profile");
            if (profile != null) {
                ContentValues pv = new ContentValues();
                if (profile.has(AccountEntry.COLUMN_FULL_NAME)) {
                    pv.put(AccountEntry.COLUMN_FULL_NAME, profile.optString(AccountEntry.COLUMN_FULL_NAME));
                }
                if (profile.has(AccountEntry.COLUMN_PHONE)) {
                    pv.put(AccountEntry.COLUMN_PHONE, profile.optString(AccountEntry.COLUMN_PHONE));
                }
                if (profile.has(AccountEntry.COLUMN_EMAIL)) {
                    pv.put(AccountEntry.COLUMN_EMAIL, profile.optString(AccountEntry.COLUMN_EMAIL));
                }
                if (profile.has(AccountEntry.COLUMN_DOB)) {
                    pv.put(AccountEntry.COLUMN_DOB, profile.optString(AccountEntry.COLUMN_DOB));
                }
                if (profile.has(AccountEntry.COLUMN_ADDRESS)) {
                    pv.put(AccountEntry.COLUMN_ADDRESS, profile.optString(AccountEntry.COLUMN_ADDRESS));
                }
                String base64Pic = profile.optString("profile_pic_base64", "");
                if (!base64Pic.isEmpty()) {
                    byte[] imgBytes = Base64.decode(base64Pic, Base64.NO_WRAP);
                    File destFile = new File(getFilesDir(), "profile_" + accountId + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        fos.write(imgBytes);
                        pv.put(AccountEntry.COLUMN_PROFILE_PIC_PATH, destFile.getAbsolutePath());
                    }
                }
                if (pv.size() > 0) {
                    db.update(AccountEntry.TABLE_NAME, pv, AccountEntry.COLUMN_ID + " = ?", new String[]{accountId});
                }
            }

            JSONObject tablesJson = root.optJSONObject("tables");
            if (tablesJson != null) {
                String[] restoreOrder = new String[]{
                        TutorSyncContract.StudentEntry.TABLE_NAME,
                        TutorSyncContract.SubjectEntry.TABLE_NAME,
                        TutorSyncContract.StudentSubjectEntry.TABLE_NAME,
                        TutorSyncContract.ScheduleEntry.TABLE_NAME,
                        TutorSyncContract.ScheduleFeeHistoryEntry.TABLE_NAME,
                        TutorSyncContract.OccurrenceEntry.TABLE_NAME,
                        TutorSyncContract.AttendanceEntry.TABLE_NAME,
                        TutorSyncContract.LessonEntry.TABLE_NAME,
                        TutorSyncContract.FeeAgreementEntry.TABLE_NAME,
                        TutorSyncContract.InvoiceEntry.TABLE_NAME,
                        TutorSyncContract.InvoiceItemEntry.TABLE_NAME,
                        TutorSyncContract.PaymentEntry.TABLE_NAME,
                        TutorSyncContract.TransactionEntry.TABLE_NAME,
                        TutorSyncContract.PaymentAllocationEntry.TABLE_NAME,
                        TutorSyncContract.AuditLogEntry.TABLE_NAME
                };

                for (String table : restoreOrder) {
                    JSONArray rows = tablesJson.optJSONArray(table);
                    if (rows == null) continue;
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject rowObj = rows.getJSONObject(i);
                        ContentValues cv = jsonToContentValues(rowObj);
                        if (!TutorSyncContract.StudentSubjectEntry.TABLE_NAME.equals(table)) {
                            cv.put("account_id", accountId);
                        }
                        db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }
            }

            db.setTransactionSuccessful();
            Toast.makeText(this, "Backup restored successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            db.endTransaction();
            DatabaseManager.getInstance().closeDatabase();
        }

        loadDashboardData();
    }

    @NonNull
    private JSONObject cursorRowToJson(@NonNull Cursor c) throws Exception {
        JSONObject obj = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++) {
            String col = c.getColumnName(i);
            int type = c.getType(i);
            if (type == Cursor.FIELD_TYPE_NULL) {
                obj.put(col, JSONObject.NULL);
            } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                obj.put(col, c.getLong(i));
            } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                obj.put(col, c.getDouble(i));
            } else {
                obj.put(col, c.getString(i));
            }
        }
        return obj;
    }

    @NonNull
    private ContentValues jsonToContentValues(@NonNull JSONObject obj) throws Exception {
        ContentValues cv = new ContentValues();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (obj.isNull(key)) {
                cv.putNull(key);
            } else {
                Object val = obj.get(key);
                if (val instanceof Long) {
                    cv.put(key, (Long) val);
                } else if (val instanceof Integer) {
                    cv.put(key, (Integer) val);
                } else if (val instanceof Double) {
                    cv.put(key, (Double) val);
                } else {
                    cv.put(key, String.valueOf(val));
                }
            }
        }
        return cv;
    }

    @Nullable
    private byte[] readFileBytes(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
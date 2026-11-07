package com.abdussamadcodes.tutorsync.ui.main;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import com.abdussamadcodes.tutorsync.util.GoogleDriveBackupHelper;
import com.abdussamadcodes.tutorsync.util.ImageUtils;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Nullable
    private ValueEventListener connectionListener;
    @Nullable
    private DatabaseReference connectedRef;

    private static final int ACTION_NONE = 0;
    private static final int ACTION_DRIVE_BACKUP = 1;
    private static final int ACTION_DRIVE_RESTORE = 2;
    private int pendingDriveAction = ACTION_NONE;

    private final ActivityResultLauncher<Intent> driveAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    try {
                        GoogleSignInAccount account = GoogleAuthHelper.extractDriveAccountFromIntent(result.getData());
                        if (account != null) {
                            if (pendingDriveAction == ACTION_DRIVE_BACKUP) {
                                startDriveBackupFlow(account);
                            } else if (pendingDriveAction == ACTION_DRIVE_RESTORE) {
                                startDriveRestoreFlow(account);
                            }
                        } else {
                            Toast.makeText(this, "Google Drive permission denied.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Drive authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                }
                pendingDriveAction = ACTION_NONE;
            });

    private final ActivityResultLauncher<String> pickProfilePhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String accountId = sessionManager.getActiveAccountId();
                    if (accountId.isEmpty()) return;

                    String savedPath = ImageUtils.compressAndSaveImage(
                            this, uri, "profile_" + accountId, pendingProfilePicPath
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

        setupUI();
        setupListeners();
        applyResponsiveLayout(getResources().getConfiguration().orientation);

        int restoredTabId = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_ACTIVE_TAB, lastActiveTabId)
                : lastActiveTabId;
        binding.bottomNavigation.setSelectedItemId(restoredTabId);
        switchToTab(restoredTabId);

        attachRealtimePresenceListener();
        syncAccountToFirestore();
        fetchDailyQuoteFromApi();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyResponsiveLayout(newConfig.orientation);
    }

    private void applyResponsiveLayout(int orientation) {
        if (binding == null) return;
        try {
            if (binding.tvStudentCount.getParent() instanceof ViewGroup) {
                ViewGroup statContainer = (ViewGroup) binding.tvStudentCount.getParent().getParent();
                if (statContainer instanceof LinearLayout) {
                    LinearLayout layout = (LinearLayout) statContainer;
                    layout.setOrientation(LinearLayout.HORIZONTAL);
                    for (int i = 0; i < layout.getChildCount(); i++) {
                        View child = layout.getChildAt(i);
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                        lp.width = 0;
                        lp.weight = 1f;
                        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        }
                        child.setLayoutParams(lp);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ResponsiveLayout", "Could not apply responsive layout dynamically", e);
        }
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
            setFirebaseRealtimePresence(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectedRef != null && connectionListener != null) {
            connectedRef.removeEventListener(connectionListener);
        }
    }

    // =========================================================================
    // FIREBASE REALTIME DB, FIRESTORE & HTTP REST JSON API
    // =========================================================================

    private String getActiveTabName(int tabId) {
        if (tabId == R.id.nav_directory) return "DIRECTORY";
        if (tabId == R.id.nav_settings) return "SETTINGS";
        return "HOME_DASHBOARD";
    }

    private static String formatMinutesOfDay(int totalMinutes) {
        int hours24 = (totalMinutes / 60) % 24;
        int mins = totalMinutes % 60;
        String period = hours24 >= 12 ? "PM" : "AM";
        int hours12 = hours24 % 12;
        if (hours12 == 0) hours12 = 12;
        return String.format(Locale.US, "%02d:%02d %s", hours12, mins, period);
    }

    private void attachRealtimePresenceListener() {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        try {
            FirebaseDatabase db = FirebaseDatabase.getInstance();
            final DatabaseReference presenceRef = db.getReference("status/" + accountId);
            connectedRef = db.getReference(".info/connected");

            if (connectionListener != null) {
                connectedRef.removeEventListener(connectionListener);
            }

            connectionListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean connected = snapshot.getValue(Boolean.class);
                    if (Boolean.TRUE.equals(connected) && sessionManager.hasActiveSession()) {
                        Map<String, Object> offlineStatus = new HashMap<>();
                        offlineStatus.put("isOnline", false);
                        offlineStatus.put("sessionState", "OFFLINE");
                        offlineStatus.put("lastActive", ServerValue.TIMESTAMP);

                        Map<String, Object> onlineStatus = new HashMap<>();
                        onlineStatus.put("isOnline", true);
                        onlineStatus.put("sessionState", "ACTIVE");
                        onlineStatus.put("activeScreen", getActiveTabName(lastActiveTabId));
                        onlineStatus.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                        onlineStatus.put("androidVersion", "Android " + Build.VERSION.RELEASE);
                        onlineStatus.put("lastActive", ServerValue.TIMESTAMP);

                        // Using updateChildren preserves workspaceSummary and todaySchedule nodes!
                        presenceRef.onDisconnect().updateChildren(offlineStatus).addOnCompleteListener(task -> {
                            if (sessionManager.hasActiveSession()) {
                                presenceRef.updateChildren(onlineStatus);
                            }
                        });
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };
            connectedRef.addValueEventListener(connectionListener);
        } catch (Exception e) {
            Log.e("FirebaseRealtime", "Failed to attach presence listener", e);
        }
    }

    private void setFirebaseRealtimePresence(boolean isOnline) {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;
        try {
            DatabaseReference presenceRef = FirebaseDatabase.getInstance().getReference("status/" + accountId);
            Map<String, Object> status = new HashMap<>();
            status.put("isOnline", isOnline);
            status.put("sessionState", isOnline ? "ACTIVE" : "SIGNED_OUT");
            status.put("activeScreen", getActiveTabName(lastActiveTabId));
            status.put("lastActive", ServerValue.TIMESTAMP);

            if (isOnline) {
                Map<String, Object> offlineStatus = new HashMap<>();
                offlineStatus.put("isOnline", false);
                offlineStatus.put("sessionState", "OFFLINE");
                offlineStatus.put("lastActive", ServerValue.TIMESTAMP);
                presenceRef.onDisconnect().updateChildren(offlineStatus);
            } else {
                presenceRef.onDisconnect().cancel();
            }
            presenceRef.updateChildren(status);
        } catch (Exception ignored) {
        }
    }

    /**
     * Pushes rich live workspace metrics and today's class schedule board to Firebase Realtime Database
     * under status/{accountId} whenever the dashboard refreshes.
     */
    private void syncRealtimeWorkspaceTelemetry(
            @NonNull String accountId,
            @Nullable Account account,
            @NonNull List<Student> students,
            @NonNull List<Subject> subjects,
            @NonNull List<OccurrenceGroup> todayGroups,
            int needsAttendanceCount,
            int totalRevenueMinor,
            @NonNull String currentLang) {
        try {
            DatabaseReference presenceRef = FirebaseDatabase.getInstance().getReference("status/" + accountId);

            Map<String, String> studentNames = new HashMap<>();
            for (Student s : students) {
                studentNames.put(s.getId(), s.getName());
            }
            Map<String, String> subjectTitles = new HashMap<>();
            for (Subject sub : subjects) {
                subjectTitles.put(sub.getId(), sub.getRawTitle());
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accountId", accountId);
            payload.put("tutorName", (account != null && !account.getFullName().trim().isEmpty()) ? account.getFullName() : "Tutor");
            payload.put("username", (account != null && account.getUsername() != null) ? account.getUsername() : "");
            payload.put("isOnline", true);
            payload.put("sessionState", "ACTIVE");
            payload.put("activeScreen", getActiveTabName(lastActiveTabId));
            payload.put("language", currentLang);
            payload.put("themeMode", appPreferences.getThemeMode());
            payload.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
            payload.put("androidVersion", "Android " + Build.VERSION.RELEASE);
            payload.put("lastActive", ServerValue.TIMESTAMP);

            // Nested live workspace summary metrics
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("activeStudentCount", students.size());
            summary.put("activeSubjectCount", subjects.size());
            summary.put("todayClassCount", todayGroups.size());
            summary.put("pendingAttendanceCount", needsAttendanceCount);
            summary.put("totalRevenueMinor", totalRevenueMinor);
            summary.put("totalRevenueFormatted", String.format(Locale.US, "৳ %.2f", totalRevenueMinor / 100.0));
            payload.put("workspaceSummary", summary);

            // Nested live board of today's scheduled classes
            Map<String, Object> todayScheduleMap = new LinkedHashMap<>();
            for (int i = 0; i < todayGroups.size(); i++) {
                OccurrenceGroup g = todayGroups.get(i);
                Map<String, Object> slot = new LinkedHashMap<>();
                String stName = studentNames.get(g.getStudentId());
                slot.put("studentName", stName != null ? stName : "Student");
                slot.put("startTime", formatMinutesOfDay(g.getStartTimeMinutes()));
                slot.put("durationMinutes", g.getDurationMinutes());

                List<String> subNames = new ArrayList<>();
                for (String subId : g.getSubjectIds()) {
                    String title = subjectTitles.get(subId);
                    if (title != null && !title.isEmpty()) subNames.add(title);
                }
                slot.put("subjects", subNames.isEmpty() ? "General" : android.text.TextUtils.join(", ", subNames));
                todayScheduleMap.put("session_" + (i + 1), slot);
            }
            payload.put("todaySchedule", todayScheduleMap);

            presenceRef.updateChildren(payload);
        } catch (Exception e) {
            Log.e("FirebaseRealtime", "Failed to sync realtime workspace telemetry", e);
        }
    }

    private void syncAccountToFirestore() {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Account account = accountStore.getAccountById(accountId);
                List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);
                List<Subject> subjects = subjectRepo.getActiveSubjectsForAccount(accountId);

                if (account != null) {
                    String localPicPath = account.getProfilePicPath();
                    boolean hasLocalPhoto = localPicPath != null && !localPicPath.trim().isEmpty() && new File(localPicPath).exists();
                    Map<String, Object> tutorDoc = new HashMap<>();
                    tutorDoc.put("accountId", accountId);
                    tutorDoc.put("fullName", account.getFullName());
                    tutorDoc.put("username", account.getUsername());
                    tutorDoc.put("email", account.getEmail() != null ? account.getEmail() : "");
                    tutorDoc.put("hasLocalProfilePhoto", hasLocalPhoto);
                    tutorDoc.put("localProfilePicPath", hasLocalPhoto ? localPicPath : "");
                    tutorDoc.put("activeStudentCount", students.size());
                    tutorDoc.put("activeSubjectCount", subjects.size());
                    tutorDoc.put("lastSyncedAt", System.currentTimeMillis());

                    FirebaseFirestore.getInstance().collection("tutors").document(accountId).set(tutorDoc);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void fetchDailyQuoteFromApi() {
        if (!GoogleAuthHelper.isNetworkConnected(this)) return;
        AppExecutors.getInstance().diskIO().execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.adviceslip.com/advice");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                    JSONObject jsonRoot = new JSONObject(response.toString());
                    JSONObject slip = jsonRoot.optJSONObject("slip");
                    if (slip != null) {
                        final String advice = slip.optString("advice", "");
                        if (!advice.isEmpty()) {
                            AppExecutors.getInstance().mainThread().execute(() -> {
                                if (isFinishing() || isDestroyed() || binding == null) return;
                                if (binding.tvGreeting.getParent() instanceof ViewGroup) {
                                    ViewGroup parent = (ViewGroup) binding.tvGreeting.getParent();
                                    if (parent.getChildCount() > 1 && parent.getChildAt(1) instanceof TextView) {
                                        TextView tvSub = (TextView) parent.getChildAt(1);
                                        tvSub.setText("Tip: " + advice);
                                    }
                                }
                            });
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // =========================================================================
    // CORE UI & DASHBOARD LOGIC
    // =========================================================================

    private void setupUI() {
        occurrenceAdapter = new OccurrenceAdapter(group -> {
            AttendanceBottomSheetFragment bottomSheet = AttendanceBottomSheetFragment.newInstance(
                    sessionManager.getActiveAccountId(), group.getStudentId(), new ArrayList<>(group.getOccurrenceIds())
            );
            bottomSheet.show(getSupportFragmentManager(), AttendanceBottomSheetFragment.TAG);
        });

        binding.rvOccurrences.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOccurrences.setAdapter(occurrenceAdapter);
        getSupportFragmentManager().setFragmentResultListener("attendance_request_key", this, (requestKey, result) -> {
            if (result.getBoolean("attendance_recorded", false)) loadDashboardData();
        });
        binding.bottomNavigation.setOnItemSelectedListener(item -> switchToTab(item.getItemId()));
    }

    private boolean switchToTab(int itemId) {
        if (itemId != R.id.nav_home && itemId != R.id.nav_directory && itemId != R.id.nav_settings) return false;
        lastActiveTabId = itemId;
        binding.tabHome.setVisibility(itemId == R.id.nav_home ? View.VISIBLE : View.GONE);
        binding.tabDirectory.setVisibility(itemId == R.id.nav_directory ? View.VISIBLE : View.GONE);
        binding.tabSettings.setVisibility(itemId == R.id.nav_settings ? View.VISIBLE : View.GONE);

        // Live-update the active screen in Realtime Database when switching bottom tabs
        if (sessionManager != null && sessionManager.hasActiveSession()) {
            String accountId = sessionManager.getActiveAccountId();
            if (!accountId.isEmpty()) {
                try {
                    Map<String, Object> tabUpdate = new HashMap<>();
                    tabUpdate.put("activeScreen", getActiveTabName(itemId));
                    tabUpdate.put("lastActive", ServerValue.TIMESTAMP);
                    FirebaseDatabase.getInstance().getReference("status/" + accountId).updateChildren(tabUpdate);
                } catch (Exception ignored) {
                }
            }
        }
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
                        if (g.getStudentId().equals(occ.getStudentId()) && g.getStartTimeMinutes() == occ.getStartTimeMinutes() && g.getDurationMinutes() == occ.getDurationMinutes()) {
                            if (occ.getSubjectId() != null && !g.getSubjectIds().contains(occ.getSubjectId())) g.getSubjectIds().add(occ.getSubjectId());
                            if (!g.getOccurrenceIds().contains(occ.getId())) g.getOccurrenceIds().add(occ.getId());
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

                // Push expanded live telemetry & today's class schedule to Firebase Realtime Database
                syncRealtimeWorkspaceTelemetry(
                        accountId, account, students, subjects, groups,
                        needsAttendanceCount, totalRevenueMinor, currentLang
                );

                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (isFinishing() || isDestroyed() || binding == null) return;
                    if (!accountId.equals(sessionManager.getActiveAccountId())) return;

                    setChangePasswordRowVisible(!isGoogleAccount);
                    if (needsAttendanceCount > 0) {
                        binding.tvAttendanceHistoryBadge.setVisibility(View.VISIBLE);
                        binding.tvAttendanceHistoryBadge.setText(needsAttendanceCount > 99 ? "99+" : String.valueOf(needsAttendanceCount));
                        binding.tvAttendanceHistorySub.setText(getString(R.string.dashboard_attendance_history_sub_pending, needsAttendanceCount));
                    } else {
                        binding.tvAttendanceHistoryBadge.setVisibility(View.GONE);
                        binding.tvAttendanceHistorySub.setText(R.string.dashboard_attendance_history_sub);
                    }

                    String accountName = (account != null && !account.getFullName().trim().isEmpty()) ? account.getFullName() : "Tutor";
                    String username = (account != null) ? account.getUsername() : "";
                    String phone = (account != null && account.getPhone() != null) ? account.getPhone() : "";
                    String email = (account != null && account.getEmail() != null) ? account.getEmail() : "";
                    String profilePicPath = (account != null && account.getProfilePicPath() != null) ? account.getProfilePicPath() : "";

                    binding.tvGreeting.setText(AppPreferences.LANG_BANGLA.equals(currentLang) ? "হ্যালো, " + accountName + "!" : "Hello, " + accountName + "!");
                    binding.tvSettingsProfileName.setText(accountName);

                    if (!username.isEmpty()) {
                        binding.tvSettingsUsername.setText("@" + username);
                        binding.tvSettingsUsername.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvSettingsUsername.setVisibility(View.GONE);
                    }

                    if (!phone.isEmpty() && !email.isEmpty()) binding.tvSettingsProfileSubtitle.setText(phone + " • " + email);
                    else if (!phone.isEmpty()) binding.tvSettingsProfileSubtitle.setText(phone);
                    else if (!email.isEmpty()) binding.tvSettingsProfileSubtitle.setText(email);
                    else binding.tvSettingsProfileSubtitle.setText(R.string.profile_tap_to_edit);

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
        binding.tvActiveLanguageValue.setText(AppPreferences.LANG_BANGLA.equals(currentLang) ? "বাংলা" : "English");
        String themeMode = appPreferences.getThemeMode();
        if (AppPreferences.THEME_LIGHT.equals(themeMode)) binding.tvActiveThemeValue.setText(R.string.theme_light);
        else if (AppPreferences.THEME_DARK.equals(themeMode)) binding.tvActiveThemeValue.setText(R.string.theme_dark);
        else binding.tvActiveThemeValue.setText(R.string.theme_system);

        if (sessionManager.hasPinSetup()) {
            binding.tvAppLockStatusValue.setText(sessionManager.isBiometricEnabled() ? "PIN + Biometric" : "PIN Enabled");
        } else {
            binding.tvAppLockStatusValue.setText(R.string.app_lock_status_off);
        }
    }

    private void setupListeners() {
        binding.btnQuickAddStudent.setOnClickListener(v -> startActivity(new Intent(this, StudentFormActivity.class)));
        binding.btnQuickAddSubject.setOnClickListener(v -> startActivity(new Intent(this, SubjectFormActivity.class)));
        binding.btnQuickAddSchedule.setOnClickListener(v -> startActivity(new Intent(this, ScheduleFormActivity.class)));
        binding.cardManageStudents.setOnClickListener(v -> startActivity(new Intent(this, StudentListActivity.class)));
        binding.cardManageSubjects.setOnClickListener(v -> startActivity(new Intent(this, SubjectListActivity.class)));
        binding.cardManageSchedules.setOnClickListener(v -> startActivity(new Intent(this, ScheduleListActivity.class)));
        binding.cardAttendanceHistory.setOnClickListener(v -> startActivity(new Intent(this, AttendanceHistoryActivity.class)));
        binding.cardRevenue.setOnClickListener(v -> startActivity(new Intent(this, InvoiceListActivity.class)));

        binding.cardTutorProfile.setOnClickListener(v -> showEditProfileBottomSheet());
        binding.btnEditProfile.setOnClickListener(v -> showEditProfileBottomSheet());

        binding.btnConfigureAppLock.setOnClickListener(v -> showConfigureAppLockDialog());
        binding.rowAppLock.setOnClickListener(v -> showConfigureAppLockDialog());

        binding.btnChangePassword.setOnClickListener(v -> showChangePasswordBottomSheet());
        binding.rowChangePassword.setOnClickListener(v -> showChangePasswordBottomSheet());

        binding.btnOpenLanguageSettings.setOnClickListener(v -> {
            lastActiveTabId = R.id.nav_settings;
            startActivity(WelcomeActivity.createReopenIntent(this));
        });
        binding.rowLanguageSettings.setOnClickListener(v -> binding.btnOpenLanguageSettings.performClick());

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
        binding.rowThemeSettings.setOnClickListener(v -> binding.btnCycleTheme.performClick());

        // 4. Cloud & Local Backup/Restore Entry Points
        binding.btnExportBackup.setOnClickListener(v -> showBackupDestinationDialog());
        binding.btnImportBackup.setOnClickListener(v -> showRestoreSourceDialog());

        binding.btnSignOut.setOnClickListener(v -> {
            if (connectedRef != null && connectionListener != null) connectedRef.removeEventListener(connectionListener);
            setFirebaseRealtimePresence(false);
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
    // 1. TUTOR PROFILE SETTINGS
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
        etUsername.setKeyListener(null);
        etUsername.setEnabled(false);
        etUsername.setFocusable(false);

        Account account = accountStore.getAccountById(accountId);
        etUsername.setText(account != null ? account.getUsername() : "");
        etFullName.setText(account != null ? account.getFullName() : "");
        etPhone.setText((account != null && account.getPhone() != null) ? account.getPhone() : "");
        etEmail.setText((account != null && account.getEmail() != null) ? account.getEmail() : "");
        etDob.setText((account != null && account.getDob() != null) ? account.getDob() : "");
        etAddress.setText((account != null && account.getAddress() != null) ? account.getAddress() : "");
        final String currentPicPath = (account != null && account.getProfilePicPath() != null) ? account.getProfilePicPath() : "";
        loadProfileImageIntoView(currentPicPath, ivDialogProfilePic);

        sheetView.findViewById(R.id.btnPickProfilePhoto).setOnClickListener(v -> {
            AppLockManager.getInstance().markExternalFlowStarted();
            pickProfilePhotoLauncher.launch("image/*");
        });

        etDob.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                etDob.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth));
            }, cal.get(Calendar.YEAR) - 22, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        sheetView.findViewById(R.id.btnCancelProfile).setOnClickListener(v -> bottomSheet.dismiss());

        sheetView.findViewById(R.id.btnSaveProfile).setOnClickListener(v -> {
            tilFullName.setError(null);
            String newFullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
            if (newFullName.isEmpty()) {
                tilFullName.setError(getString(R.string.auth_err_empty));
                return;
            }
            if (pendingProfilePicPath != null) {
                accountStore.updateProfilePicPath(accountId, pendingProfilePicPath);
                if (!currentPicPath.isEmpty() && !currentPicPath.equals(pendingProfilePicPath)) ImageUtils.deleteProfileImage(this, currentPicPath);
                pendingProfilePicPath = null;
            }
            if (accountStore.updateProfile(accountId, newFullName, etPhone.getText().toString().trim(), etEmail.getText().toString().trim(), etAddress.getText().toString().trim(), etDob.getText().toString().trim())) {
                syncAccountToFirestore();
                bottomSheet.dismiss();
                loadDashboardData();
            } else showMessage(binding.getRoot(), "Failed to save profile");
        });

        bottomSheet.setOnDismissListener(d -> {
            activeDialogProfileImageView = null;
            if (pendingProfilePicPath != null && !pendingProfilePicPath.equals(currentPicPath)) {
                ImageUtils.deleteProfileImage(this, pendingProfilePicPath);
                pendingProfilePicPath = null;
            }
        });
        bottomSheet.show();
    }

    // =========================================================================
    // 2. SECURITY: CHANGE PASSWORD
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

        sheetView.findViewById(R.id.btnCancelChangePassword).setOnClickListener(v -> bottomSheet.dismiss());
        MaterialButton btnSubmit = sheetView.findViewById(R.id.btnSubmitChangePassword);

        btnSubmit.setOnClickListener(v -> {
            tilCurrent.setError(null); tilNew.setError(null); tilConfirm.setError(null);
            String currentPass = etCurrent.getText() != null ? etCurrent.getText().toString() : "";
            String newPass = etNew.getText() != null ? etNew.getText().toString() : "";
            String confirmPass = etConfirm.getText() != null ? etConfirm.getText().toString() : "";

            if (currentPass.isEmpty()) { tilCurrent.setError(getString(R.string.auth_err_empty)); return; }
            if (newPass.isEmpty()) { tilNew.setError(getString(R.string.auth_err_empty)); return; }
            if (confirmPass.isEmpty()) { tilConfirm.setError(getString(R.string.auth_err_empty)); return; }

            try {
                if (!PasswordHasher.hashPassword(currentPass, currentAccount.getSalt()).equals(currentAccount.getPasswordHash())) {
                    tilCurrent.setError(getString(R.string.err_incorrect_current_password)); return;
                }
                if (newPass.length() < 8) { tilNew.setError(getString(R.string.auth_err_pass_length)); return; }
                if (!newPass.matches(".*[A-Z].*")) { tilNew.setError(getString(R.string.auth_err_pass_upper)); return; }
                if (!newPass.matches(".*[a-z].*")) { tilNew.setError(getString(R.string.auth_err_pass_lower)); return; }
                if (!newPass.matches(".*[0-9].*")) { tilNew.setError(getString(R.string.auth_err_pass_digit)); return; }
                if (!newPass.matches(".*[^A-Za-z0-9].*")) { tilNew.setError(getString(R.string.auth_err_pass_special)); return; }
                if (newPass.equals(currentPass)) { tilNew.setError(getString(R.string.err_new_password_same)); return; }
                if (!newPass.equals(confirmPass)) { tilConfirm.setError(getString(R.string.auth_err_password_mismatch)); return; }

                final String newSalt = PasswordHasher.generateSalt();
                final String newHash = PasswordHasher.hashPassword(newPass, newSalt);

                String accountEmail = Patterns.EMAIL_ADDRESS.matcher(currentAccount.getUsername()).matches() ? currentAccount.getUsername()
                        : (currentAccount.getEmail() != null && Patterns.EMAIL_ADDRESS.matcher(currentAccount.getEmail().trim()).matches() ? currentAccount.getEmail().trim() : null);

                if (accountEmail != null) {
                    if (!GoogleAuthHelper.isNetworkConnected(this)) { tilNew.setError("Internet required."); return; }
                    btnSubmit.setEnabled(false);
                    GoogleAuthHelper.updateFirebasePassword(this, accountEmail, currentPass, newPass, new GoogleAuthHelper.PasswordUpdateCallback() {
                        @Override public void onUpdated() {
                            if (accountStore.updatePassword(accountId, newHash, newSalt)) { bottomSheet.dismiss(); showMessage(binding.getRoot(), R.string.msg_password_updated); }
                        }
                        @Override public void onError(@NonNull String msg) { btnSubmit.setEnabled(true); tilCurrent.setError(msg); }
                    });
                } else {
                    if (accountStore.updatePassword(accountId, newHash, newSalt)) { bottomSheet.dismiss(); showMessage(binding.getRoot(), R.string.msg_password_updated); }
                }
            } catch (Exception e) { showMessage(binding.getRoot(), R.string.auth_err_hashing); }
        });
        bottomSheet.show();
    }

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
                    if (rawPin.length() < 4 || rawPin.length() > 6) { Toast.makeText(this, "PIN must be 4 to 6 digits", Toast.LENGTH_SHORT).show(); return; }
                    try {
                        sessionManager.setPin(rawPin, cbBio.isChecked() && bioReady);
                        AppLockManager.getInstance().markUnlocked();
                        refreshStatusLabels();
                    } catch (Exception e) { showMessage(binding.getRoot(), R.string.auth_err_hashing); }
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
    // 5. CLOUD & LOCAL BACKUP UI SELECTION & ENGINE
    // =========================================================================

    private void showBackupDestinationDialog() {
        String[] options = {"Local Device Storage", "Google Drive Cloud"};
        final int[] selectedIndex = {0};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Backup Destination")
                .setSingleChoiceItems(options, 0, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton(R.string.common_ok, (dialog, which) -> {
                    if (selectedIndex[0] == 0) {
                        String timeStamp = new java.text.SimpleDateFormat("yyyy-MMM-dd_hh-mm-a", java.util.Locale.US).format(new java.util.Date());
                        String baseName = "TutorSync_Backup_" + timeStamp;

                        BackupDialogs.askNewPassword(this,
                                pw -> {
                                    pendingBackupPassword = pw;
                                    encryptedExportLauncher.launch(baseName + ".tsbackup");
                                },
                                () -> exportBackupLauncher.launch(baseName + ".json"));
                    } else {
                        pendingDriveAction = ACTION_DRIVE_BACKUP;
                        GoogleSignInAccount account = GoogleAuthHelper.getSignedInDriveAccount(this);
                        if (account != null) {
                            startDriveBackupFlow(account);
                        } else {
                            driveAuthLauncher.launch(GoogleAuthHelper.getDriveAccessIntent(this));
                        }
                    }
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void showRestoreSourceDialog() {
        String[] options = {"Local Device Storage", "Google Drive Cloud"};
        final int[] selectedIndex = {0};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore Source")
                .setSingleChoiceItems(options, 0, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton(R.string.common_ok, (dialog, which) -> {
                    if (selectedIndex[0] == 0) {
                        startRestoreFlow();
                    } else {
                        pendingDriveAction = ACTION_DRIVE_RESTORE;
                        GoogleSignInAccount account = GoogleAuthHelper.getSignedInDriveAccount(this);
                        if (account != null) {
                            startDriveRestoreFlow(account);
                        } else {
                            driveAuthLauncher.launch(GoogleAuthHelper.getDriveAccessIntent(this));
                        }
                    }
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    // --- Google Drive Cloud Backup Routine ---

    private void startDriveBackupFlow(@NonNull GoogleSignInAccount account) {
        BackupDialogs.askNewPassword(this,
                pw -> performDriveBackup(account, pw, true),
                () -> performDriveBackup(account, null, false)
        );
    }

    private void performDriveBackup(@NonNull GoogleSignInAccount account, @Nullable char[] password, boolean isEncrypted) {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty() || !isBackupTaskRunning.compareAndSet(false, true)) {
            if (password != null) Arrays.fill(password, '\0');
            return;
        }

        Toast.makeText(this, "Uploading to Google Drive...", Toast.LENGTH_SHORT).show();
        final Context appContext = getApplicationContext();

        AppExecutors.getInstance().diskIO().execute(() -> {
            boolean success = false;
            String errorMsg = null;
            File tempFile = null;
            SQLiteDatabase db = null;
            try {
                db = DatabaseManager.getInstance().openDatabase();
                GoogleDriveBackupHelper driveHelper = new GoogleDriveBackupHelper(appContext, account);

                // Add Readable Date and Time to the file name
                String timeStamp = new java.text.SimpleDateFormat("yyyy-MMM-dd_hh-mm-a", java.util.Locale.US).format(new java.util.Date());
                String extension = isEncrypted ? ".tsbackup" : ".json";
                String mimeType = isEncrypted ? "application/octet-stream" : "application/json";

                tempFile = new File(appContext.getCacheDir(), "TutorSync_Drive_" + timeStamp + extension);

                try (OutputStream out = new FileOutputStream(tempFile)) {
                    if (isEncrypted) {
                        BackupRestoreManager.exportEncryptedBackupToStream(appContext, db, accountId, out, password);
                    } else {
                        BackupRestoreManager.exportAccountBackupToStream(appContext, db, accountId, out);
                    }
                }

                // This method safely uploads first, then deletes old backups
                driveHelper.uploadBackup(tempFile, mimeType);
                success = true;

            } catch (Exception e) {
                errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            } finally {
                if (password != null) Arrays.fill(password, '\0');
                if (db != null) DatabaseManager.getInstance().closeDatabase();
                if (tempFile != null && tempFile.exists()) tempFile.delete();
                isBackupTaskRunning.set(false);
            }

            final boolean finalSuccess = success;
            final String finalError = errorMsg;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalSuccess) {
                    Toast.makeText(MainActivity.this, "Successfully backed up to Google Drive!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Drive Backup failed: " + finalError, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // --- Google Drive Cloud Restore Routine ---

    private void startDriveRestoreFlow(@NonNull GoogleSignInAccount account) {
        if (!isBackupTaskRunning.compareAndSet(false, true)) return;
        Toast.makeText(this, "Fetching Google Drive backups...", Toast.LENGTH_SHORT).show();

        AppExecutors.getInstance().diskIO().execute(() -> {
            List<com.google.api.services.drive.model.File> files = null;
            String error = null;
            try {
                GoogleDriveBackupHelper driveHelper = new GoogleDriveBackupHelper(getApplicationContext(), account);
                files = driveHelper.listBackups();
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                isBackupTaskRunning.set(false);
            }

            final List<com.google.api.services.drive.model.File> finalFiles = files;
            final String finalError = error;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalError != null) {
                    Toast.makeText(this, "Failed to list Drive backups: " + finalError, Toast.LENGTH_LONG).show();
                    return;
                }
                if (finalFiles == null || finalFiles.isEmpty()) {
                    Toast.makeText(this, "No TutorSync backups found in Google Drive.", Toast.LENGTH_LONG).show();
                    return;
                }

                String[] items = new String[finalFiles.size()];
                for (int i = 0; i < finalFiles.size(); i++) {
                    com.google.api.services.drive.model.File f = finalFiles.get(i);

                    // Extract real Date metadata for a beautiful UI string
                    long timeMs = f.getCreatedTime() != null ? f.getCreatedTime().getValue() : 0;
                    String displayDate = timeMs > 0
                            ? java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new java.util.Date(timeMs))
                            : f.getName();

                    long kb = f.getSize() != null ? (f.getSize() / 1024) : 0;
                    String encLabel = f.getName().endsWith(".tsbackup") ? " (Encrypted)" : "";

                    items[i] = displayDate + " • " + kb + " KB" + encLabel;
                }

                final int[] selectedIndex = {0};

                new MaterialAlertDialogBuilder(this)
                        .setTitle("Select Cloud Backup")
                        .setSingleChoiceItems(items, 0, (dialog, which) -> selectedIndex[0] = which)
                        .setPositiveButton("Restore", (dialog, which) -> downloadAndParseDriveBackup(account, finalFiles.get(selectedIndex[0])))
                        .setNegativeButton(R.string.common_cancel, null)
                        .show();
            });
        });
    }

    private void downloadAndParseDriveBackup(@NonNull GoogleSignInAccount account, @NonNull com.google.api.services.drive.model.File driveFile) {
        if (!isBackupTaskRunning.compareAndSet(false, true)) return;
        Toast.makeText(this, "Downloading from Google Drive...", Toast.LENGTH_SHORT).show();

        AppExecutors.getInstance().diskIO().execute(() -> {
            File tempFile = null;
            String error = null;
            try {
                GoogleDriveBackupHelper driveHelper = new GoogleDriveBackupHelper(getApplicationContext(), account);
                tempFile = new File(getApplicationContext().getCacheDir(), "drive_restore_tmp_" + System.currentTimeMillis());
                driveHelper.downloadBackup(driveFile.getId(), tempFile);
            } catch (Exception e) {
                error = e.getMessage();
                if (tempFile != null && tempFile.exists()) tempFile.delete();
                tempFile = null;
            } finally {
                isBackupTaskRunning.set(false);
            }

            final File finalTempFile = tempFile;
            final String finalError = error;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalError != null || finalTempFile == null) {
                    Toast.makeText(this, "Failed to download backup: " + finalError, Toast.LENGTH_LONG).show();
                    return;
                }
                // Route directly into the existing parse flow with the temp file.
                beginParseBackup(null, finalTempFile, null);
            });
        });
    }

    // --- Original Local Storage Helper Routines ---
    private void loadProfileImageIntoView(@Nullable String path, @NonNull ImageView target) {
        if (!ImageUtils.loadProfileImageIntoView(path, target)) target.setImageResource(R.drawable.ic_tutor_logo);
    }

    private void exportBackupToUri(@NonNull Uri uri) {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty() || !isBackupTaskRunning.compareAndSet(false, true)) return;
        final Context appContext = getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            String errorMsg = null;
            boolean exported = false;
            try (OutputStream out = appContext.getContentResolver().openOutputStream(uri)) {
                if (out != null) { BackupRestoreManager.exportAccountBackupToStream(appContext, db, accountId, out); exported = true; }
                else errorMsg = "Unable to open destination file.";
            } catch (Exception e) { errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            } finally { DatabaseManager.getInstance().closeDatabase(); isBackupTaskRunning.set(false); }

            final boolean success = exported;
            final String finalError = errorMsg;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (success) Toast.makeText(MainActivity.this, "Backup exported successfully!", Toast.LENGTH_SHORT).show();
                else if (finalError != null) Toast.makeText(MainActivity.this, "Export failed: " + finalError, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void exportEncryptedBackupToUri(@NonNull Uri uri, @NonNull char[] password) {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty() || !isBackupTaskRunning.compareAndSet(false, true)) { Arrays.fill(password, '\0'); return; }
        final Context appContext = getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            String errorMsg = null;
            boolean exported = false;
            try (OutputStream out = appContext.getContentResolver().openOutputStream(uri)) {
                if (out != null) { BackupRestoreManager.exportEncryptedBackupToStream(appContext, db, accountId, out, password); exported = true; }
                else errorMsg = "Unable to open destination file.";
            } catch (Exception e) { errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            } finally { Arrays.fill(password, '\0'); DatabaseManager.getInstance().closeDatabase(); isBackupTaskRunning.set(false); }

            final boolean success = exported;
            final String finalError = errorMsg;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (success) Toast.makeText(MainActivity.this, R.string.backup_export_encrypted_ok, Toast.LENGTH_LONG).show();
                else if (finalError != null) Toast.makeText(MainActivity.this, "Export failed: " + finalError, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void startRestoreFlow() {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;
        final List<File> safetyBackups = BackupRestoreManager.listSafetyBackups(getApplicationContext(), accountId);
        if (safetyBackups.isEmpty()) { importBackupLauncher.launch(new String[]{"*/*"}); return; }
        BackupDialogs.chooseRestoreSource(this, safetyBackups.get(0).lastModified(),
                () -> importBackupLauncher.launch(new String[]{"*/*"}), () -> beginParseBackup(null, safetyBackups.get(0), null));
    }

    private void importBackupFromUri(@NonNull Uri uri) {
        beginParseBackup(uri, null, null);
    }

    private void beginParseBackup(@Nullable Uri uri, @Nullable File file, @Nullable char[] password) {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty() || !isBackupTaskRunning.compareAndSet(false, true)) { if (password != null) Arrays.fill(password, '\0'); return; }

        final Context appContext = getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            BackupRestoreManager.ParsedBackup parsed = null;
            Exception error = null;
            try (InputStream in = file != null ? new FileInputStream(file) : appContext.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Unable to open the backup file.");
                parsed = BackupRestoreManager.parseBackup(in, password);
            } catch (Exception e) { error = e; } finally {
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
                    Toast.makeText(this, getString(R.string.backup_restore_read_failed_fmt, finalError.getMessage() != null ? finalError.getMessage() : "Invalid backup"), Toast.LENGTH_LONG).show();
                } else if (finalParsed != null) {
                    BackupDialogs.confirmRestore(this, finalParsed, accountId, () -> performRestore(finalParsed, accountId));
                }
            });
        });
    }

    private void performRestore(@NonNull BackupRestoreManager.ParsedBackup backup, @NonNull String accountId) {
        if (!isBackupTaskRunning.compareAndSet(false, true)) return;
        final Context appContext = getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            String message; boolean restoreSucceeded = false;
            try {
                boolean safetySaved = true;
                try { BackupRestoreManager.createSafetyBackup(appContext, db, accountId); } catch (Exception e) { safetySaved = false; }

                if (!safetySaved) { message = getString(R.string.backup_safety_failed);
                } else {
                    try {
                        BackupRestoreManager.ImportResult result = BackupRestoreManager.importParsedBackup(appContext, db, accountId, backup);
                        studentRepo.migrateLegacyStudentIds(accountId); invoiceRepo.migrateLegacyInvoiceIds(accountId);
                        lastReconciledAccountId = accountId; restoreSucceeded = true;
                        message = result.hasConflicts() ? getString(R.string.backup_restore_done_conflicts_fmt, result.insertedCount, result.updatedCount, result.skippedCount, result.conflictCount)
                                : getString(R.string.backup_restore_done_fmt, result.insertedCount, result.updatedCount, result.skippedCount);
                    } catch (Exception e) { message = getString(R.string.backup_restore_failed_fmt, e.getMessage() != null ? e.getMessage() : "Invalid backup"); }
                }
            } finally { DatabaseManager.getInstance().closeDatabase(); isBackupTaskRunning.set(false); }

            final String finalMsg = message; final boolean finalSuccess = restoreSucceeded;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(MainActivity.this, finalMsg, Toast.LENGTH_LONG).show();
                if (finalSuccess) { refreshStatusLabels(); loadDashboardData(); }
            });
        });
    }
}
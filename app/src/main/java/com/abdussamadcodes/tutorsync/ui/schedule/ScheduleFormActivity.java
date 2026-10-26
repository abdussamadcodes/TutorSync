package com.abdussamadcodes.tutorsync.ui.schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.databinding.ActivityScheduleFormBinding;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.FeeAgreement;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.EnrollmentRepository;
import com.abdussamadcodes.tutorsync.repository.local.FeeAgreementRepository;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ScheduleFormActivity extends BaseActivity {

    private ActivityScheduleFormBinding binding;
    private SessionManager sessionManager;

    private List<Student> allStudents = new ArrayList<>();
    private final Set<String> selectedStudentIds = new HashSet<>();
    private List<Subject> availableSubjects = new ArrayList<>();
    private final Set<String> selectedSubjectIds = new HashSet<>();

    private int selectedStartMinute = 960;
    private long selectedDateMillis = -1;

    private boolean isEditMode = false;
    private String editScheduleGroupId;
    private String editStudentId;
    private ArrayList<String> editSubjectIds;
    private int editStartTime;
    private int editDuration;
    private String editBillingModel = RecurringSchedule.MODEL_MONTHLY;
    private int editFeeMinor = 0;
    private int editAnchorDay = 1;
    private ArrayList<Integer> editDays;
    private ArrayList<String> editScheduleIds;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScheduleFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.scheduleFormRoot);

        sessionManager = new SessionManager(this);
        binding.toggleGroupBillingModel.check(R.id.btnBillingMonthly);

        if (getIntent().hasExtra("edit_student_id")) {
            isEditMode = true;
            editScheduleGroupId = getIntent().getStringExtra("edit_schedule_group_id");
            editStudentId = getIntent().getStringExtra("edit_student_id");
            editSubjectIds = getIntent().getStringArrayListExtra("edit_subject_ids");
            editStartTime = getIntent().getIntExtra("edit_start_time", 960);
            editDuration = getIntent().getIntExtra("edit_duration", 60);
            editBillingModel = getIntent().getStringExtra("edit_billing_model");
            editFeeMinor = getIntent().getIntExtra("edit_fee_minor", 0);
            editAnchorDay = getIntent().getIntExtra("edit_anchor_day", 1);
            editDays = getIntent().getIntegerArrayListExtra("edit_days");
            editScheduleIds = getIntent().getStringArrayListExtra("edit_schedule_ids");

            binding.toolbar.setTitle("Edit Schedule Group");
            binding.switchRecurring.setChecked(true);
            binding.switchRecurring.setEnabled(false);

            if (RecurringSchedule.MODEL_PER_CLASS.equals(editBillingModel)) {
                binding.toggleGroupBillingModel.check(R.id.btnBillingPerClass);
            } else {
                binding.toggleGroupBillingModel.check(R.id.btnBillingMonthly);
            }
            if (editFeeMinor > 0) {
                binding.etScheduleFee.setText(String.format(Locale.US, "%.0f", editFeeMinor / 100.0));
            }
        }

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setupToggles();
        setupPickers();
        loadInitialData();

        binding.etStudentSelection.setOnClickListener(v -> showPremiumStudentPicker());
        binding.etSubjectSelection.setOnClickListener(v -> showPremiumSubjectPicker());
        binding.btnSaveSchedule.setOnClickListener(v -> saveSchedule());
    }

    private void setupToggles() {
        binding.switchRecurring.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.layoutRecurringOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            binding.layoutOneOffOptions.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });
    }

    private void setupPickers() {
        binding.etStartTime.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                selectedStartMinute = (hourOfDay * 60) + minute;
                int displayHour = hourOfDay % 12;
                if (displayHour == 0) displayHour = 12;
                String ampm = hourOfDay >= 12 ? "PM" : "AM";
                binding.etStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, ampm));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
        });

        binding.etSpecificDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth, 0, 0, 0);
                selected.set(Calendar.MILLISECOND, 0);
                selectedDateMillis = selected.getTimeInMillis();
                binding.etSpecificDate.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void loadInitialData() {
        String accountId = sessionManager.getActiveAccountId();
        StudentRepository studentRepo = new StudentRepository();
        studentRepo.migrateLegacyStudentIds(accountId);
        allStudents = studentRepo.getActiveStudentsForAccount(accountId);

        if (isEditMode) {
            if (editStudentId != null) {
                selectedStudentIds.add(editStudentId);
            }
            filterSubjectsBySelectedStudents(true);
            if (editSubjectIds != null) {
                selectedSubjectIds.addAll(editSubjectIds);
            }

            // If schedule fee was 0 on legacy schedule, prefill from student's FeeAgreement if present
            if (editFeeMinor <= 0 && editStudentId != null) {
                FeeAgreement fa = new FeeAgreementRepository().getAgreementForStudent(editStudentId);
                if (fa != null && fa.getAmountMinorUnits() > 0) {
                    editFeeMinor = fa.getAmountMinorUnits();
                    editBillingModel = fa.getFeeType();
                    binding.etScheduleFee.setText(String.format(Locale.US, "%.0f", editFeeMinor / 100.0));
                    if (RecurringSchedule.MODEL_PER_CLASS.equals(editBillingModel)) {
                        binding.toggleGroupBillingModel.check(R.id.btnBillingPerClass);
                    } else {
                        binding.toggleGroupBillingModel.check(R.id.btnBillingMonthly);
                    }
                }
            }

            updateStudentSelectionUI();
            updateSubjectSelectionUI();

            selectedStartMinute = editStartTime;
            int h = selectedStartMinute / 60;
            int m = selectedStartMinute % 60;
            int dh = h % 12; if (dh == 0) dh = 12;
            binding.etStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d %s", dh, m, h >= 12 ? "PM" : "AM"));
            binding.etDuration.setText(String.valueOf(editDuration));

            if (editDays != null) {
                binding.chipSun.setChecked(editDays.contains(1));
                binding.chipMon.setChecked(editDays.contains(2));
                binding.chipTue.setChecked(editDays.contains(3));
                binding.chipWed.setChecked(editDays.contains(4));
                binding.chipThu.setChecked(editDays.contains(5));
                binding.chipFri.setChecked(editDays.contains(6));
                binding.chipSat.setChecked(editDays.contains(7));
            }
        } else {
            filterSubjectsBySelectedStudents(false);
        }
    }

    private void showPremiumStudentPicker() {
        if (allStudents == null || allStudents.isEmpty()) {
            showMessage(binding.getRoot(), "Please add students in the directory first.");
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = createBottomSheetContainer(
                getString(R.string.schedule_select_student),
                isEditMode ? "Tap a student for this schedule group" : "Select one or more students for this schedule"
        );

        ScrollView scroll = new ScrollView(this);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollLp);

        for (Student s : allStudents) {
            String subtitle = (s.getGrade() != null && !s.getGrade().isEmpty() ? s.getGrade() : "Student")
                    + (s.getSchool() != null && !s.getSchool().isEmpty() ? " • " + s.getSchool() : "");
            boolean isChecked = selectedStudentIds.contains(s.getId());

            MaterialCardView card = createSelectableItemCard(s.getFormattedDisplayName(), subtitle, isChecked);
            card.setOnClickListener(v -> {
                if (isEditMode) {
                    selectedStudentIds.clear();
                    selectedStudentIds.add(s.getId());
                    updateStudentSelectionUI();
                    filterSubjectsBySelectedStudents(false);
                    prefillFeeFromStudentAgreementIfEmpty(s.getId());
                    sheet.dismiss();
                } else {
                    boolean nowSelected = !selectedStudentIds.contains(s.getId());
                    if (nowSelected) selectedStudentIds.add(s.getId());
                    else selectedStudentIds.remove(s.getId());
                    styleSelectableCard(card, nowSelected);
                }
            });
            listContainer.addView(card);
        }

        if (!isEditMode) {
            MaterialButton btnDone = new MaterialButton(this);
            btnDone.setText("Confirm Selection");
            btnDone.setMinHeight(dp(54));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.topMargin = dp(12);
            btnDone.setLayoutParams(btnLp);
            btnDone.setOnClickListener(v -> {
                updateStudentSelectionUI();
                filterSubjectsBySelectedStudents(false);
                if (selectedStudentIds.size() == 1) {
                    prefillFeeFromStudentAgreementIfEmpty(selectedStudentIds.iterator().next());
                }
                sheet.dismiss();
            });
            root.addView(btnDone);
        }

        sheet.setContentView(root);
        sheet.show();
    }

    private void prefillFeeFromStudentAgreementIfEmpty(String studentId) {
        String currentFeeText = binding.etScheduleFee.getText() != null ? binding.etScheduleFee.getText().toString().trim() : "";
        if (currentFeeText.isEmpty()) {
            FeeAgreement fa = new FeeAgreementRepository().getAgreementForStudent(studentId);
            if (fa != null && fa.getAmountMinorUnits() > 0) {
                binding.etScheduleFee.setText(String.format(Locale.US, "%.0f", fa.getAmountMinorUnits() / 100.0));
                if (FeeAgreement.TYPE_PER_CLASS.equals(fa.getFeeType())) {
                    binding.toggleGroupBillingModel.check(R.id.btnBillingPerClass);
                } else {
                    binding.toggleGroupBillingModel.check(R.id.btnBillingMonthly);
                }
            }
        }
    }

    private void showPremiumSubjectPicker() {
        if (availableSubjects == null || availableSubjects.isEmpty()) {
            showMessage(binding.getRoot(), "No enrolled subjects found for the selected student(s).");
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = createBottomSheetContainer(
                getString(R.string.schedule_select_subject),
                "Tap subjects covered in this schedule"
        );

        ScrollView scroll = new ScrollView(this);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollLp);

        for (Subject sub : availableSubjects) {
            String level = (sub.getLevel() != null && !sub.getLevel().isEmpty()) ? sub.getLevel() : getString(R.string.subject_level_general);
            boolean isChecked = selectedSubjectIds.contains(sub.getId());

            MaterialCardView card = createSelectableItemCard(sub.getTitle(), level, isChecked);
            card.setOnClickListener(v -> {
                boolean nowSelected = !selectedSubjectIds.contains(sub.getId());
                if (nowSelected) selectedSubjectIds.add(sub.getId());
                else selectedSubjectIds.remove(sub.getId());
                styleSelectableCard(card, nowSelected);
            });
            listContainer.addView(card);
        }

        MaterialButton btnDone = new MaterialButton(this);
        btnDone.setText("Confirm Subjects");
        btnDone.setMinHeight(dp(54));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(12);
        btnDone.setLayoutParams(btnLp);
        btnDone.setOnClickListener(v -> {
            updateSubjectSelectionUI();
            sheet.dismiss();
        });
        root.addView(btnDone);

        sheet.setContentView(root);
        sheet.show();
    }

    private LinearLayout createBottomSheetContainer(String title, String subtitle) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
        root.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(13f);
        tvSub.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(16);
        tvSub.setLayoutParams(subLp);
        root.addView(tvSub);

        return root;
    }

    private MaterialCardView createSelectableItemCard(String title, String subtitle, boolean isSelected) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);
        card.setRadius(dp(12));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
        textCol.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText(subtitle);
        tvSubtitle.setTextSize(13f);
        tvSubtitle.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        textCol.addView(tvSubtitle);

        ImageView ivCheck = new ImageView(this);
        ivCheck.setId(android.R.id.icon);
        ivCheck.setImageResource(R.drawable.ic_check_circle);
        ivCheck.setColorFilter(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        ivCheck.setLayoutParams(iconLp);

        row.addView(textCol);
        row.addView(ivCheck);
        card.addView(row);

        styleSelectableCard(card, isSelected);
        return card;
    }

    private void styleSelectableCard(MaterialCardView card, boolean isSelected) {
        int primary = resolveAttrColor(com.google.android.material.R.attr.colorPrimary);
        int outline = resolveAttrColor(com.google.android.material.R.attr.colorOutlineVariant);
        int surface = resolveAttrColor(com.google.android.material.R.attr.colorSurface);
        int primaryContainer = resolveAttrColor(com.google.android.material.R.attr.colorPrimaryContainer);

        card.setStrokeWidth(isSelected ? dp(2) : dp(1));
        card.setStrokeColor(isSelected ? primary : outline);
        card.setCardBackgroundColor(isSelected ? primaryContainer : surface);

        View check = card.findViewById(android.R.id.icon);
        if (check != null) {
            check.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void updateStudentSelectionUI() {
        List<Student> chosen = getSelectedStudentsList();
        if (chosen.isEmpty()) {
            binding.etStudentSelection.setText("");
        } else if (chosen.size() == 1) {
            binding.etStudentSelection.setText(chosen.get(0).getFormattedDisplayName());
        } else {
            binding.etStudentSelection.setText(chosen.size() + " Students Selected");
        }
    }

    private void filterSubjectsBySelectedStudents(boolean preserveSelection) {
        String accountId = sessionManager.getActiveAccountId();
        List<Student> chosenStudents = getSelectedStudentsList();

        if (chosenStudents.isEmpty()) {
            availableSubjects = new SubjectRepository().getActiveSubjectsForAccount(accountId);
        } else {
            EnrollmentRepository enrollRepo = new EnrollmentRepository();
            List<String> commonSubjectIds = null;
            for (Student s : chosenStudents) {
                List<String> enrolledIds = enrollRepo.getEnrolledSubjectIdsForStudent(s.getId());
                if (commonSubjectIds == null) {
                    commonSubjectIds = new ArrayList<>(enrolledIds);
                } else {
                    commonSubjectIds.retainAll(enrolledIds);
                }
            }
            List<Subject> allActive = new SubjectRepository().getActiveSubjectsForAccount(accountId);
            availableSubjects = new ArrayList<>();
            if (commonSubjectIds != null && !commonSubjectIds.isEmpty()) {
                for (Subject sub : allActive) {
                    if (commonSubjectIds.contains(sub.getId())) availableSubjects.add(sub);
                }
            } else {
                // Fallback to all active subjects if student hasn't enrolled in specific subjects yet
                availableSubjects.addAll(allActive);
            }
        }

        if (!preserveSelection) {
            Set<String> validIds = new HashSet<>();
            for (Subject sub : availableSubjects) validIds.add(sub.getId());
            selectedSubjectIds.retainAll(validIds);
            updateSubjectSelectionUI();
        }
    }

    private void updateSubjectSelectionUI() {
        List<Subject> chosen = getSelectedSubjectsList();
        if (chosen.isEmpty()) {
            binding.etSubjectSelection.setText("");
        } else if (chosen.size() == 1) {
            binding.etSubjectSelection.setText(chosen.get(0).getTitle());
        } else {
            binding.etSubjectSelection.setText(chosen.size() + " Subjects Selected");
        }
    }

    private List<Student> getSelectedStudentsList() {
        List<Student> list = new ArrayList<>();
        for (Student s : allStudents) {
            if (selectedStudentIds.contains(s.getId())) list.add(s);
        }
        return list;
    }

    private List<Subject> getSelectedSubjectsList() {
        List<Subject> list = new ArrayList<>();
        for (Subject sub : availableSubjects) {
            if (selectedSubjectIds.contains(sub.getId())) list.add(sub);
        }
        return list;
    }

    private void saveSchedule() {
        List<Student> chosenStudents = getSelectedStudentsList();
        List<Subject> chosenSubjects = getSelectedSubjectsList();

        if (chosenStudents.isEmpty() || chosenSubjects.isEmpty()) {
            showMessage(binding.getRoot(), "Please select students and subjects");
            return;
        }

        String feeStr = binding.etScheduleFee.getText() != null ? binding.etScheduleFee.getText().toString().trim() : "";
        int feeMinor = 0;
        if (!feeStr.isEmpty()) {
            try {
                feeMinor = (int) Math.round(Double.parseDouble(feeStr) * 100);
            } catch (NumberFormatException e) {
                binding.etScheduleFee.setError("Invalid fee amount");
                return;
            }
        }

        String billingModel = binding.toggleGroupBillingModel.getCheckedButtonId() == R.id.btnBillingPerClass
                ? RecurringSchedule.MODEL_PER_CLASS : RecurringSchedule.MODEL_MONTHLY;

        String accountId = sessionManager.getActiveAccountId();
        String durationStr = binding.etDuration.getText() != null ? binding.etDuration.getText().toString() : "";
        int duration = Integer.parseInt(durationStr.isEmpty() ? "60" : durationStr);

        boolean isRecurring = binding.switchRecurring.isChecked();
        ScheduleRepository scheduleRepo = new ScheduleRepository();
        OccurrenceRepository occurrenceRepo = new OccurrenceRepository();
        FeeAgreementRepository feeRepo = new FeeAgreementRepository();

        if (isEditMode && editScheduleIds != null) {
            for (String oldId : editScheduleIds) {
                scheduleRepo.deleteScheduleSafely(oldId);
                occurrenceRepo.deleteFutureOccurrencesByScheduleId(oldId);
            }
        }

        boolean success = true;
        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);
        long startOfTodayMillis = startOfToday.getTimeInMillis();
        int defaultAnchorDay = isEditMode ? editAnchorDay : startOfToday.get(Calendar.DAY_OF_MONTH);

        for (Student student : chosenStudents) {
            String groupId = (isEditMode && editScheduleGroupId != null && !editScheduleGroupId.isEmpty())
                    ? editScheduleGroupId : UUID.randomUUID().toString();

            if (feeMinor > 0) {
                scheduleRepo.updateScheduleGroupFee(accountId, student.getId(), groupId, billingModel, feeMinor, defaultAnchorDay);
                // Keep legacy FeeAgreement synced so any fallback or legacy view sees the latest model & fee
                feeRepo.saveAgreement(new FeeAgreement(
                        UUID.randomUUID().toString(), accountId, student.getId(),
                        billingModel, feeMinor, System.currentTimeMillis()));
            }

            for (Subject subject : chosenSubjects) {
                if (isRecurring) {
                    List<Integer> selectedDays = new ArrayList<>();
                    if (binding.chipSun.isChecked()) selectedDays.add(1);
                    if (binding.chipMon.isChecked()) selectedDays.add(2);
                    if (binding.chipTue.isChecked()) selectedDays.add(3);
                    if (binding.chipWed.isChecked()) selectedDays.add(4);
                    if (binding.chipThu.isChecked()) selectedDays.add(5);
                    if (binding.chipFri.isChecked()) selectedDays.add(6);
                    if (binding.chipSat.isChecked()) selectedDays.add(7);

                    if (selectedDays.isEmpty()) {
                        showMessage(binding.getRoot(), getString(R.string.schedule_err_select_day));
                        return;
                    }

                    for (int dayOfWeek : selectedDays) {
                        String scheduleId = UUID.randomUUID().toString();
                        RecurringSchedule schedule = new RecurringSchedule(
                                scheduleId, accountId, student.getId(), subject.getId(),
                                groupId, dayOfWeek, selectedStartMinute, duration,
                                startOfTodayMillis, startOfTodayMillis + 31536000000L,
                                billingModel, feeMinor, defaultAnchorDay);

                        if (scheduleRepo.insertScheduleWithFee(schedule, groupId, billingModel, feeMinor, defaultAnchorDay)) {
                            generateOccurrences(schedule, groupId, occurrenceRepo);
                        } else {
                            success = false;
                        }
                    }
                } else {
                    if (selectedDateMillis == -1) {
                        showMessage(binding.getRoot(), getString(R.string.schedule_err_date));
                        return;
                    }
                    ClassOccurrence occurrence = new ClassOccurrence(UUID.randomUUID().toString(), accountId,
                            student.getId(), subject.getId(), null, selectedDateMillis, selectedStartMinute, duration);
                    if (!occurrenceRepo.insertOccurrenceWithGroup(occurrence, groupId)) {
                        success = false;
                    }
                }
            }

            // Link any orphaned occurrences for this student & time to this groupId
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try {
                ContentValues upOcc = new ContentValues();
                upOcc.put(OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID, groupId);
                db.update(OccurrenceEntry.TABLE_NAME, upOcc,
                        OccurrenceEntry.COLUMN_STUDENT_ID + " = ? AND " +
                                OccurrenceEntry.COLUMN_START_TIME_MINUTES + " = ? AND " +
                                OccurrenceEntry.COLUMN_SCHEDULE_GROUP_ID + " IS NULL",
                        new String[]{student.getId(), String.valueOf(selectedStartMinute)});
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        }

        if (success) {
            showMessage(binding.getRoot(), getString(R.string.schedule_success));
            finish();
        } else {
            showMessage(binding.getRoot(), getString(R.string.schedule_failed));
        }
    }

    private void generateOccurrences(RecurringSchedule schedule, String groupId, OccurrenceRepository occurrenceRepo) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < 30; i++) {
            if (cal.get(Calendar.DAY_OF_WEEK) == schedule.getDayOfWeek()) {
                ClassOccurrence occ = new ClassOccurrence(
                        UUID.randomUUID().toString(), schedule.getAccountId(), schedule.getStudentId(),
                        schedule.getSubjectId(), schedule.getId(), cal.getTimeInMillis(),
                        schedule.getStartTimeMinutes(), schedule.getDurationMinutes());
                occurrenceRepo.insertOccurrenceWithGroup(occ, groupId);
            }
            cal.add(Calendar.DATE, 1);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int resolveAttrColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
}
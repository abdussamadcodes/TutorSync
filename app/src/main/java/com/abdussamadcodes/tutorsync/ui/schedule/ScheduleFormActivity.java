package com.abdussamadcodes.tutorsync.ui.schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityScheduleFormBinding;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.EnrollmentRepository;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ScheduleFormActivity extends BaseActivity {

    private ActivityScheduleFormBinding binding;
    private SessionManager sessionManager;

    private List<Student> allStudents;
    private List<Student> selectedStudents = new ArrayList<>();

    private List<Subject> availableSubjects = new ArrayList<>();
    private List<Subject> selectedSubjects = new ArrayList<>();

    private int selectedStartMinute = 960; // 16:00 (4:00 PM default)
    private long selectedDateMillis = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScheduleFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.scheduleFormRoot);

        sessionManager = new SessionManager(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupToggles();
        setupPickers();
        loadInitialData();

        binding.etStudentSelection.setOnClickListener(v -> showStudentMultiSelectDialog());
        binding.etSubjectSelection.setOnClickListener(v -> showSubjectMultiSelectDialog());
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
                selectedDateMillis = selected.getTimeInMillis();
                binding.etSpecificDate.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void loadInitialData() {
        String accountId = sessionManager.getActiveAccountId();
        allStudents = new StudentRepository().getActiveStudentsForAccount(accountId);
        filterSubjectsBySelectedStudents();
    }

    private void showStudentMultiSelectDialog() {
        if (allStudents == null || allStudents.isEmpty()) {
            showMessage(binding.getRoot(), "Please add students in the directory first.");
            return;
        }

        String[] studentNames = new String[allStudents.size()];
        boolean[] checkedItems = new boolean[allStudents.size()];

        for (int i = 0; i < allStudents.size(); i++) {
            studentNames[i] = allStudents.get(i).getName();
            checkedItems[i] = selectedStudents.contains(allStudents.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.schedule_select_student)
                .setMultiChoiceItems(studentNames, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        if (!selectedStudents.contains(allStudents.get(which))) {
                            selectedStudents.add(allStudents.get(which));
                        }
                    } else {
                        selectedStudents.remove(allStudents.get(which));
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    updateStudentSelectionUI();
                    filterSubjectsBySelectedStudents();
                })
                .show();
    }

    private void updateStudentSelectionUI() {
        if (selectedStudents.isEmpty()) {
            binding.etStudentSelection.setText("");
        } else if (selectedStudents.size() == 1) {
            binding.etStudentSelection.setText(selectedStudents.get(0).getName());
        } else {
            binding.etStudentSelection.setText(selectedStudents.size() + " Students Selected");
        }
    }

    private void filterSubjectsBySelectedStudents() {
        String accountId = sessionManager.getActiveAccountId();

        if (selectedStudents.isEmpty()) {
            availableSubjects = new SubjectRepository().getActiveSubjectsForAccount(accountId);
        } else {
            EnrollmentRepository enrollRepo = new EnrollmentRepository();
            List<String> commonSubjectIds = null;

            for (Student s : selectedStudents) {
                List<String> enrolledIds = enrollRepo.getEnrolledSubjectIdsForStudent(s.getId());
                if (commonSubjectIds == null) {
                    commonSubjectIds = new ArrayList<>(enrolledIds);
                } else {
                    commonSubjectIds.retainAll(enrolledIds);
                }
            }

            List<Subject> allActive = new SubjectRepository().getActiveSubjectsForAccount(accountId);
            availableSubjects.clear();
            if (commonSubjectIds != null) {
                for (Subject sub : allActive) {
                    if (commonSubjectIds.contains(sub.getId())) {
                        availableSubjects.add(sub);
                    }
                }
            }
        }

        selectedSubjects.clear();
        updateSubjectSelectionUI();
    }

    private void showSubjectMultiSelectDialog() {
        if (availableSubjects == null || availableSubjects.isEmpty()) {
            showMessage(binding.getRoot(), "No subjects available for the selected students.");
            return;
        }

        String[] subjectTitles = new String[availableSubjects.size()];
        boolean[] checkedItems = new boolean[availableSubjects.size()];

        for (int i = 0; i < availableSubjects.size(); i++) {
            subjectTitles[i] = availableSubjects.get(i).getTitle();
            checkedItems[i] = selectedSubjects.contains(availableSubjects.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.schedule_select_subject)
                .setMultiChoiceItems(subjectTitles, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        if (!selectedSubjects.contains(availableSubjects.get(which))) {
                            selectedSubjects.add(availableSubjects.get(which));
                        }
                    } else {
                        selectedSubjects.remove(availableSubjects.get(which));
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    updateSubjectSelectionUI();
                })
                .show();
    }

    private void updateSubjectSelectionUI() {
        if (selectedSubjects.isEmpty()) {
            binding.etSubjectSelection.setText("");
        } else if (selectedSubjects.size() == 1) {
            binding.etSubjectSelection.setText(selectedSubjects.get(0).getTitle());
        } else {
            binding.etSubjectSelection.setText(selectedSubjects.size() + " Subjects Selected");
        }
    }

    private void saveSchedule() {
        if (selectedStudents.isEmpty()) {
            showMessage(binding.getRoot(), getString(R.string.schedule_err_select_student));
            return;
        }
        if (selectedSubjects.isEmpty()) {
            showMessage(binding.getRoot(), getString(R.string.schedule_err_select_subject));
            return;
        }

        String accountId = sessionManager.getActiveAccountId();
        String durationStr = binding.etDuration.getText() != null ? binding.etDuration.getText().toString() : "";
        int duration = Integer.parseInt(durationStr.isEmpty() ? "60" : durationStr);

        boolean isRecurring = binding.switchRecurring.isChecked();
        boolean success = true;

        ScheduleRepository scheduleRepo = new ScheduleRepository();
        OccurrenceRepository occurrenceRepo = new OccurrenceRepository();

        for (Student student : selectedStudents) {
            for (Subject subject : selectedSubjects) {
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
                                scheduleId, accountId, student.getId(),
                                subject.getId(), dayOfWeek, selectedStartMinute, duration,
                                System.currentTimeMillis(), System.currentTimeMillis() + 31536000000L);

                        if (scheduleRepo.insertSchedule(schedule)) {
                            Calendar cal = Calendar.getInstance();
                            cal.set(Calendar.HOUR_OF_DAY, 0);
                            cal.set(Calendar.MINUTE, 0);
                            cal.set(Calendar.SECOND, 0);
                            cal.set(Calendar.MILLISECOND, 0);

                            for (int i = 0; i < 30; i++) {
                                if (cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek) {
                                    ClassOccurrence occ = new ClassOccurrence(
                                            UUID.randomUUID().toString(), accountId, student.getId(),
                                            scheduleId, cal.getTimeInMillis(), selectedStartMinute, duration);
                                    occurrenceRepo.insertOccurrence(occ);
                                }
                                cal.add(Calendar.DATE, 1);
                            }
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
                            student.getId(), null, selectedDateMillis, selectedStartMinute, duration);
                    if (!occurrenceRepo.insertOccurrence(occurrence)) {
                        success = false;
                    }
                }
            }
        }

        if (success) {
            showMessage(binding.getRoot(), getString(R.string.schedule_success));
            finish();
        } else {
            showMessage(binding.getRoot(), getString(R.string.schedule_failed));
        }
    }
}
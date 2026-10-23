package com.abdussamadcodes.tutorsync.ui.schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityScheduleFormBinding;
import com.abdussamadcodes.tutorsync.model.ClassOccurrence;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ScheduleFormActivity extends BaseActivity {

    private ActivityScheduleFormBinding binding;
    private SessionManager sessionManager;

    private List<Student> students;
    private List<Subject> subjects;

    private int selectedStartMinute = -1;
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
        loadSpinners();

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
                binding.etStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
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

    private void loadSpinners() {
        String accountId = sessionManager.getActiveAccountId();

        students = new StudentRepository().getActiveStudentsForAccount(accountId);
        String[] studentNames = new String[students.size()];
        for (int i = 0; i < students.size(); i++) studentNames[i] = students.get(i).getName();
        binding.spinnerStudent.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, studentNames));

        subjects = new SubjectRepository().getActiveSubjectsForAccount(accountId);
        String[] subjectTitles = new String[subjects.size()];
        for (int i = 0; i < subjects.size(); i++) subjectTitles[i] = subjects.get(i).getTitle();
        binding.spinnerSubject.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subjectTitles));
    }

    private void saveSchedule() {
        if (students.isEmpty() || binding.spinnerStudent.getSelectedItemPosition() < 0) {
            showMessage(binding.getRoot(), getString(R.string.schedule_err_select_student));
            return;
        }
        if (subjects.isEmpty() || binding.spinnerSubject.getSelectedItemPosition() < 0) {
            showMessage(binding.getRoot(), getString(R.string.schedule_err_select_subject));
            return;
        }
        if (selectedStartMinute == -1) {
            showMessage(binding.getRoot(), getString(R.string.schedule_err_time));
            return;
        }

        String accountId = sessionManager.getActiveAccountId();
        Student selectedStudent = students.get(binding.spinnerStudent.getSelectedItemPosition());
        Subject selectedSubject = subjects.get(binding.spinnerSubject.getSelectedItemPosition());
        int duration = Integer.parseInt(binding.etDuration.getText().toString().isEmpty() ? "60" : binding.etDuration.getText().toString());

        boolean isRecurring = binding.switchRecurring.isChecked();
        boolean success;

        if (isRecurring) {
            int dayOfWeek = binding.spinnerDayOfWeek.getSelectedItemPosition() + 1; // 1=Sunday, 7=Saturday
            RecurringSchedule schedule = new RecurringSchedule(UUID.randomUUID().toString(), accountId,
                    selectedStudent.getId(), selectedSubject.getId(), dayOfWeek, selectedStartMinute, duration,
                    System.currentTimeMillis(), System.currentTimeMillis() + 31536000000L); // 1 year end date
            success = new ScheduleRepository().insertSchedule(schedule);
        } else {
            if (selectedDateMillis == -1) {
                showMessage(binding.getRoot(), getString(R.string.schedule_err_date));
                return;
            }
            ClassOccurrence occurrence = new ClassOccurrence(UUID.randomUUID().toString(), accountId,
                    selectedStudent.getId(), null, selectedDateMillis, selectedStartMinute, duration);
            success = new OccurrenceRepository().insertOccurrence(occurrence);
        }

        if (success) {
            showMessage(binding.getRoot(), getString(R.string.schedule_success));
            finish();
        } else {
            showMessage(binding.getRoot(), getString(R.string.schedule_failed));
        }
    }
}
package com.abdussamadcodes.tutorsync.ui.schedule;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.abdussamadcodes.tutorsync.databinding.ActivityScheduleListBinding;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.List;

public class ScheduleListActivity extends BaseActivity {

    private ActivityScheduleListBinding binding;
    private ScheduleAdapter adapter;
    private ScheduleRepository scheduleRepo;
    private StudentRepository studentRepo;
    private SubjectRepository subjectRepo;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScheduleListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.scheduleListRoot);

        sessionManager = new SessionManager(this);
        scheduleRepo = new ScheduleRepository();
        studentRepo = new StudentRepository();
        subjectRepo = new SubjectRepository();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ScheduleAdapter(this::showDeleteDialog);
        binding.rvSchedules.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSchedules.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        List<RecurringSchedule> schedules = scheduleRepo.getSchedulesForAccount(accountId);
        List<Student> students = studentRepo.getActiveStudentsForAccount(accountId);
        List<Subject> subjects = subjectRepo.getActiveSubjectsForAccount(accountId);

        adapter.setData(schedules, students, subjects);

        if (schedules.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvSchedules.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvSchedules.setVisibility(View.VISIBLE);
        }
    }

    private void showDeleteDialog(RecurringSchedule schedule) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Schedule?")
                .setMessage("Are you sure you want to delete this recurring schedule? This will also remove all upcoming un-recorded classes for this rule from your dashboard.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (scheduleRepo.deleteSchedule(schedule.getId())) {
                        showMessage(binding.getRoot(), "Schedule deleted");
                        loadData(); // Refresh the list
                    } else {
                        showMessage(binding.getRoot(), "Failed to delete schedule");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
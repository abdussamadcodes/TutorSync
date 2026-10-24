package com.abdussamadcodes.tutorsync.ui.schedule;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.abdussamadcodes.tutorsync.databinding.ActivityScheduleListBinding;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.ScheduleGroup;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.model.Subject;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.SubjectRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import java.util.ArrayList;
import java.util.Collections;
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

        adapter = new ScheduleAdapter(this::showOptionsDialog);
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

        List<ScheduleGroup> groups = new ArrayList<>();
        for (RecurringSchedule rs : schedules) {
            boolean found = false;
            for (ScheduleGroup g : groups) {
                if (g.getStudentId().equals(rs.getStudentId()) &&
                        g.getStartTimeMinutes() == rs.getStartTimeMinutes() &&
                        g.getDurationMinutes() == rs.getDurationMinutes()) {

                    if (!g.getDaysOfWeek().contains(rs.getDayOfWeek())) g.getDaysOfWeek().add(rs.getDayOfWeek());
                    if (!g.getSubjectIds().contains(rs.getSubjectId())) g.getSubjectIds().add(rs.getSubjectId());
                    if (!g.getScheduleIds().contains(rs.getId())) g.getScheduleIds().add(rs.getId());

                    Collections.sort(g.getDaysOfWeek());
                    found = true;
                    break;
                }
            }
            if (!found) {
                ScheduleGroup newGroup = new ScheduleGroup(rs.getStudentId(), rs.getStartTimeMinutes(), rs.getDurationMinutes());
                newGroup.getDaysOfWeek().add(rs.getDayOfWeek());
                newGroup.getSubjectIds().add(rs.getSubjectId());
                newGroup.getScheduleIds().add(rs.getId());
                groups.add(newGroup);
            }
        }

        adapter.setData(groups, students, subjects);

        if (groups.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvSchedules.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvSchedules.setVisibility(View.VISIBLE);
        }
    }

    private void showOptionsDialog(ScheduleGroup group) {
        String[] options = {"Edit Schedule Group", "Delete Entire Group"};
        new AlertDialog.Builder(this)
                .setTitle("Manage Schedule")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, ScheduleFormActivity.class);
                        intent.putExtra("edit_student_id", group.getStudentId());
                        intent.putStringArrayListExtra("edit_subject_ids", new ArrayList<>(group.getSubjectIds()));
                        intent.putExtra("edit_start_time", group.getStartTimeMinutes());
                        intent.putExtra("edit_duration", group.getDurationMinutes());
                        intent.putIntegerArrayListExtra("edit_days", new ArrayList<>(group.getDaysOfWeek()));
                        intent.putStringArrayListExtra("edit_schedule_ids", new ArrayList<>(group.getScheduleIds()));
                        startActivity(intent);
                    } else {
                        showDeleteDialog(group);
                    }
                }).show();
    }

    private void showDeleteDialog(ScheduleGroup group) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Group?")
                .setMessage("Are you sure you want to delete all days in this recurring schedule?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (String id : group.getScheduleIds()) {
                        scheduleRepo.deleteScheduleSafely(id);
                    }
                    showMessage(binding.getRoot(), "Schedule group deleted");
                    loadData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
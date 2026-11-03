package com.abdussamadcodes.tutorsync.ui.attendance;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ActivityAttendanceHistoryBinding;
import com.abdussamadcodes.tutorsync.model.AttendanceHistoryItem;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.util.AppExecutors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * H2: shows recent classes (recorded and unrecorded) so missed attendance can be recorded and
 * previously recorded attendance can be corrected safely.
 */
public class AttendanceHistoryActivity extends BaseActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_NEEDS = 1;
    private static final int FILTER_RECORDED = 2;

    private ActivityAttendanceHistoryBinding binding;
    private AttendanceHistoryAdapter adapter;
    private AttendanceRepository attendanceRepo;
    private SessionManager sessionManager;

    private List<AttendanceHistoryItem> allItems = new ArrayList<>();
    private int statusFilter = FILTER_ALL;
    private int rangeDays = 30;
    private int loadToken = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAttendanceHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.attendanceHistoryRoot);

        sessionManager = new SessionManager(this);
        attendanceRepo = new AttendanceRepository();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new AttendanceHistoryAdapter(this::openAttendanceSheet);
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(adapter);

        binding.chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipStatusNeeds) statusFilter = FILTER_NEEDS;
            else if (id == R.id.chipStatusRecorded) statusFilter = FILTER_RECORDED;
            else statusFilter = FILTER_ALL;
            applyFilter();
        });

        binding.chipGroupRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipRange7) rangeDays = 7;
            else if (id == R.id.chipRange90) rangeDays = 90;
            else rangeDays = 30;
            loadHistory();
        });

        // The bottom sheet reports back here after saving.
        getSupportFragmentManager().setFragmentResultListener("attendance_request_key", this,
                (requestKey, result) -> {
                    if (result.getBoolean("attendance_recorded", false)) {
                        loadHistory();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        final String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) {
            finish();
            return;
        }
        final int token = ++loadToken;
        final int days = rangeDays;
        binding.progressHistory.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            List<AttendanceHistoryItem> loaded;
            try {
                loaded = attendanceRepo.getClassHistory(accountId, days);
            } catch (RuntimeException e) {
                loaded = null;
            }
            final List<AttendanceHistoryItem> result = loaded;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed() || binding == null) return;
                if (token != loadToken) return; // a newer load is already running
                binding.progressHistory.setVisibility(View.GONE);
                if (result == null) {
                    Toast.makeText(this, R.string.attendance_history_load_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                allItems = result;
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        int needs = 0;
        int recorded = 0;
        int invoiced = 0;
        for (AttendanceHistoryItem item : allItems) {
            if (item.needsAttendance) needs++;
            else recorded++;
            if (item.billed) invoiced++;
        }
        binding.tvStatNeeds.setText(String.valueOf(needs));
        binding.tvStatRecorded.setText(String.valueOf(recorded));
        binding.tvStatInvoiced.setText(String.valueOf(invoiced));

        List<AttendanceHistoryItem> visible = new ArrayList<>();
        for (AttendanceHistoryItem item : allItems) {
            if (statusFilter == FILTER_NEEDS && !item.needsAttendance) continue;
            if (statusFilter == FILTER_RECORDED && item.needsAttendance) continue;
            visible.add(item);
        }

        List<AttendanceHistoryAdapter.Row> rows = new ArrayList<>();
        int i = 0;
        while (i < visible.size()) {
            long day = visible.get(i).dateMillis;
            int j = i;
            int pending = 0;
            while (j < visible.size() && visible.get(j).dateMillis == day) {
                if (visible.get(j).needsAttendance) pending++;
                j++;
            }
            int count = j - i;
            String meta = pending > 0
                    ? getString(R.string.attendance_header_meta_pending, pending)
                    : getString(R.string.attendance_header_meta_classes, count);
            rows.add(AttendanceHistoryAdapter.Row.header(formatDayLabel(day), meta));
            for (int k = i; k < j; k++) {
                rows.add(AttendanceHistoryAdapter.Row.item(visible.get(k)));
            }
            i = j;
        }
        adapter.submit(rows);

        boolean empty = visible.isEmpty();
        binding.rvHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (statusFilter == FILTER_NEEDS) {
                binding.tvEmptyTitle.setText(R.string.attendance_empty_caught_up_title);
                binding.tvEmptySubtitle.setText(R.string.attendance_empty_caught_up_subtitle);
            } else {
                binding.tvEmptyTitle.setText(R.string.attendance_empty_title);
                binding.tvEmptySubtitle.setText(R.string.attendance_empty_subtitle);
            }
        }
    }

    private void openAttendanceSheet(@NonNull AttendanceHistoryItem item) {
        if (getSupportFragmentManager().findFragmentByTag(AttendanceBottomSheetFragment.TAG) != null) {
            return; // a sheet is already open
        }
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty()) return;

        String label = item.studentName + " · " + formatDayLabel(item.dateMillis)
                + " · " + AttendanceHistoryAdapter.formatTime(item.startTimeMinutes);
        ArrayList<String> ids = new ArrayList<>(item.occurrenceIds);

        AttendanceBottomSheetFragment sheet = item.needsAttendance
                ? AttendanceBottomSheetFragment.newInstance(accountId, item.studentId, ids, label)
                : AttendanceBottomSheetFragment.newEditInstance(accountId, item.studentId, ids, label);
        sheet.show(getSupportFragmentManager(), AttendanceBottomSheetFragment.TAG);
    }

    @NonNull
    private String formatDayLabel(long dayMillis) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long startOfToday = today.getTimeInMillis();
        today.add(Calendar.DATE, -1);
        long startOfYesterday = today.getTimeInMillis();

        // Dates are stored as local midnight; a 12h tolerance survives timezone changes.
        long tolerance = 12L * 60L * 60L * 1000L;
        if (Math.abs(dayMillis - startOfToday) < tolerance) {
            return getString(R.string.attendance_day_today);
        }
        if (Math.abs(dayMillis - startOfYesterday) < tolerance) {
            return getString(R.string.attendance_day_yesterday);
        }
        return new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(new Date(dayMillis));
    }
}

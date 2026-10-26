package com.abdussamadcodes.tutorsync.ui.attendance;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.DialogBottomSheetAttendanceBinding;
import com.abdussamadcodes.tutorsync.model.AttendanceRecord;
import com.abdussamadcodes.tutorsync.model.Lesson;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository;
import com.abdussamadcodes.tutorsync.repository.local.LessonRepository;
import com.abdussamadcodes.tutorsync.repository.local.OccurrenceRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class AttendanceBottomSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AttendanceBottomSheet";
    private static final String ARG_ACCOUNT_ID = "arg_account_id";
    private static final String ARG_STUDENT_ID = "arg_student_id";
    private static final String ARG_OCCURRENCE_IDS = "arg_occurrence_ids";

    private DialogBottomSheetAttendanceBinding binding;
    private String accountId;
    private String studentId;
    private ArrayList<String> occurrenceIds;

    private AttendanceRepository attendanceRepo;
    private LessonRepository lessonRepo;
    private OccurrenceRepository occurrenceRepo;

    private long makeupDateMillis = -1L;
    private int makeupStartMinutes = 960;

    public static AttendanceBottomSheetFragment newInstance(String accountId, String studentId, ArrayList<String> occurrenceIds) {
        AttendanceBottomSheetFragment fragment = new AttendanceBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT_ID, accountId);
        args.putString(ARG_STUDENT_ID, studentId);
        args.putStringArrayList(ARG_OCCURRENCE_IDS, occurrenceIds);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            accountId = getArguments().getString(ARG_ACCOUNT_ID);
            studentId = getArguments().getString(ARG_STUDENT_ID);
            occurrenceIds = getArguments().getStringArrayList(ARG_OCCURRENCE_IDS);
        }
        attendanceRepo = new AttendanceRepository();
        lessonRepo = new LessonRepository();
        occurrenceRepo = new OccurrenceRepository();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogBottomSheetAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toggleGroupStatus.check(R.id.btnStatusPresent);

        binding.toggleGroupStatus.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnStatusPresent) {
                binding.chipGroupCancelActor.setVisibility(View.GONE);
                binding.layoutBillingAndReschedule.setVisibility(View.GONE);
            } else {
                binding.chipGroupCancelActor.setVisibility(View.VISIBLE);
                binding.layoutBillingAndReschedule.setVisibility(View.VISIBLE);
                if (checkedId == R.id.btnStatusAbsent && binding.chipByStudent.isChecked()) {
                    // Tutor is present, student absent -> default to charge
                    binding.switchChargeClass.setChecked(true);
                } else {
                    binding.switchChargeClass.setChecked(false);
                }
            }
        });

        binding.chipGroupCancelActor.setOnCheckedStateChangeListener((group, checkedIds) -> {
            boolean byTutor = binding.chipByTutor.isChecked();
            if (byTutor) {
                binding.switchChargeClass.setChecked(false);
            } else if (binding.toggleGroupStatus.getCheckedButtonId() == R.id.btnStatusAbsent) {
                binding.switchChargeClass.setChecked(true);
            }
        });

        binding.switchRescheduleMakeup.setOnCheckedChangeListener((btn, isChecked) -> {
            binding.btnPickMakeupDateTime.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                // Prevent duplicate charge on original class when rescheduled
                binding.switchChargeClass.setChecked(false);
            }
        });

        binding.btnPickMakeupDateTime.setOnClickListener(v -> pickMakeupDateTime());
        binding.btnSaveAttendance.setOnClickListener(v -> saveRecord());

        binding.btnRemoveClass.setOnClickListener(v -> {
            for (String occId : occurrenceIds) {
                occurrenceRepo.deleteOccurrence(occId);
            }
            Toast.makeText(requireContext(), "Class removed from dashboard", Toast.LENGTH_SHORT).show();
            notifyDashboardRefresh();
            dismiss();
        });
    }

    private void pickMakeupDateTime() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (dView, year, month, dayOfMonth) -> {
            Calendar chosenDate = Calendar.getInstance();
            chosenDate.set(year, month, dayOfMonth, 0, 0, 0);
            chosenDate.set(Calendar.MILLISECOND, 0);
            makeupDateMillis = chosenDate.getTimeInMillis();

            new TimePickerDialog(requireContext(), (tView, hourOfDay, minute) -> {
                makeupStartMinutes = (hourOfDay * 60) + minute;
                SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                int h = hourOfDay % 12;
                if (h == 0) h = 12;
                String ampm = hourOfDay >= 12 ? "PM" : "AM";
                binding.btnPickMakeupDateTime.setText(String.format(Locale.US, "Makeup: %s at %02d:%02d %s",
                        df.format(new Date(makeupDateMillis)), h, minute, ampm));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveRecord() {
        int checkedId = binding.toggleGroupStatus.getCheckedButtonId();
        if (checkedId == View.NO_ID) {
            Toast.makeText(requireContext(), "Please select an attendance status", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean byTutor = binding.chipByTutor.isChecked();
        boolean scheduleMakeup = binding.switchRescheduleMakeup.isChecked();

        if (scheduleMakeup && makeupDateMillis <= 0) {
            Toast.makeText(requireContext(), "Please select the makeup class date and time", Toast.LENGTH_SHORT).show();
            return;
        }

        String status;
        if (checkedId == R.id.btnStatusPresent) {
            status = AttendanceRecord.STATUS_PRESENT;
        } else if (checkedId == R.id.btnStatusAbsent) {
            status = byTutor ? AttendanceRecord.STATUS_TUTOR_ABSENT : AttendanceRecord.STATUS_STUDENT_ABSENT;
        } else {
            status = byTutor ? AttendanceRecord.STATUS_CANCELLED_BY_TUTOR : AttendanceRecord.STATUS_CANCELLED_BY_STUDENT;
        }

        String chargeDecision;
        if (checkedId == R.id.btnStatusPresent) {
            chargeDecision = AttendanceRecord.DECISION_CHARGE;
        } else if (scheduleMakeup) {
            chargeDecision = AttendanceRecord.DECISION_WAIVE;
        } else {
            chargeDecision = binding.switchChargeClass.isChecked()
                    ? AttendanceRecord.DECISION_CHARGE : AttendanceRecord.DECISION_WAIVE;
        }

        String topic = binding.etTopic.getText() != null ? binding.etTopic.getText().toString().trim() : "";
        String homework = binding.etHomework.getText() != null ? binding.etHomework.getText().toString().trim() : "";
        String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";

        for (String occId : occurrenceIds) {
            AttendanceRecord attendance = new AttendanceRecord(
                    UUID.randomUUID().toString(), accountId, occId, status,
                    chargeDecision, 0, scheduleMakeup, null, null, System.currentTimeMillis()
            );
            attendanceRepo.recordAttendanceWithBilling(attendance, scheduleMakeup, makeupDateMillis, makeupStartMinutes);

            if (!topic.isEmpty() || !homework.isEmpty() || !notes.isEmpty()) {
                Lesson lesson = new Lesson(
                        UUID.randomUUID().toString(), accountId, studentId, occId,
                        topic.isEmpty() ? null : topic,
                        homework.isEmpty() ? null : homework,
                        notes.isEmpty() ? null : notes
                );
                lessonRepo.saveLesson(lesson);
            }
        }

        Toast.makeText(requireContext(), "Attendance & billing decision saved", Toast.LENGTH_SHORT).show();
        notifyDashboardRefresh();
        dismiss();
    }

    private void notifyDashboardRefresh() {
        Bundle resultBundle = new Bundle();
        resultBundle.putBoolean("attendance_recorded", true);
        getParentFragmentManager().setFragmentResult("attendance_request_key", resultBundle);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
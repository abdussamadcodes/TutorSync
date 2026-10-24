package com.abdussamadcodes.tutorsync.ui.attendance;

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

import java.util.ArrayList;
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

    private void saveRecord() {
        int checkedId = binding.toggleGroupStatus.getCheckedButtonId();
        if (checkedId == View.NO_ID) {
            Toast.makeText(requireContext(), "Please select an attendance status", Toast.LENGTH_SHORT).show();
            return;
        }

        String status = "PRESENT";
        if (checkedId == R.id.btnStatusAbsent) {
            status = "ABSENT";
        } else if (checkedId == R.id.btnStatusCancelled) {
            status = "CANCELLED";
        }

        String topic = binding.etTopic.getText() != null ? binding.etTopic.getText().toString().trim() : "";
        String homework = binding.etHomework.getText() != null ? binding.etHomework.getText().toString().trim() : "";
        String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";

        for (String occId : occurrenceIds) {
            AttendanceRecord attendance = new AttendanceRecord(
                    UUID.randomUUID().toString(), accountId, occId, status
            );
            attendanceRepo.recordAttendance(attendance);

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

        Toast.makeText(requireContext(), "Attendance recorded", Toast.LENGTH_SHORT).show();
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
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
import com.google.android.material.color.MaterialColors;
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
    private static final String ARG_EDIT_MODE = "arg_edit_mode";
    private static final String ARG_CONTEXT_LABEL = "arg_context_label";

    private DialogBottomSheetAttendanceBinding binding;
    private String accountId;
    private String studentId;
    private ArrayList<String> occurrenceIds;
    private boolean editMode;
    @Nullable private String contextLabel;

    private AttendanceRepository attendanceRepo;
    private LessonRepository lessonRepo;
    private OccurrenceRepository occurrenceRepo;

    private long makeupDateMillis = -1L;
    private int makeupStartMinutes = 960;

    // Correction-mode state
    @Nullable private String existingMakeupId = null;
    private boolean makeupDirty = false;
    private boolean hadLesson = false;
    private boolean locked = false;

    /** Record attendance for a class that has no attendance yet (dashboard). */
    public static AttendanceBottomSheetFragment newInstance(String accountId, String studentId, ArrayList<String> occurrenceIds) {
        return build(accountId, studentId, occurrenceIds, false, null);
    }

    /** Record attendance and show a context line such as "Rahim · Mon 3 Nov, 4:00 PM". */
    public static AttendanceBottomSheetFragment newInstance(String accountId, String studentId,
                                                            ArrayList<String> occurrenceIds,
                                                            @Nullable String contextLabel) {
        return build(accountId, studentId, occurrenceIds, false, contextLabel);
    }

    /** Correct attendance that was recorded earlier (Attendance History screen). */
    public static AttendanceBottomSheetFragment newEditInstance(String accountId, String studentId,
                                                                ArrayList<String> occurrenceIds,
                                                                @Nullable String contextLabel) {
        return build(accountId, studentId, occurrenceIds, true, contextLabel);
    }

    private static AttendanceBottomSheetFragment build(String accountId, String studentId,
                                                       ArrayList<String> occurrenceIds, boolean edit,
                                                       @Nullable String contextLabel) {
        AttendanceBottomSheetFragment fragment = new AttendanceBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT_ID, accountId);
        args.putString(ARG_STUDENT_ID, studentId);
        args.putStringArrayList(ARG_OCCURRENCE_IDS, occurrenceIds);
        args.putBoolean(ARG_EDIT_MODE, edit);
        args.putString(ARG_CONTEXT_LABEL, contextLabel);
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
            editMode = getArguments().getBoolean(ARG_EDIT_MODE, false);
            contextLabel = getArguments().getString(ARG_CONTEXT_LABEL);
        }
        if (occurrenceIds == null) {
            occurrenceIds = new ArrayList<>();
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
            if (accountId == null || accountId.trim().isEmpty()) return;
            boolean removedAny = false;
            for (String occId : occurrenceIds) {
                if (occurrenceRepo.deleteOccurrence(accountId, occId)) {
                    removedAny = true;
                }
            }
            Toast.makeText(requireContext(),
                    removedAny ? "Class removed from dashboard" : getString(R.string.attendance_remove_blocked),
                    Toast.LENGTH_SHORT).show();
            if (removedAny) {
                notifyDashboardRefresh();
                dismiss();
            }
        });

        if (contextLabel != null && !contextLabel.trim().isEmpty()) {
            binding.tvAttendanceSubtitle.setText(contextLabel);
        }
        if (editMode) {
            setupEditMode();
        }
    }

    // ------------------------------------------------------------------------------------
    // Correction mode
    // ------------------------------------------------------------------------------------

    private void setupEditMode() {
        binding.tvAttendanceTitle.setText(R.string.attendance_edit_title);
        if (contextLabel == null || contextLabel.trim().isEmpty()) {
            binding.tvAttendanceSubtitle.setText(R.string.attendance_edit_subtitle);
        }
        binding.btnSaveAttendance.setText(R.string.attendance_edit_save);
        binding.btnRemoveClass.setVisibility(View.GONE);

        if (accountId == null || occurrenceIds.isEmpty()) return;

        String primaryId = occurrenceIds.get(0);
        AttendanceRepository.AttendanceEditState state = attendanceRepo.getAttendanceEditState(accountId, primaryId);
        locked = attendanceRepo.isAnyAttendanceBilled(accountId, occurrenceIds);

        if (state != null) {
            String st = state.status;
            boolean byTutor = AttendanceRecord.STATUS_TUTOR_ABSENT.equals(st)
                    || AttendanceRecord.STATUS_CANCELLED_BY_TUTOR.equals(st);

            if (AttendanceRecord.STATUS_PRESENT.equals(st)) {
                binding.toggleGroupStatus.check(R.id.btnStatusPresent);
            } else if (AttendanceRecord.STATUS_STUDENT_ABSENT.equals(st)
                    || AttendanceRecord.STATUS_TUTOR_ABSENT.equals(st)) {
                binding.toggleGroupStatus.check(R.id.btnStatusAbsent);
            } else {
                binding.toggleGroupStatus.check(R.id.btnStatusCancelled);
            }
            if (byTutor) {
                binding.chipByTutor.setChecked(true);
            } else {
                binding.chipByStudent.setChecked(true);
            }

            if (!AttendanceRecord.STATUS_PRESENT.equals(st)) {
                boolean charged = AttendanceRecord.DECISION_CHARGE.equals(state.chargeDecision)
                        || AttendanceRecord.DECISION_INCLUDED_MONTHLY.equals(state.chargeDecision);
                binding.switchChargeClass.setChecked(charged);
            }

            if (state.makeupOccurrenceId != null && state.makeupDateMillis > 0) {
                existingMakeupId = state.makeupOccurrenceId;
                makeupDateMillis = state.makeupDateMillis;
                if (state.makeupStartMinutes >= 0) {
                    makeupStartMinutes = state.makeupStartMinutes;
                }
                binding.switchRescheduleMakeup.setChecked(true);
                binding.btnPickMakeupDateTime.setText(formatMakeupLabel(makeupDateMillis, makeupStartMinutes));
            }
        }

        Lesson lesson = lessonRepo.getLessonForOccurrence(accountId, primaryId);
        if (lesson != null) {
            hadLesson = true;
            if (lesson.getTopicCovered() != null) binding.etTopic.setText(lesson.getTopicCovered());
            if (lesson.getHomework() != null) binding.etHomework.setText(lesson.getHomework());
            if (lesson.getTutorNotes() != null) binding.etNotes.setText(lesson.getTutorNotes());
        }

        if (locked) {
            applyLockedState();
        }
    }

    private void applyLockedState() {
        binding.tvAttendanceSubtitle.setText(R.string.attendance_locked_subtitle);
        binding.tvAttendanceSubtitle.setTextColor(
                MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorError));
        binding.btnSaveAttendance.setEnabled(false);
        binding.btnStatusPresent.setEnabled(false);
        binding.btnStatusAbsent.setEnabled(false);
        binding.btnStatusCancelled.setEnabled(false);
        binding.chipByStudent.setEnabled(false);
        binding.chipByTutor.setEnabled(false);
        binding.switchChargeClass.setEnabled(false);
        binding.switchRescheduleMakeup.setEnabled(false);
        binding.btnPickMakeupDateTime.setEnabled(false);
        binding.etTopic.setEnabled(false);
        binding.etHomework.setEnabled(false);
        binding.etNotes.setEnabled(false);
    }

    private String formatMakeupLabel(long dateMillis, int startMinutes) {
        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        int hourOfDay = startMinutes / 60;
        int minute = startMinutes % 60;
        int h = hourOfDay % 12;
        if (h == 0) h = 12;
        String ampm = hourOfDay >= 12 ? "PM" : "AM";
        return String.format(Locale.US, "Makeup: %s at %02d:%02d %s",
                df.format(new Date(dateMillis)), h, minute, ampm);
    }

    private void pickMakeupDateTime() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (dView, year, month, dayOfMonth) -> {
            Calendar chosenDate = Calendar.getInstance();
            chosenDate.set(year, month, dayOfMonth, 0, 0, 0);
            chosenDate.set(Calendar.MILLISECOND, 0);
            long pickedDate = chosenDate.getTimeInMillis();

            new TimePickerDialog(requireContext(), (tView, hourOfDay, minute) -> {
                makeupDateMillis = pickedDate;
                makeupStartMinutes = (hourOfDay * 60) + minute;
                makeupDirty = true;
                if (binding != null) {
                    binding.btnPickMakeupDateTime.setText(formatMakeupLabel(makeupDateMillis, makeupStartMinutes));
                }
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveRecord() {
        if (accountId == null || accountId.trim().isEmpty() || occurrenceIds.isEmpty()) {
            return;
        }
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

        // Nothing may be saved for a class that is already on an invoice (checked up front so a
        // multi-subject class is never saved half-way).
        if (attendanceRepo.isAnyAttendanceBilled(accountId, occurrenceIds)) {
            Toast.makeText(requireContext(), R.string.attendance_locked_toast, Toast.LENGTH_LONG).show();
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

        String primaryChargeDecision;
        if (checkedId == R.id.btnStatusPresent) {
            primaryChargeDecision = AttendanceRecord.DECISION_CHARGE;
        } else if (scheduleMakeup) {
            primaryChargeDecision = AttendanceRecord.DECISION_WAIVE;
        } else {
            primaryChargeDecision = binding.switchChargeClass.isChecked()
                    ? AttendanceRecord.DECISION_CHARGE : AttendanceRecord.DECISION_WAIVE;
        }

        String topic = binding.etTopic.getText() != null ? binding.etTopic.getText().toString().trim() : "";
        String homework = binding.etHomework.getText() != null ? binding.etHomework.getText().toString().trim() : "";
        String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";

        // A makeup class that already exists and was not changed is kept as it is.
        boolean keepExistingMakeup = editMode && scheduleMakeup && existingMakeupId != null && !makeupDirty;

        int failure = AttendanceRepository.RESULT_OK;
        for (int i = 0; i < occurrenceIds.size(); i++) {
            String occId = occurrenceIds.get(i);
            // Charge once per class session even if multiple subjects are taught in the same slot
            String decisionForOccurrence = (i == 0) ? primaryChargeDecision : AttendanceRecord.DECISION_WAIVE;
            boolean primaryWithMakeup = (i == 0) && scheduleMakeup;
            boolean createMakeupForOccurrence = primaryWithMakeup && !keepExistingMakeup;
            String keepMakeupId = (primaryWithMakeup && keepExistingMakeup) ? existingMakeupId : null;

            AttendanceRecord attendance = new AttendanceRecord(
                    UUID.randomUUID().toString(), accountId, occId, status,
                    decisionForOccurrence, 0, primaryWithMakeup, keepMakeupId, null, System.currentTimeMillis()
            );
            int result = attendanceRepo.recordAttendanceWithBilling(
                    attendance, createMakeupForOccurrence, makeupDateMillis, makeupStartMinutes);

            if (result != AttendanceRepository.RESULT_OK) {
                failure = result;
                break;
            }

            boolean hasLessonText = !topic.isEmpty() || !homework.isEmpty() || !notes.isEmpty();
            if (i == 0 && (hasLessonText || hadLesson)) {
                Lesson lesson = new Lesson(
                        UUID.randomUUID().toString(), accountId, studentId, occId,
                        topic.isEmpty() ? null : topic,
                        homework.isEmpty() ? null : homework,
                        notes.isEmpty() ? null : notes
                );
                lessonRepo.saveLesson(lesson);
            }
        }

        if (failure != AttendanceRepository.RESULT_OK) {
            int msg;
            if (failure == AttendanceRepository.RESULT_BILLED_LOCKED) {
                msg = R.string.attendance_locked_toast;
            } else if (failure == AttendanceRepository.RESULT_MAKEUP_RECORDED) {
                msg = R.string.attendance_makeup_recorded_toast;
            } else {
                msg = R.string.attendance_save_failed;
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            notifyDashboardRefresh();
            return;
        }

        Toast.makeText(requireContext(),
                editMode ? getString(R.string.attendance_update_saved) : "Attendance & billing decision saved",
                Toast.LENGTH_SHORT).show();
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

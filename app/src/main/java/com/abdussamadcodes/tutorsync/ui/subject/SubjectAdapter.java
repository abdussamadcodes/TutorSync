package com.abdussamadcodes.tutorsync.ui.subject;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.databinding.ItemSubjectBinding;
import com.abdussamadcodes.tutorsync.model.Subject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    private final List<Subject> subjects = new ArrayList<>();
    private final Set<String> selectedSubjectIds = new HashSet<>();
    private final OnSubjectClickListener listener;
    private boolean isEnrollmentMode = false;

    public interface OnSubjectClickListener {
        void onSubjectClick(Subject subject);
    }

    public SubjectAdapter(OnSubjectClickListener listener) {
        this.listener = listener;
    }

    public void setSubjects(List<Subject> newSubjects) {
        subjects.clear();
        subjects.addAll(newSubjects);
        notifyDataSetChanged();
    }

    public void setEnrollmentMode(List<String> currentlyEnrolledIds) {
        this.isEnrollmentMode = true;
        this.selectedSubjectIds.clear();
        this.selectedSubjectIds.addAll(currentlyEnrolledIds);
        notifyDataSetChanged();
    }

    public Set<String> getSelectedSubjectIds() {
        return new HashSet<>(selectedSubjectIds);
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubjectBinding binding = ItemSubjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SubjectViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        holder.bind(subjects.get(position));
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    class SubjectViewHolder extends RecyclerView.ViewHolder {
        private final ItemSubjectBinding binding;

        SubjectViewHolder(ItemSubjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                Subject subject = subjects.get(position);

                if (isEnrollmentMode) {
                    if (selectedSubjectIds.contains(subject.getId())) {
                        selectedSubjectIds.remove(subject.getId());
                    } else {
                        selectedSubjectIds.add(subject.getId());
                    }
                    notifyItemChanged(position);
                } else if (listener != null) {
                    listener.onSubjectClick(subject);
                }
            });
        }

        void bind(Subject subject) {
            Context context = binding.getRoot().getContext();
            binding.tvSubjectTitle.setText(subject.getTitle());
            binding.tvSubjectLevel.setText(subject.getLevel() != null && !subject.getLevel().isEmpty() ? subject.getLevel() : context.getString(R.string.subject_level_general));
            binding.cardColorTag.setVisibility(View.GONE);

            if (isEnrollmentMode) {
                boolean isSelected = selectedSubjectIds.contains(subject.getId());
                binding.ivActiveStatus.setVisibility(isSelected ? View.VISIBLE : View.GONE);

                binding.getRoot().setAlpha(isSelected ? 1.0f : 0.6f);
                int strokeWidth = isSelected ? 4 : 0;
                int strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorPrimary);
                binding.getRoot().setStrokeWidth(strokeWidth);
                binding.getRoot().setStrokeColor(strokeColor);
            } else {
                binding.getRoot().setAlpha(1.0f);
                binding.getRoot().setStrokeWidth(0);
                binding.ivActiveStatus.setVisibility(subject.isActive() ? View.VISIBLE : View.GONE);
            }
        }

        private int resolveThemeColor(int attrRes) {
            TypedValue typedValue = new TypedValue();
            binding.getRoot().getContext().getTheme().resolveAttribute(attrRes, typedValue, true);
            return typedValue.data;
        }
    }
}
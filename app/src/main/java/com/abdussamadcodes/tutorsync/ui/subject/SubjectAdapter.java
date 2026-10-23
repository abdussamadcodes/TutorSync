package com.abdussamadcodes.tutorsync.ui.subject;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abdussamadcodes.tutorsync.databinding.ItemSubjectBinding;
import com.abdussamadcodes.tutorsync.model.Subject;
import java.util.ArrayList;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    private final List<Subject> subjects = new ArrayList<>();
    private final OnSubjectClickListener listener;

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
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSubjectClick(subjects.get(position));
                }
            });
        }

        void bind(Subject subject) {
            binding.tvSubjectTitle.setText(subject.getTitle());
            binding.tvSubjectLevel.setText(subject.getLevel() != null && !subject.getLevel().isEmpty() ? subject.getLevel() : "General");

            try {
                if (subject.getColorTag() != null && !subject.getColorTag().isEmpty()) {
                    binding.cardColorTag.setCardBackgroundColor(Color.parseColor(subject.getColorTag()));
                }
            } catch (Exception ignored) {
                // Fallback to default styling if hex is invalid
            }

            binding.ivActiveStatus.setVisibility(subject.isActive() ? View.VISIBLE : View.GONE);
        }
    }
}
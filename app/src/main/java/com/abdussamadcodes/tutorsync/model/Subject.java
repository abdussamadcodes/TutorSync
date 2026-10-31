package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import java.util.regex.Pattern;

public class Subject {
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("\\s*·\\s*SUB-\\d+$", Pattern.CASE_INSENSITIVE);

    private final String id;
    private final String accountId;
    private final String subjectUniqueId;
    private final String rawTitle;
    private final String level;
    private final boolean isActive;
    private final long updatedAt;

    public Subject(@NonNull String id, @NonNull String accountId, @NonNull String title,
                   @Nullable String level, boolean isActive) {
        this(id, accountId, null, title, level, isActive, System.currentTimeMillis());
    }

    public Subject(@NonNull String id, @NonNull String accountId, @NonNull String title,
                   @Nullable String level, boolean isActive, long updatedAt) {
        this(id, accountId, null, title, level, isActive, updatedAt);
    }

    public Subject(@NonNull String id, @NonNull String accountId, @Nullable String subjectUniqueId,
                   @NonNull String title, @Nullable String level, boolean isActive, long updatedAt) {
        this.id = TutorSyncContract.ensureValidUuid(id);
        this.accountId = accountId;
        this.rawTitle = stripSubjectIdSuffix(title);
        this.subjectUniqueId = (subjectUniqueId != null && !subjectUniqueId.trim().isEmpty())
                ? subjectUniqueId.trim()
                : extractSubjectIdFromTitle(title);
        this.level = level;
        this.isActive = isActive;
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    @NonNull
    public static String stripSubjectIdSuffix(@Nullable String title) {
        if (title == null) return "";
        return SUFFIX_PATTERN.matcher(title.trim()).replaceFirst("").trim();
    }

    @Nullable
    private static String extractSubjectIdFromTitle(@Nullable String title) {
        if (title == null) return null;
        int idx = title.lastIndexOf("· SUB-");
        if (idx != -1 && idx + 2 < title.length()) {
            return title.substring(idx + 2).trim();
        }
        return null;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getAccountId() {
        return accountId;
    }

    @Nullable
    public String getSubjectUniqueId() {
        return subjectUniqueId;
    }

    @NonNull
    public String getRawTitle() {
        return rawTitle;
    }

    @NonNull
    public String getTitle() {
        return getFormattedDisplayName();
    }

    @NonNull
    public String getFormattedDisplayName() {
        if (subjectUniqueId != null && !subjectUniqueId.isEmpty()) {
            return rawTitle + " · " + subjectUniqueId;
        }
        return rawTitle;
    }

    @Nullable
    public String getLevel() {
        return level;
    }

    public boolean isActive() {
        return isActive;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public long getLastModified() {
        return updatedAt;
    }
}
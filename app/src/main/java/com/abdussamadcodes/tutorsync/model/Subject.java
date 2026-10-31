package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;

public class Subject {
    private final String id;
    private final String accountId;
    private final String title;
    private final String level;
    private final boolean isActive;
    private final long updatedAt;

    public Subject(@NonNull String id, @NonNull String accountId, @NonNull String title,
                   @Nullable String level, boolean isActive) {
        this(id, accountId, title, level, isActive, System.currentTimeMillis());
    }

    public Subject(@NonNull String id, @NonNull String accountId, @NonNull String title,
                   @Nullable String level, boolean isActive, long updatedAt) {
        this.id = TutorSyncContract.ensureValidUuid(id);
        this.accountId = accountId;
        this.title = title;
        this.level = level;
        this.isActive = isActive;
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getTitle() { return title; }
    @Nullable public String getLevel() { return level; }
    public boolean isActive() { return isActive; }
    public long getUpdatedAt() { return updatedAt; }
    public long getLastModified() { return updatedAt; }
}
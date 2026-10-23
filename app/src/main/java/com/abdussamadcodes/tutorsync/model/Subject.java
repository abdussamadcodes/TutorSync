package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Subject {
    private final String id;
    private final String accountId;
    private final String title;
    private final String level;
    private final String colorTag;
    private final boolean isActive;

    public Subject(@NonNull String id, @NonNull String accountId, @NonNull String title,
                   @Nullable String level, @Nullable String colorTag, boolean isActive) {
        this.id = id;
        this.accountId = accountId;
        this.title = title;
        this.level = level;
        this.colorTag = colorTag;
        this.isActive = isActive;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getTitle() { return title; }
    @Nullable public String getLevel() { return level; }
    @Nullable public String getColorTag() { return colorTag; }
    public boolean isActive() { return isActive; }
}
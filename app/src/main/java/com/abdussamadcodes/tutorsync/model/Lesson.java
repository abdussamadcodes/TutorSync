package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;

public class Lesson {
    private final String id;
    private final String accountId;
    private final String studentId;
    private final String occurrenceId;
    private final String topicCovered;
    private final String homework;
    private final String tutorNotes;
    private final long updatedAt;

    public Lesson(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                  @Nullable String occurrenceId, @Nullable String topicCovered,
                  @Nullable String homework, @Nullable String tutorNotes) {
        this(id, accountId, studentId, occurrenceId, topicCovered, homework, tutorNotes, System.currentTimeMillis());
    }

    public Lesson(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                  @Nullable String occurrenceId, @Nullable String topicCovered,
                  @Nullable String homework, @Nullable String tutorNotes, long updatedAt) {
        this.id = TutorSyncContract.ensureValidUuid(id);
        this.accountId = accountId;
        this.studentId = studentId;
        this.occurrenceId = occurrenceId;
        this.topicCovered = topicCovered;
        this.homework = homework;
        this.tutorNotes = tutorNotes;
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @Nullable public String getOccurrenceId() { return occurrenceId; }
    @Nullable public String getTopicCovered() { return topicCovered; }
    @Nullable public String getHomework() { return homework; }
    @Nullable public String getTutorNotes() { return tutorNotes; }
    public long getUpdatedAt() { return updatedAt; }
    public long getLastModified() { return updatedAt; }
}
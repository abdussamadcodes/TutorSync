package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Lesson {
    private final String id;
    private final String accountId;
    private final String studentId;
    private final String occurrenceId;
    private final String topicCovered;
    private final String homework;
    private final String tutorNotes;

    public Lesson(@NonNull String id, @NonNull String accountId, @NonNull String studentId,
                  @Nullable String occurrenceId, @Nullable String topicCovered,
                  @Nullable String homework, @Nullable String tutorNotes) {
        this.id = id;
        this.accountId = accountId;
        this.studentId = studentId;
        this.occurrenceId = occurrenceId;
        this.topicCovered = topicCovered;
        this.homework = homework;
        this.tutorNotes = tutorNotes;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStudentId() { return studentId; }
    @Nullable public String getOccurrenceId() { return occurrenceId; }
    @Nullable public String getTopicCovered() { return topicCovered; }
    @Nullable public String getHomework() { return homework; }
    @Nullable public String getTutorNotes() { return tutorNotes; }
}
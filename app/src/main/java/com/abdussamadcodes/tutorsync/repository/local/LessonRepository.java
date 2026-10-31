package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.LessonEntry;
import com.abdussamadcodes.tutorsync.model.Lesson;

public class LessonRepository {

    public boolean saveLesson(@NonNull Lesson lesson) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            long now = System.currentTimeMillis();
            long modTime = lesson.getUpdatedAt() > 0 ? lesson.getUpdatedAt() : now;
            String id = TutorSyncContract.ensureValidUuid(lesson.getId());
            String accountId = lesson.getAccountId();

            long createdAt = modTime;
            boolean exists = false;
            try (Cursor c = db.query(LessonEntry.TABLE_NAME,
                    new String[]{LessonEntry.COLUMN_CREATED_AT},
                    LessonEntry.COLUMN_ACCOUNT_ID + " = ? AND " + LessonEntry.COLUMN_ID + " = ?",
                    new String[]{accountId, id}, null, null, null)) {
                if (c.moveToFirst()) {
                    exists = true;
                    if (!c.isNull(0) && c.getLong(0) > 0) {
                        createdAt = c.getLong(0);
                    }
                }
            }

            ContentValues values = new ContentValues();
            values.put(LessonEntry.COLUMN_ID, id);
            values.put(LessonEntry.COLUMN_ACCOUNT_ID, accountId);
            values.put(LessonEntry.COLUMN_STUDENT_ID, lesson.getStudentId());
            values.put(LessonEntry.COLUMN_OCCURRENCE_ID, lesson.getOccurrenceId());
            values.put(LessonEntry.COLUMN_TOPIC_COVERED, lesson.getTopicCovered());
            values.put(LessonEntry.COLUMN_HOMEWORK, lesson.getHomework());
            values.put(LessonEntry.COLUMN_TUTOR_NOTES, lesson.getTutorNotes());
            values.put(LessonEntry.COLUMN_CREATED_AT, createdAt);
            values.put(LessonEntry.COLUMN_UPDATED_AT, modTime);
            values.put(LessonEntry.COLUMN_LAST_MODIFIED, modTime);

            if (exists) {
                return db.update(LessonEntry.TABLE_NAME, values,
                        LessonEntry.COLUMN_ACCOUNT_ID + " = ? AND " + LessonEntry.COLUMN_ID + " = ?",
                        new String[]{accountId, id}) > 0;
            } else {
                return db.insert(LessonEntry.TABLE_NAME, null, values) != -1;
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }
}
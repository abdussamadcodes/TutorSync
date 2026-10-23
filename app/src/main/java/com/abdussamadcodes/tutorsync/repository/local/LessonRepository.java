package com.abdussamadcodes.tutorsync.repository.local;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.LessonEntry;
import com.abdussamadcodes.tutorsync.model.Lesson;

public class LessonRepository {

    public boolean saveLesson(@NonNull Lesson lesson) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(LessonEntry.COLUMN_ID, lesson.getId());
            values.put(LessonEntry.COLUMN_ACCOUNT_ID, lesson.getAccountId());
            values.put(LessonEntry.COLUMN_STUDENT_ID, lesson.getStudentId());
            values.put(LessonEntry.COLUMN_OCCURRENCE_ID, lesson.getOccurrenceId());
            values.put(LessonEntry.COLUMN_TOPIC_COVERED, lesson.getTopicCovered());
            values.put(LessonEntry.COLUMN_HOMEWORK, lesson.getHomework());
            values.put(LessonEntry.COLUMN_TUTOR_NOTES, lesson.getTutorNotes());
            values.put(LessonEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());

            return db.insert(LessonEntry.TABLE_NAME, null, values) != -1;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }
}
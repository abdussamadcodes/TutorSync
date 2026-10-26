package com.abdussamadcodes.tutorsync;

import android.app.Application;

import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncDbHelper;
import com.abdussamadcodes.tutorsync.security.AppLockManager;
import com.abdussamadcodes.tutorsync.util.GlobalExceptionHandler;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;

public class TutorSyncApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GlobalExceptionHandler.install(this);
        LocaleThemeHelper.applySavedLocaleAndTheme(this);

        TutorSyncDbHelper dbHelper = new TutorSyncDbHelper(this);
        DatabaseManager.initializeInstance(dbHelper);

        AppLockManager.init(this);
    }
}
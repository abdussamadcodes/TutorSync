package com.abdussamadcodes.tutorsync;

import android.app.Application;
import com.abdussamadcodes.tutorsync.util.GlobalExceptionHandler;
import com.abdussamadcodes.tutorsync.util.LocaleThemeHelper;

public class TutorSyncApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GlobalExceptionHandler.install(this);
        LocaleThemeHelper.applySavedLocaleAndTheme(this);
    }
}
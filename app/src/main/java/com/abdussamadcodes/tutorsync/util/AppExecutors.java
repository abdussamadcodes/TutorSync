package com.abdussamadcodes.tutorsync.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class AppExecutors {

    private static volatile AppExecutors instance;

    private final Executor diskIO;
    private final Executor mainThread;

    private AppExecutors(@NonNull Executor diskIO, @NonNull Executor mainThread) {
        this.diskIO = diskIO;
        this.mainThread = mainThread;
    }

    @NonNull
    public static AppExecutors getInstance() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors(
                            Executors.newSingleThreadExecutor(r -> {
                                Thread t = new Thread(r, "TutorSync-DiskIO");
                                t.setDaemon(true);
                                return t;
                            }),
                            new MainThreadExecutor()
                    );
                }
            }
        }
        return instance;
    }

    @NonNull
    public Executor diskIO() {
        return diskIO;
    }

    @NonNull
    public Executor mainThread() {
        return mainThread;
    }

    private static final class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                command.run();
            } else {
                mainThreadHandler.post(command);
            }
        }
    }
}
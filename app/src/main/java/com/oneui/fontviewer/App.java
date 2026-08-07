package com.oneui.fontviewer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.oneui.fontviewer.data.database.AppDatabase;
import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;
import com.oneui.fontviewer.fragment.localfont.data.LocalFontRepository;
import com.oneui.fontviewer.fragment.systemfont.data.SystemFontRepository;
import com.oneui.fontviewer.fragment.localfont.data.LocalFontCache;
import com.oneui.fontviewer.fragment.systemfont.data.SystemFontCache;
import com.oneui.fontviewer.utils.CrashHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class App extends Application {

    private static final String TAG = "App";
    private static App sInstance;
    private static final List<WeakReference<Activity>> activities = new ArrayList<>();

    private AppDatabase database;
    private SettingsDataStore settingsDataStore;
    private LocalFontRepository localFontRepository;
    private SystemFontRepository systemFontRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        CrashHandler.init(this);

        initializeCaches();

        initializeDataStore();

        applyInitialTheme();

        new Thread(() -> {
            try {
                initializeDatabase();
                initializeRepositories();

                Log.d(TAG, "All components initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error during initialization", e);
            }
        }).start();

        setupActivityTracking();
    }

    private void initializeCaches() {
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                LocalFontCache.getInstance().initialize(this);
                Log.d(TAG, "✓ LocalFontCache initialized");

                SystemFontCache.getInstance().initialize(this);
                Log.d(TAG, "✓ SystemFontCache initialized");

                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "★★★ Caches initialized in " + duration + "ms");

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize font caches", e);
            }
        }, "LocalFontCache-Initializer").start();
    }

    private void initializeDatabase() {
        database = AppDatabase.getInstance(this);
        Log.d(TAG, "AppDatabase initialized");
    }

    private void initializeDataStore() {
        if (settingsDataStore == null) {
            settingsDataStore = SettingsDataStore.getInstance(this);
            Log.d(TAG, "SettingsDataStore initialized");
        }
    }

    private void initializeRepositories() {
        localFontRepository = LocalFontRepository.getInstance(this);
        Log.d(TAG, "LocalFontRepository initialized");

        systemFontRepository = SystemFontRepository.getInstance(this);
        Log.d(TAG, "SystemFontRepository initialized");
    }

    private void applyInitialTheme() {
        try {
            Boolean isAuto = settingsDataStore.getThemeAuto().blockingFirst();

            if (isAuto != null && isAuto) {
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                Log.d(TAG, "✓ Theme set to FOLLOW_SYSTEM (Blocking Mode)");
            } else {
                Integer themeMode = settingsDataStore.getThemeMode().blockingFirst();

                if (themeMode != null && themeMode == 1) {
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_YES);
                    Log.d(TAG, "✓ Theme set to DARK (Blocking Mode)");
                } else {
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_NO);
                    Log.d(TAG, "✓ Theme set to LIGHT (Blocking Mode)");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply initial theme synchronously", e);
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private void setupActivityTracking() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                activities.add(new WeakReference<>(activity));
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                for (int i = activities.size() - 1; i >= 0; i--) {
                    Activity a = activities.get(i).get();
                    if (a == null || a == activity) {
                        activities.remove(i);
                    }
                }
            }

            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
        });
    }

    public static App getInstance() {
        return sInstance;
    }



    public void recreateAllActivities() {
        for (WeakReference<Activity> ref : new ArrayList<>(activities)) {
            Activity act = ref.get();
            if (act != null && !act.isFinishing()) {
                act.recreate();
                act.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }
    }



    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory warning received");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "Application terminated");
    }
}

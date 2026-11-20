package com.oneui.fontviewer.ui.utils;

import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import com.oneui.fontviewer.data.settings.SettingsRepository;

public class ThemeHelper {

    private static final String TAG = "ThemeHelper";

    public static void applyTheme(Context context) {
        boolean isAuto = SettingsRepository.isThemeAuto(context);
        
        Log.d(TAG, "Applying theme - Auto mode: " + isAuto);
        
        if (isAuto) {
            Log.d(TAG, "Applying FOLLOW_SYSTEM mode");
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            int mode = SettingsRepository.getThemeMode(context);
            if (mode == SettingsRepository.THEME_DARK) {
                Log.d(TAG, "Applying DARK mode");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                Log.d(TAG, "Applying LIGHT mode");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
    }
}

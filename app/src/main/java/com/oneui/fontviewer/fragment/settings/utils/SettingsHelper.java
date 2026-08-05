package com.oneui.fontviewer.fragment.settings.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import android.os.Build;
import android.util.Log;
import android.os.LocaleList;
import android.app.LocaleManager;

import java.util.Locale;

import androidx.appcompat.app.AppCompatDelegate;


import com.oneui.fontviewer.R;
import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;

public class SettingsHelper {

    private static final String TAG = "SettingsHelper";
    
    public static final int LANGUAGE_SYSTEM = 0;
    public static final int LANGUAGE_ARABIC = 1;
    public static final int LANGUAGE_ENGLISH = 2;

    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;


    public static int getLanguageMode(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getSystemAssignedLanguage(context);
        }
        try {
            return SettingsDataStore.getInstance(context)
                    .getLanguageMode()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading language mode", e);
            return LANGUAGE_SYSTEM;
        }
    }

    public static int getSystemAssignedLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                LocaleManager localeManager =
                        context.getSystemService(LocaleManager.class);
                if (localeManager != null) {
                    LocaleList locales = localeManager.getApplicationLocales();
                    if (!locales.isEmpty()) {
                        String lang = locales.get(0).getLanguage();
                        if (lang.equals("ar")) {
                            return LANGUAGE_ARABIC;
                        } else if (lang.equals("en")) {
                            return LANGUAGE_ENGLISH;
                        }
                    }
                }
                return LANGUAGE_SYSTEM;
            } catch (Exception e) {
                Log.e(TAG, "Error reading locale from LocaleManager", e);
                return LANGUAGE_SYSTEM;
            }
        }
        try {
            return SettingsDataStore.getInstance(context)
                    .getLanguageMode()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading language mode from DataStore", e);
            return LANGUAGE_SYSTEM;
        }
    }

    public static int getThemeMode(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getThemeMode()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading theme mode", e);
            return THEME_LIGHT;
        }
    }

    public static boolean isThemeAuto(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getThemeAuto()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading theme auto", e);
            return true;
        }
    }

    
    
    public static boolean isFontPreviewEnabled(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getFontPreviewEnabled()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading font preview setting", e);
            return true;
        }
    }
    
    public static boolean isTranslationEnabled(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getTranslationEnabled()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading translation setting", e);
            return false;
        }
    }
    
    
    public static String getPreviewText(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getPreviewText()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading preview text", e);
            return SettingsDataStore.DEFAULT_PREVIEW_TEXT;
        }
    }


    public static void applyTheme(Context context) {
        boolean isAuto = isThemeAuto(context);
        
        Log.d(TAG, "Applying theme - Auto mode: " + isAuto);
        
        if (isAuto) {
            Log.d(TAG, "Applying FOLLOW_SYSTEM mode");
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            int mode = getThemeMode(context);
            if (mode == THEME_DARK) {
                Log.d(TAG, "Applying DARK mode");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                Log.d(TAG, "Applying LIGHT mode");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
    }


    public static Locale getLocale(Context context) {
        int mode = getLanguageMode(context);
        
        switch (mode) {
            case LANGUAGE_ARABIC:
                return new Locale("ar");
            case LANGUAGE_ENGLISH:
                return new Locale("en");
            case LANGUAGE_SYSTEM:
            default:
                return getSystemLocale();
        }
    }

           private static Locale getSystemLocale() {
        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }

    @SuppressWarnings("deprecation")
    public static Context wrapContext(Context context) {
        Locale locale = getLocale(context);
        Locale.setDefault(locale);
        
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        
        config.setLocale(locale);
        
        Log.d(TAG, "Context wrapped with locale: " + locale.getLanguage());
        
        return context.createConfigurationContext(config);
    }
    
    public static void initializeFromSettings(Context context) {
        applyTheme(context);
    }
                 }

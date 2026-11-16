package com.oneui.fontviewer.data.repository;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.os.LocaleList;
import android.app.LocaleManager;

import java.util.Locale;

import com.oneui.fontviewer.data.datastore.SettingsDataStore;

public class SettingsRepository {

    private static final String TAG = "SettingsRepository";
    
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
}

package com.oneui.fontviewer.fragment.fontviewer.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import io.reactivex.rxjava3.schedulers.Schedulers;

import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;
import com.oneui.fontviewer.fragment.fontviewer.utils.VariableFontHelper;

public class FontViewerPreferenceManager {
    
    private static final String TAG = "FontViewerPrefManager";
    private final SettingsDataStore dataStore;
    private final SharedPreferences originalPathPrefs;
    
    public FontViewerPreferenceManager(Context context) {
        this.dataStore = SettingsDataStore.getInstance(context);
        this.originalPathPrefs = context.getApplicationContext()
                .getSharedPreferences("font_viewer_original_path", Context.MODE_PRIVATE);
    }
    
    public void saveLastViewedFontOriginalPath(String originalPath) {
        originalPathPrefs.edit().putString("original_path", originalPath).apply();
    }
    
    public String getLastViewedFontOriginalPath() {
        return originalPathPrefs.getString("original_path", null);
    }
    
    public void saveLastViewedFont(String fontPath, String fileName, String realName) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path");
            return;
        }
        
        dataStore.setLastViewedFont(fontPath, fileName, realName)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved last viewed font: " + fontPath),
                    error -> Log.e(TAG, "Error saving font: " + error.getMessage())
                );
    }
    
    public String getLastViewedFontPath() {
        try {
            return dataStore.getViewerFontPath().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error getting font path: " + e.getMessage());
            return null;
        }
    }
    
    public String getLastViewedFontFileName() {
        try {
            return dataStore.getViewerFileName().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name: " + e.getMessage());
            return null;
        }
    }
    
    public String getLastViewedFontRealName() {
        try {
            return dataStore.getViewerRealName().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error getting real name: " + e.getMessage());
            return null;
        }
    }
    
    
    
    public void clearLastViewedFont() {
        dataStore.clearLastViewedFont()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Cleared last viewed font"),
                    error -> Log.e(TAG, "Error clearing font: " + error.getMessage())
                );
    }
    
    
    
    public void saveFontWeight(float fontWeight) {
        dataStore.setViewerFontWeight(fontWeight)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font weight: " + fontWeight),
                    error -> Log.e(TAG, "Error saving font weight: " + error.getMessage())
                );
    }
    
    
    public float getFontWeight(float defaultWeight) {
        try {
            Float value = dataStore.getViewerFontWeight().blockingFirst();
            return value != null ? value : defaultWeight;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font weight: " + e.getMessage());
            return defaultWeight;
        }
    }

    

    public void saveFontWidth(float fontWidth) {
        dataStore.setViewerFontWidth(fontWidth)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font width: " + fontWidth),
                    error -> Log.e(TAG, "Error saving font width: " + error.getMessage())
                );
    }

    public float getFontWidth(float defaultWidth) {
        try {
            Float value = dataStore.getViewerFontWidth().blockingFirst();
            return value != null ? value : defaultWidth;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font width: " + e.getMessage());
            return defaultWidth;
        }
    }

    public void saveFontGrade(float fontGrade) {
        dataStore.setViewerFontGrade(fontGrade)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font grade: " + fontGrade),
                    error -> Log.e(TAG, "Error saving font grade: " + error.getMessage())
                );
    }

    public float getFontGrade(float defaultGrade) {
        try {
            Float value = dataStore.getViewerFontGrade().blockingFirst();
            return value != null ? value : defaultGrade;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font grade: " + e.getMessage());
            return defaultGrade;
        }
    }

    public void saveFontRoundness(float fontRoundness) {
        dataStore.setViewerFontRoundness(fontRoundness)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font roundness: " + fontRoundness),
                    error -> Log.e(TAG, "Error saving font roundness: " + error.getMessage())
                );
    }

    public float getFontRoundness(float defaultRoundness) {
        try {
            Float value = dataStore.getViewerFontRoundness().blockingFirst();
            return value != null ? value : defaultRoundness;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font roundness: " + e.getMessage());
            return defaultRoundness;
        }
    }

    public void saveFontItalicAxis(float italValue) {
        dataStore.setViewerFontItalicAxis(italValue)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font italic axis: " + italValue),
                    error -> Log.e(TAG, "Error saving font italic axis: " + error.getMessage())
                );
    }

    public float getFontItalicAxis(float defaultItal) {
        try {
            Float value = dataStore.getViewerFontItalicAxis().blockingFirst();
            return value != null ? value : defaultItal;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font italic axis: " + e.getMessage());
            return defaultItal;
        }
    }

    public void saveFontMono(float monoValue) {
        dataStore.setViewerFontMono(monoValue)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font mono: " + monoValue),
                    error -> Log.e(TAG, "Error saving font mono: " + error.getMessage())
                );
    }

    public float getFontMono(float defaultMono) {
        try {
            Float value = dataStore.getViewerFontMono().blockingFirst();
            return value != null ? value : defaultMono;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font mono: " + e.getMessage());
            return defaultMono;
        }
    }

    // ★ دالتان عامتان (Generic) للحفظ والاسترجاع حسب وسم المحور، تُستخدمان من FontViewerFragment
    //   بدل استدعاء الدالة المخصصة لكل محور يدوياً في كل مرة ★
    public void saveFontAxisValue(String axisTag, float value) {
        if (VariableFontHelper.AXIS_WGHT.equals(axisTag)) {
            saveFontWeight(value);
        } else if (VariableFontHelper.AXIS_WDTH.equals(axisTag)) {
            saveFontWidth(value);
        } else if (VariableFontHelper.AXIS_ITAL.equals(axisTag)) {
            saveFontItalicAxis(value);
        } else if (VariableFontHelper.AXIS_GRAD.equals(axisTag)) {
            saveFontGrade(value);
        } else if (VariableFontHelper.AXIS_ROND.equals(axisTag)) {
            saveFontRoundness(value);
        } else if (VariableFontHelper.AXIS_MONO.equals(axisTag)) {
            saveFontMono(value);
        } else {
            Log.w(TAG, "Unknown axis tag for saving: " + axisTag);
        }
    }

    public float getFontAxisValue(String axisTag, float defaultValue) {
        if (VariableFontHelper.AXIS_WGHT.equals(axisTag)) {
            return getFontWeight(defaultValue);
        } else if (VariableFontHelper.AXIS_WDTH.equals(axisTag)) {
            return getFontWidth(defaultValue);
        } else if (VariableFontHelper.AXIS_ITAL.equals(axisTag)) {
            return getFontItalicAxis(defaultValue);
        } else if (VariableFontHelper.AXIS_GRAD.equals(axisTag)) {
            return getFontGrade(defaultValue);
        } else if (VariableFontHelper.AXIS_ROND.equals(axisTag)) {
            return getFontRoundness(defaultValue);
        } else if (VariableFontHelper.AXIS_MONO.equals(axisTag)) {
            return getFontMono(defaultValue);
        }
        return defaultValue;
    }
    
    
    }

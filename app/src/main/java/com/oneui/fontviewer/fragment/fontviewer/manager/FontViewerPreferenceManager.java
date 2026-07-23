package com.oneui.fontviewer.fragment.fontviewer.manager;

import android.content.Context;
import android.util.Log;

import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;

import io.reactivex.rxjava3.schedulers.Schedulers;

public class FontViewerPreferenceManager {
    
    private static final String TAG = "FontViewerPrefManager";
    private final SettingsDataStore dataStore;
    
    public FontViewerPreferenceManager(Context context) {
        this.dataStore = SettingsDataStore.getInstance(context);
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
    
    public boolean hasLastViewedFont() {
        return getLastViewedFontPath() != null;
    }
    
    public void clearLastViewedFont() {
        dataStore.clearLastViewedFont()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Cleared last viewed font"),
                    error -> Log.e(TAG, "Error clearing font: " + error.getMessage())
                );
    }
    
    public void saveFontSize(float fontSize) {
        dataStore.setViewerFontSize(fontSize)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font size: " + fontSize),
                    error -> Log.e(TAG, "Error saving font size: " + error.getMessage())
                );
    }
    
    public float getFontSize() {
        return getFontSize(SettingsDataStore.DEFAULT_VIEWER_FONT_SIZE);
    }
    
    public float getFontSize(float defaultSize) {
        try {
            Float value = dataStore.getViewerFontSize().blockingFirst();
            return value != null ? value : defaultSize;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font size: " + e.getMessage());
            return defaultSize;
        }
    }
    
    public void saveFontWeight(float fontWeight) {
        dataStore.setViewerFontWeight(fontWeight)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font weight: " + fontWeight),
                    error -> Log.e(TAG, "Error saving font weight: " + error.getMessage())
                );
    }
    
    public float getFontWeight() {
        return getFontWeight(SettingsDataStore.DEFAULT_VIEWER_FONT_WEIGHT);
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
    
    public void resetFontSize() {
        saveFontSize(SettingsDataStore.DEFAULT_VIEWER_FONT_SIZE);
    }
    
    public void resetFontWeight() {
        saveFontWeight(SettingsDataStore.DEFAULT_VIEWER_FONT_WEIGHT);
    }
}

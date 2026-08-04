package com.oneui.fontviewer.fragment.systemfont.manager;

import android.content.Context;
import android.util.Log;
import io.reactivex.rxjava3.schedulers.Schedulers;

import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;

public class SystemFontPreferenceManager {
    
    private static final String TAG = "SystemFontPrefManager";
    private final SettingsDataStore dataStore;
    
    private String cachedLastOpenedPath;
    
    public SystemFontPreferenceManager(Context context) {
        this.dataStore = SettingsDataStore.getInstance(context);
        
        try {
            cachedLastOpenedPath = dataStore.getLastOpenedSystemFontPath().blockingFirst();
        } catch (Exception e) {
            cachedLastOpenedPath = null;
            Log.d(TAG, "No cached last opened system font");
        }
    }
    
    public void saveLastOpenedFont(String fontPath) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path");
            return;
        }
        
        cachedLastOpenedPath = fontPath;
        
        dataStore.setLastOpenedSystemFontPath(fontPath)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved last opened system font: " + fontPath),
                    error -> Log.e(TAG, "Error saving last opened system font", error)
                );
    }
    
    
    
    public String getLastOpenedFont() {
        try {
            cachedLastOpenedPath = dataStore.getLastOpenedSystemFontPath().blockingFirst();
            return cachedLastOpenedPath;
        } catch (Exception e) {
            Log.d(TAG, "No last opened system font found");
            return null;
        }
    }
    
    public boolean isLastOpenedFont(String fontPath) {
        if (fontPath == null) {
            return false;
        }
        
        return cachedLastOpenedPath != null && cachedLastOpenedPath.equals(fontPath);
    }
    
    
}

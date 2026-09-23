package com.oneui.fontviewer.fragment.localfont.manager;

import android.content.Context;
import android.util.Log;

import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;

import io.reactivex.rxjava3.schedulers.Schedulers;

public class LocalFontPreferenceManager {
    
    private static final String TAG = "LocalFontPreferenceManager";
    private final SettingsDataStore dataStore;
    private final boolean isFavoritesList;
    
    private String cachedLastOpenedPath;
    
    public LocalFontPreferenceManager(Context context) {
        this(context, false);
    }

    public LocalFontPreferenceManager(Context context, boolean isFavoritesList) {
        this.dataStore = SettingsDataStore.getInstance(context);
        this.isFavoritesList = isFavoritesList;
        
        try {
            cachedLastOpenedPath = isFavoritesList
                    ? dataStore.getLastOpenedFavoriteFontPath().blockingFirst()
                    : dataStore.getLastOpenedFontPath().blockingFirst();
        } catch (Exception e) {
            cachedLastOpenedPath = null;
            Log.d(TAG, "No cached last opened font");
        }
    }
    
    public void saveLastOpenedFont(String fontPath) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path");
            return;
        }
        
        cachedLastOpenedPath = fontPath;
        
        (isFavoritesList
                ? dataStore.setLastOpenedFavoriteFontPath(fontPath)
                : dataStore.setLastOpenedFontPath(fontPath))
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved last opened font: " + fontPath),
                    error -> Log.e(TAG, "Error saving last opened font", error)
                );
    }
    
    
    
    public boolean isLastOpenedFont(String fontPath) {
        if (fontPath == null) {
            return false;
        }
        
        return cachedLastOpenedPath != null && cachedLastOpenedPath.equals(fontPath);
    }
    
    
    
    public void saveFontFolderPath(String folderPath) {
        if (folderPath == null) {
            Log.w(TAG, "Attempted to save null folder path");
            return;
        }
        
        dataStore.setFolderPath(folderPath)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font folder path: " + folderPath),
                    error -> Log.e(TAG, "Error saving folder path", error)
                );
    }
    
    public String getFontFolderPath() {
        try {
            return dataStore.getFolderPath().blockingFirst();
        } catch (Exception e) {
            Log.d(TAG, "No folder path found");
            return null;
        }
    }
    
    public boolean hasFontFolderPath() {
        return getFontFolderPath() != null;
    }  
    
}

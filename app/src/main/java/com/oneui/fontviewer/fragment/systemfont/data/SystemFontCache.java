package com.oneui.fontviewer.fragment.systemfont.data;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import com.oneui.fontviewer.data.database.AppDatabase;
import com.oneui.fontviewer.data.entity.FontEntity;
import com.oneui.fontviewer.fragment.fontviewer.utils.VariableFontHelper;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SystemFontCache {
    
    private static final String TAG = "SystemFontCache";
    private static SystemFontCache instance;
    
    private final ConcurrentHashMap<String, Typeface> memoryCache;
    private final ConcurrentHashMap<String, Typeface> weightedCache;
    private Context context;
    private AppDatabase database;
    private volatile boolean isInitialized = false;
    
    private SystemFontCache() {
        memoryCache = new ConcurrentHashMap<>(150);
        weightedCache = new ConcurrentHashMap<>(300);
    }
    
    public static synchronized SystemFontCache getInstance() {
        if (instance == null) {
            instance = new SystemFontCache();
        }
        return instance;
    }
    
    public void initialize(Context context) {
        if (isInitialized) {
            return;
        }
        
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
        
        Log.d(TAG, "SystemFontCache initializing with Room Database");
        
        preloadCachedFontsFromDatabase();
        
        isInitialized = true;
    }
    
    private void preloadCachedFontsFromDatabase() {
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                long startTime = System.currentTimeMillis();
                
                List<FontEntity> cachedSystemFonts = database.fontDao().getCachedFonts();
                
                if (cachedSystemFonts == null || cachedSystemFonts.isEmpty()) {
                    Log.d(TAG, "No cached system fonts found in database");
                    return;
                }
                
                cachedSystemFonts.removeIf(font -> !font.isSystemFont());
                
                if (cachedSystemFonts.isEmpty()) {
                    Log.d(TAG, "No cached system fonts after filtering");
                    return;
                }
                
                Log.d(TAG, "Found " + cachedSystemFonts.size() + " cached system fonts");
                
                int loadedCount = 0;
                
                cachedSystemFonts.sort((f1, f2) -> {
                    int countCompare = Integer.compare(f2.getAccessCount(), f1.getAccessCount());
                    if (countCompare != 0) return countCompare;
                    return Long.compare(f2.getLastAccessTime(), f1.getLastAccessTime());
                });
                
                for (FontEntity font : cachedSystemFonts) {
                    String path = font.getPath();
                    
                    Typeface typeface = loadTypefaceInternal(path, 0, 0);
                    
                    if (typeface != null) {
                        memoryCache.put(path, typeface);
                        loadedCount++;
                        
                        if (loadedCount % 10 == 0) {
                            Log.d(TAG, "Preloaded " + loadedCount + " system fonts...");
                        }
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "★ Auto-preloaded " + loadedCount + " system fonts in " + duration + "ms");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to preload cached system fonts from database", e);
            }
        }, "SystemFontCache-Preloader").start();
    }
    
    public Typeface getIfCached(String fontPath) {
        if (fontPath == null) return null;
        return memoryCache.get(fontPath);
    }
    
    public Typeface getTypeface(String fontPath) {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }
        
        return memoryCache.computeIfAbsent(fontPath, path -> loadTypefaceInternal(path, 0, 0));
    }
    
    public Typeface getTypefaceWithWeight(String fontPath, float weight, int ttcIndex) {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }
        
        Log.d(TAG, "Loading typeface with weight: " + weight + ", ttcIndex: " + ttcIndex);
        
        Typeface typeface = loadTypefaceInternal(fontPath, weight, ttcIndex);
        
        if (typeface != null) {
            Log.d(TAG, "Successfully created typeface with weight: " + weight);
        } else {
            Log.e(TAG, "Failed to create typeface with weight: " + weight);
        }
        
        return typeface;
    }

    /**
     * نسخة عامة تدعم تطبيق عدة محاور دفعة واحدة (وزن، عرض، ميل، تدرج، استدارة، تباعد أحادي...)
     * على خط النظام، بنفس فلسفة getTypefaceWithWeight.
     */
    public Typeface getTypefaceWithAxes(String fontPath, Map<String, Float> axisValues, int ttcIndex) {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }

        Log.d(TAG, "Loading typeface with axes: " + axisValues + ", ttcIndex: " + ttcIndex);

        Typeface typeface = loadTypefaceWithAxesInternal(fontPath, axisValues, ttcIndex);

        if (typeface != null) {
            Log.d(TAG, "Successfully created typeface with axes: " + axisValues);
        } else {
            Log.e(TAG, "Failed to create typeface with axes: " + axisValues);
        }

        return typeface;
    }
    
    
    
    private Typeface loadTypefaceInternal(String fontPath, float weight, int ttcIndex) {
        File fontFile = new File(fontPath);
        if (!fontFile.exists() || !fontFile.canRead()) {
            Log.w(TAG, "Font file does not exist or cannot be read: " + fontPath);
            return null;
        }
        
        return loadTypefaceUsingBuilder(fontFile, weight, ttcIndex);
    }
        private Typeface loadTypefaceUsingBuilder(File fontFile, float weight, int ttcIndex) {
        try {
            android.graphics.fonts.Font.Builder fontBuilder = 
                new android.graphics.fonts.Font.Builder(fontFile);
            
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                Log.d(TAG, "Set TTC index: " + ttcIndex);
            }
            
            if (weight > 0 && weight != 400) {
                String variationSettings = "'wght' " + weight;
                fontBuilder.setFontVariationSettings(variationSettings);
                Log.d(TAG, "Set font variation settings: " + variationSettings);
            }
            
            android.graphics.fonts.Font font = fontBuilder.build();
            
            Typeface.CustomFallbackBuilder fallbackBuilder = 
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );
            
            return fallbackBuilder.build();
            
        } catch (Exception e) {
            Log.e(TAG, "Font.Builder failed", e);
            
            if (weight <= 0 || weight == 400) {
                return loadTypefaceFromFile(fontFile.getAbsolutePath());
            }
            
            return null;
        }
    }

    private Typeface loadTypefaceWithAxesInternal(String fontPath, Map<String, Float> axisValues, int ttcIndex) {
        File fontFile = new File(fontPath);
        if (!fontFile.exists() || !fontFile.canRead()) {
            Log.w(TAG, "Font file does not exist or cannot be read: " + fontPath);
            return null;
        }

        return loadTypefaceUsingBuilderWithAxes(fontFile, axisValues, ttcIndex);
    }

    private Typeface loadTypefaceUsingBuilderWithAxes(File fontFile, Map<String, Float> axisValues, int ttcIndex) {
        try {
            android.graphics.fonts.Font.Builder fontBuilder =
                new android.graphics.fonts.Font.Builder(fontFile);

            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                Log.d(TAG, "Set TTC index: " + ttcIndex);
            }

            String variationSettings = VariableFontHelper.buildVariationSettingsString(axisValues);
            if (!variationSettings.isEmpty()) {
                fontBuilder.setFontVariationSettings(variationSettings);
                Log.d(TAG, "Set font variation settings: " + variationSettings);
            }

            android.graphics.fonts.Font font = fontBuilder.build();

            Typeface.CustomFallbackBuilder fallbackBuilder =
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );

            return fallbackBuilder.build();

        } catch (Exception e) {
            Log.e(TAG, "Font.Builder failed for axes", e);

            if (axisValues == null || axisValues.isEmpty()) {
                return loadTypefaceFromFile(fontFile.getAbsolutePath());
            }

            return null;
        }
    }
    
    private Typeface loadTypefaceFromFile(String fontPath) {
        try {
            return Typeface.createFromFile(fontPath);
        } catch (Exception e) {
            Log.e(TAG, "Error loading font from file: " + fontPath, e);
            return null;
        }
    }
    
    public void preloadFonts(List<String> fontPaths) {
        if (fontPaths == null || fontPaths.isEmpty()) {
            return;
        }
        
        new Thread(() -> {
            int loadedCount = 0;
            for (String fontPath : fontPaths) {
                if (getIfCached(fontPath) == null) {
                    Typeface typeface = getTypeface(fontPath);
                    if (typeface != null) {
                        loadedCount++;
                    }
                }
            }
            
            Log.d(TAG, "Preloaded " + loadedCount + " system fonts into memory");
        }, "SystemFontCache-BackgroundPreload").start();
    }
}

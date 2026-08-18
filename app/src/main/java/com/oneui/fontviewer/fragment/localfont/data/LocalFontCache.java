package com.oneui.fontviewer.fragment.localfont.data;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import com.oneui.fontviewer.data.database.AppDatabase;
import com.oneui.fontviewer.data.entity.FontEntity;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class LocalFontCache {
    
    private static final String TAG = "LocalFontCache";
    private static LocalFontCache instance;
    
    private final ConcurrentHashMap<String, Typeface> memoryCache;
    private Context context;
    private AppDatabase database;
    private volatile boolean isInitialized = false;

    private final java.util.concurrent.ExecutorService fontLoaderExecutor =
        java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private LocalFontCache() {
        memoryCache = new ConcurrentHashMap<>(150);
    }
    
    public static synchronized LocalFontCache getInstance() {
        if (instance == null) {
            instance = new LocalFontCache();
        }
        return instance;
    }
    
    public void initialize(Context context) {
        if (isInitialized) {
            return;
        }
        
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
        
        Log.d(TAG, "LocalFontCache initializing with Room Database");
        
        preloadCachedFontsFromDatabase();
        
        isInitialized = true;
    }
    
    private void preloadCachedFontsFromDatabase() {
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                long startTime = System.currentTimeMillis();
                
                List<FontEntity> cachedFonts = database.fontDao().getCachedFonts();
                
                if (cachedFonts == null || cachedFonts.isEmpty()) {
                    Log.d(TAG, "No cached fonts found in database");
                    return;
                }
                
                cachedFonts.removeIf(FontEntity::isSystemFont);
                
                if (cachedFonts.isEmpty()) {
                    Log.d(TAG, "No cached local fonts found");
                    return;
                }
                
                Log.d(TAG, "Found " + cachedFonts.size() + " cached local fonts in database");
                
                int loadedCount = 0;
                
                cachedFonts.sort((f1, f2) -> {
                    int countCompare = Integer.compare(f2.getAccessCount(), f1.getAccessCount());
                    if (countCompare != 0) return countCompare;
                    
                    return Long.compare(f2.getLastAccessTime(), f1.getLastAccessTime());
                });
                
                for (FontEntity font : cachedFonts) {
                    String path = font.getPath();
                    
                    Typeface typeface = loadTypefaceFromFile(path);
                    
                    if (typeface != null) {
                        memoryCache.put(path, typeface);
                        loadedCount++;
                        
                        if (loadedCount % 10 == 0) {
                            Log.d(TAG, "Preloaded " + loadedCount + " fonts...");
                        }
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "★ Auto-preloaded " + loadedCount + " fonts in " + duration + "ms");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to preload cached fonts from database", e);
            }
        }, "LocalFontCache-Preloader").start();
    }
    
    public Typeface getIfCached(String fontPath) {
        if (fontPath == null) return null;
        return memoryCache.get(fontPath);
    }
    
    public Typeface getTypeface(String fontPath) {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }
        
        return memoryCache.computeIfAbsent(fontPath, this::loadTypefaceFromFile);
    }
    
    
    
    private Typeface loadTypefaceFromFile(String fontPath) {
        try {
            File fontFile = new File(fontPath);
            if (!fontFile.exists() || !fontFile.canRead()) {
                Log.w(TAG, "Font file does not exist or cannot be read: " + fontPath);
                return null;
            }
            
            return loadTypefaceUsingBuilder(fontFile);
        } catch (Exception e) {
            Log.e(TAG, "Error loading font from file: " + fontPath, e);
            return null;
        }
    }

    private Typeface loadTypefaceUsingBuilder(File fontFile) {
        try {
            android.graphics.fonts.Font.Builder fontBuilder =
                new android.graphics.fonts.Font.Builder(fontFile);

            android.graphics.fonts.Font font = fontBuilder.build();

            Typeface.CustomFallbackBuilder fallbackBuilder =
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );

            return fallbackBuilder.build();

        } catch (Exception e) {
            Log.e(TAG, "Font.Builder failed, falling back to createFromFile", e);
            return Typeface.createFromFile(fontFile);
        }
    } 
    
    public void preloadFonts(List<String> fontPaths) {
        if (fontPaths == null || fontPaths.isEmpty()) {
            return;
        }
        
        fontLoaderExecutor.execute(() -> {
            int loadedCount = 0;
            for (String fontPath : fontPaths) {
                if (getIfCached(fontPath) == null) {
                    Typeface typeface = getTypeface(fontPath);
                    if (typeface != null) {
                        loadedCount++;
                    }
                }
            }
            
            Log.d(TAG, "Preloaded " + loadedCount + " fonts into memory");
        });
    }
    
    
}

package com.oneui.fontviewer;

import android.app.Application;
import android.util.Log;

import com.oneui.fontviewer.data.common.room.FontDatabase;
import com.oneui.fontviewer.data.AppDataStore;
import com.oneui.fontviewer.data.localfont.LocalFontRepository;
import com.oneui.fontviewer.data.systemfont.SystemFontRepository;
import com.oneui.fontviewer.data.localfont.LocalFontCache;
import com.oneui.fontviewer.data.systemfont.SystemFontCache;
import com.oneui.fontviewer.ui.core.base.ActivityTracker;
import com.oneui.fontviewer.ui.core.utils.ThemeHelper;
import com.oneui.fontviewer.utils.CrashHandler;

/**
 * App - النسخة المحسّنة لحل مشكلة وميض الثيم
 * تم إصلاح مشكلة ظهور الثيم الخطأ لأجزاء من الثانية عند بدء التطبيق
 * 
 * ═══════════════════════════════════════════════════════════════════
 * ★ الحل الكامل: قراءة إعدادات الثيم بشكل متزامن (Blocking) ★
 * ═══════════════════════════════════════════════════════════════════
 */
public class FontViewerApp extends Application {

    private static final String TAG = "App";

    private AppDataStore appDataStore;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize crash handler first
        CrashHandler.init(this);
        
        // ★★★ تهيئة DataStore هنا مباشرة (ليس في خيط خلفي) ★★★
        // السبب: نحتاجه فوراً لقراءة إعدادات الثيم
        // ملاحظة: قدّمناه على تهيئة الكاش، لأن إنشاء AppDataStore يبدأ قراءة ملف الإعدادات من القرص
        // في الخلفية فوراً، فيسبق ذلك أي عمل آخر يزاحمه على القرص والمعالج عند الإقلاع بعد إعادة تشغيل الجهاز
        initializeDataStore();

        // ★★★ الحل الرئيسي: تهيئة الكاش مبكراً جداً ★★★
        initializeCaches();

        // ★★★ تطبيق الثيم بشكل متزامن لمنع الوميض ★★★
        ThemeHelper.applyInitialTheme(appDataStore);
        
        // Initialize other components in background thread
        new Thread(() -> {
            try {
                initializeDatabase();
                initializeRepositories();
                
                Log.d(TAG, "All components initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error during initialization", e);
            }
        }).start();
        
        ActivityTracker.register(this);
    }
    
    /**
     * تهيئة الكاش للخطوط
     */
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
        FontDatabase.getInstance(this);
        Log.d(TAG, "FontDatabase initialized");
    }
    
    private void initializeDataStore() {
        if (appDataStore == null) {
            appDataStore = AppDataStore.getInstance(this);
            Log.d(TAG, "AppDataStore initialized");
        }
    }
    
    /**
     * تهيئة مستودعات البيانات مسبقاً في الخلفية
     * يضمن هذا أن الـ Singleton لكل مستودع جاهز في الذاكرة
     * قبل أن يحتاجه أي ViewModel، مما يُقلل العبء عند فتح الشاشات
     */
    private void initializeRepositories() {
        LocalFontRepository.getInstance(this);
        Log.d(TAG, "LocalFontRepository initialized");
        
        SystemFontRepository.getInstance(this);
        Log.d(TAG, "SystemFontRepository initialized");
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

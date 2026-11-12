package com.oneui.fontviewer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.oneui.fontviewer.data.database.AppDatabase;
import com.oneui.fontviewer.ui.settings.datastore.SettingsDataStore;
import com.oneui.fontviewer.ui.fragment.localfont.data.LocalFontRepository;
import com.oneui.fontviewer.ui.fragment.systemfont.data.SystemFontRepository;
import com.oneui.fontviewer.ui.fragment.localfont.data.LocalFontCache;
import com.oneui.fontviewer.ui.fragment.systemfont.data.SystemFontCache;
import com.oneui.fontviewer.ui.utils.CrashHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    // أقصى مدة ننتظرها قراءة الثيم على الخيط الرئيسي أثناء onCreate.
    // بعد إعادة تشغيل الجهاز قد يتأخر القرص والخيوط بسبب إقلاع النظام، ولا يجوز أن يبقى الخيط الرئيسي
    // محجوباً بلا حد (وإلا ظهر ANR). عند انتهاء المهلة نبدأ بثيم النظام ثم نطبّق الثيم المحفوظ فور وصوله.
    private static final long THEME_READ_TIMEOUT_MS = 1500L;

    private static FontViewerApp sInstance;
    private static final List<WeakReference<Activity>> activities = new ArrayList<>();
    
    private SettingsDataStore settingsDataStore;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        
        // Initialize crash handler first
        CrashHandler.init(this);
        
        // ★★★ تهيئة DataStore هنا مباشرة (ليس في خيط خلفي) ★★★
        // السبب: نحتاجه فوراً لقراءة إعدادات الثيم
        // ملاحظة: قدّمناه على تهيئة الكاش، لأن إنشاء SettingsDataStore يبدأ قراءة ملف الإعدادات من القرص
        // في الخلفية فوراً، فيسبق ذلك أي عمل آخر يزاحمه على القرص والمعالج عند الإقلاع بعد إعادة تشغيل الجهاز
        initializeDataStore();

        // ★★★ الحل الرئيسي: تهيئة الكاش مبكراً جداً ★★★
        initializeCaches();

        // ★★★ تطبيق الثيم بشكل متزامن لمنع الوميض ★★★
        applyInitialTheme();
        
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
        
        setupActivityTracking();
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
        AppDatabase.getInstance(this);
        Log.d(TAG, "AppDatabase initialized");
    }
    
    private void initializeDataStore() {
        if (settingsDataStore == null) {
            settingsDataStore = SettingsDataStore.getInstance(this);
            Log.d(TAG, "SettingsDataStore initialized");
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
    
    /**
     * ★★★ الحل الكامل لمشكلة الوميض ★★★
     * 
     * تطبيق الثيم بشكل متزامن (Blocking) قبل رسم أي واجهة
     * 
     * ملاحظة مهمة:
     * - استخدام blockingFirst() آمن هنا لأننا نقرأ قيمة واحدة صغيرة فقط
     * - العملية سريعة جداً (أقل من 10ms عادةً) ولن تسبب تجميد
     * - هذا يضمن تطبيق الثيم الصحيح قبل ظهور أي شاشة
     *
     * تحديث: التقدير "أقل من 10ms" لا يصح دائماً، فبعد إعادة تشغيل الجهاز قد تستغرق القراءة الأولى من القرص
     * ثواني، لذلك أُضيفت مهلة THEME_READ_TIMEOUT_MS: إن انتهت نعود لثيم النظام ثم نطبّق الثيم المحفوظ لاحقاً.
     */
    private void applyInitialTheme() {
        try {
            // قراءة إعداد الثيم التلقائي بشكل متزامن
            Boolean isAuto = settingsDataStore.getThemeAuto()
                    .timeout(THEME_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .blockingFirst();
            
            if (isAuto != null && isAuto) {
                // الثيم تلقائي - اتبع إعدادات النظام
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                Log.d(TAG, "✓ Theme set to FOLLOW_SYSTEM (Blocking Mode)");
            } else {
                // الثيم يدوي - اقرأ الوضع المحدد
                Integer themeMode = settingsDataStore.getThemeMode()
                        .timeout(THEME_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .blockingFirst();
                
                if (themeMode != null && themeMode == 1) {
                    // وضع داكن
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_YES);
                    Log.d(TAG, "✓ Theme set to DARK (Blocking Mode)");
                } else {
                    // وضع فاتح (القيمة الافتراضية)
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_NO);
                    Log.d(TAG, "✓ Theme set to LIGHT (Blocking Mode)");
                }
            }
        } catch (Exception e) {
            // في حالة حدوث خطأ، استخدم إعدادات النظام كقيمة افتراضية آمنة
            Log.e(TAG, "Failed to apply initial theme synchronously", e);
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

            // لم نقرأ الثيم المحفوظ في الوقت المناسب: ننتظره في خيط خلفي (دون حجب الخيط الرئيسي) ثم نطبّقه
            applySavedThemeWhenReady();
        }
    }

    /**
     * يقرأ الثيم المحفوظ في خيط خلفي ثم يطبّقه على الخيط الرئيسي. يُستدعى فقط في الحالة النادرة التي
     * تأخرت فيها القراءة المتزامنة في applyInitialTheme عن المهلة المحددة.
     */
    private void applySavedThemeWhenReady() {
        new Thread(() -> {
            try {
                Boolean isAuto = settingsDataStore.getThemeAuto().blockingFirst();
                Integer themeMode = settingsDataStore.getThemeMode().blockingFirst();

                final int nightMode;
                if (isAuto != null && isAuto) {
                    nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                } else if (themeMode != null && themeMode == 1) {
                    nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                } else {
                    nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    AppCompatDelegate.setDefaultNightMode(nightMode);
                    Log.d(TAG, "✓ Saved theme applied after delayed read");
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply saved theme after delayed read", e);
            }
        }, "Theme-LateApply").start();
    }
    
    private void setupActivityTracking() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                activities.add(new WeakReference<>(activity));
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                for (int i = activities.size() - 1; i >= 0; i--) {
                    Activity a = activities.get(i).get();
                    if (a == null || a == activity) {
                        activities.remove(i);
                    }
                }
            }

            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
        });
    }
    
    public static FontViewerApp getInstance() {
        return sInstance;
    }
    
    /**
     * إعادة بناء جميع الأنشطة الحية
     * تُستخدم عند تغيير الثيم أو الخط أو الإعدادات العامة التي تتطلب
     * إعادة رسم كامل للواجهة
     */
    public void recreateAllActivities() {
        for (WeakReference<Activity> ref : new ArrayList<>(activities)) {
            Activity act = ref.get();
            if (act != null && !act.isFinishing()) {
                act.recreate();
            }
        }
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

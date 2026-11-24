package com.oneui.fontviewer.ui.core.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import com.oneui.fontviewer.data.AppDataStore;
import com.oneui.fontviewer.data.settings.SettingsRepository;

import java.util.concurrent.TimeUnit;

public class ThemeHelper {

    private static final String TAG = "ThemeHelper";

    // أقصى مدة ننتظرها قراءة الثيم على الخيط الرئيسي أثناء onCreate.
    // بعد إعادة تشغيل الجهاز قد يتأخر القرص والخيوط بسبب إقلاع النظام، ولا يجوز أن يبقى الخيط الرئيسي
    // محجوباً بلا حد (وإلا ظهر ANR). عند انتهاء المهلة نبدأ بثيم النظام ثم نطبّق الثيم المحفوظ فور وصوله.
    private static final long THEME_READ_TIMEOUT_MS = 1500L;

    public static void applyTheme(Context context) {
        boolean isAuto = SettingsRepository.isThemeAuto(context);
        
        Log.d(TAG, "Applying theme - Auto mode: " + isAuto);
        
        if (isAuto) {
            Log.d(TAG, "Applying FOLLOW_SYSTEM mode");
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            int mode = SettingsRepository.getThemeMode(context);
            if (mode == SettingsRepository.THEME_DARK) {
                Log.d(TAG, "Applying DARK mode");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                Log.d(TAG, "Applying LIGHT mode");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
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
    public static void applyInitialTheme(AppDataStore appDataStore) {
        try {
            // قراءة إعداد الثيم التلقائي بشكل متزامن
            Boolean isAuto = appDataStore.getThemeAuto()
                    .timeout(THEME_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .blockingFirst();
            
            if (isAuto != null && isAuto) {
                // الثيم تلقائي - اتبع إعدادات النظام
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                Log.d(TAG, "✓ Theme set to FOLLOW_SYSTEM (Blocking Mode)");
            } else {
                // الثيم يدوي - اقرأ الوضع المحدد
                Integer themeMode = appDataStore.getThemeMode()
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
            applySavedThemeWhenReady(appDataStore);
        }
    }

    /**
     * يقرأ الثيم المحفوظ في خيط خلفي ثم يطبّقه على الخيط الرئيسي. يُستدعى فقط في الحالة النادرة التي
     * تأخرت فيها القراءة المتزامنة في applyInitialTheme عن المهلة المحددة.
     */
    private static void applySavedThemeWhenReady(AppDataStore appDataStore) {
        new Thread(() -> {
            try {
                Boolean isAuto = appDataStore.getThemeAuto().blockingFirst();
                Integer themeMode = appDataStore.getThemeMode().blockingFirst();

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
}

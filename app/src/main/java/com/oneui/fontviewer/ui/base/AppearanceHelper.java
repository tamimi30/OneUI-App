package com.oneui.fontviewer.ui.base;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.text.TextUtils;

import java.util.Locale;

import androidx.appcompat.app.AppCompatDelegate;
import com.oneui.fontviewer.data.settings.SettingsRepository;

public class AppearanceHelper {

    private static final String TAG = "AppearanceHelper";

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

    @SuppressWarnings("deprecation")
    public static Context wrapContext(Context context) {
        // من أندرويد 13 فما فوق، النظام (LocaleManager) هو من يضع لغة التطبيق في إعدادات كل شاشة.
        // فرض اللغة هنا من وراء النظام يجعل ما تعرضه الشاشة يختلف عمّا سجّله النظام لها، فإذا طابقت
        // اللغة الجديدة لاحقاً ما سجّله النظام (القديم) ظنّ أنه لا تغيير ولم يعد بناء الشاشة.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // لا نغيّر اللغة نفسها، لكن نصحّح اتجاه الواجهة فقط ليطابق لغة الإعدادات التي سلّمها النظام.
            // السبب: عند العودة إلى لغة النظام (setApplicationLocales بقائمة فارغة) يسلّم النظام
            // إعدادات لغتها عربية لكن اتجاهها ما زال LTR (يظهر في التقرير: locales=ar-MA ... dir=LTR).
            // اتجاه الواجهة مشتق من اللغة، فتصحيحه لا يجعل الشاشة تعرض لغة غير التي سجّلها النظام لها.
            Configuration current = context.getResources().getConfiguration();
            Locale configLocale = current.getLocales().get(0);
            int expectedDirection = TextUtils.getLayoutDirectionFromLocale(configLocale);
            if (current.getLayoutDirection() == expectedDirection) {
                return context;
            }
            Configuration fixed = new Configuration(current);
            fixed.setLayoutDirection(configLocale);
            return context.createConfigurationContext(fixed);
        }

        Locale locale = SettingsRepository.getLocale(context);
        Locale.setDefault(locale);
        
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        
        config.setLocale(locale);
        
        Log.d(TAG, "Context wrapped with locale: " + locale.getLanguage());
        
        return context.createConfigurationContext(config);
    }
    
    public static void initializeFromSettings(Context context) {
        applyTheme(context);
    }
}

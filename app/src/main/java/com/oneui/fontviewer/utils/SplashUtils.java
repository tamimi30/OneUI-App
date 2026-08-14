package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.View;

import androidx.core.splashscreen.SplashScreen;
import androidx.core.splashscreen.SplashScreenViewProvider;

public class SplashUtils {

    public static void configureSplashScreen(SplashScreen splashScreen, View root) {
        splashScreen.setOnExitAnimationListener(splashScreenViewProvider -> {
            View splashView = splashScreenViewProvider.getView();

            // 1. إعداد أنيميشن الخروج لشاشة البداية (مطابق لملف note_style_fragment_exit)
            ObjectAnimator splashAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    splashView,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.90f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.90f)
            );
            splashAnimator.setDuration(500);
            splashAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    splashScreenViewProvider.remove(); // إزالة شاشة البداية بعد انتهاء الحركة
                }
            });

            // 2. إعداد أنيميشن الدخول لشاشة التطبيق (مطابق لملف note_style_fragment_enter)
            if (root != null) {
                root.setAlpha(0f); // إخفاء التطبيق في البداية حتى تظهر الحركة بشكل سليم
            }
            
            ObjectAnimator contentAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    root,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.90f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.90f, 1f)
            );
            contentAnimator.setDuration(450);
            contentAnimator.setStartDelay(100);

            // 3. حساب وقت انتهاء حركة الأيقونة (كما في التطبيق الآخر بالضبط)
            long iconAnimDuration = splashScreenViewProvider.getIconAnimationDurationMillis();
            long iconAnimStart = splashScreenViewProvider.getIconAnimationStartMillis();
            long currentTime = System.currentTimeMillis();
            long remainingDuration = Math.max(0, iconAnimDuration - (currentTime - iconAnimStart));

            // 4. تشغيل الأنيميشن بانسيابية
            splashView.postDelayed(() -> {
                splashAnimator.start();
                if (root != null) {
                    contentAnimator.start();
                }
            }, remainingDuration);
        });
    }
}

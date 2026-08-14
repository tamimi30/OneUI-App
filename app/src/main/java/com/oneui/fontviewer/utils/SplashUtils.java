package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.core.splashscreen.SplashScreen;

public class SplashUtils {

    private static boolean isUIReady = false;
    private static long splashStartTime = 0L;

    // 1. تنصيب شاشة البداية وحفظ وقت البدء
    public static SplashScreen install(Activity activity) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(activity);
        splashStartTime = System.currentTimeMillis();
        return splashScreen;
    }

    // 2. إعداد المنطق بالكامل (التأخير + الأنيميشن)
    public static void setupSplashLogic(SplashScreen splashScreen, View root) {
        
        // [حل مشكلة الوميض]: إخفاء الواجهة فوراً بمجرد تمريرها قبل أن يتم رسمها على الشاشة
        if (root != null) {
            root.setAlpha(0f);
        }

        // إبقاء شاشة البداية ظاهرة حتى تصبح الواجهة جاهزة
        splashScreen.setKeepOnScreenCondition(() -> !isUIReady);

        // حساب وقت التأخير لضمان بقاء شاشة البداية الوقت المطلوب
        long elapsedTime = System.currentTimeMillis() - splashStartTime;
        long remainingDelay = Math.max(0L, 800L - elapsedTime);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isUIReady = true;
        }, remainingDelay);

        // إعداد أنيميشن الخروج
        splashScreen.setOnExitAnimationListener(splashScreenViewProvider -> {
            View splashView = splashScreenViewProvider.getView();

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
                    splashScreenViewProvider.remove();
                }
            });

            ObjectAnimator contentAnimator = null;
            if (root != null) {
                contentAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        root,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 0.90f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.90f, 1f)
                );
                contentAnimator.setDuration(450);
                contentAnimator.setStartDelay(100);
            }

            long iconAnimDuration = splashScreenViewProvider.getIconAnimationDurationMillis();
            long iconAnimStart = splashScreenViewProvider.getIconAnimationStartMillis();
            long currentTime = System.currentTimeMillis();
            long remainingIconDelay = Math.max(0, iconAnimDuration - (currentTime - iconAnimStart));

            final ObjectAnimator finalContentAnimator = contentAnimator;
            splashView.postDelayed(() -> {
                splashAnimator.start();
                if (finalContentAnimator != null) {
                    finalContentAnimator.start();
                }
            }, remainingIconDelay);
        });
    }
}

package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.core.splashscreen.SplashScreen;
import androidx.core.splashscreen.SplashScreenViewProvider;

public class SplashUtils {

    private static final long SPLASH_ANIMATION_DURATION_MS = 400L;
    private static final float SPLASH_SCALE_FACTOR = 1.2f;

    public static void configureSplashScreen(SplashScreen splashScreen, View root) {
        splashScreen.setOnExitAnimationListener(new SplashScreen.OnExitAnimationListener() {
            @Override
            public void onSplashScreenExit(SplashScreenViewProvider splashScreenViewProvider) {
                
                // 1. أنيميشن إخفاء وتكبير أيقونة Splash Screen
                ObjectAnimator splashAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        splashScreenViewProvider.getView(),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, SPLASH_SCALE_FACTOR),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, SPLASH_SCALE_FACTOR)
                );
                splashAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                splashAnimator.setDuration(SPLASH_ANIMATION_DURATION_MS);
                splashAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        splashScreenViewProvider.remove();
                    }
                });

                // 2. أنيميشن إظهار وتصغير محتوى التطبيق الأساسي
                ObjectAnimator contentAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        root,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, SPLASH_SCALE_FACTOR, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, SPLASH_SCALE_FACTOR, 1f)
                );
                contentAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                contentAnimator.setDuration(SPLASH_ANIMATION_DURATION_MS);

                // تشغيل الأنيميشن معاً
                splashAnimator.start();
                contentAnimator.start();
            }
        });
    }
}

package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.core.splashscreen.SplashScreen;
import androidx.core.splashscreen.SplashScreenViewProvider;

public class SplashUtils {

    public static void configureSplashScreen(SplashScreen splashScreen, View root) {
        splashScreen.setOnExitAnimationListener(new SplashScreen.OnExitAnimationListener() {
            @Override
            public void onSplashScreenExit(SplashScreenViewProvider splashScreenViewProvider) {
                View splashView = splashScreenViewProvider.getView();
                
                PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
                LinearInterpolator linearInterpolator = new LinearInterpolator();

                // --- 1. أنيميشن خروج أيقونة Splash ---
                ObjectAnimator splashAlpha = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f);
                splashAlpha.setInterpolator(linearInterpolator);
                splashAlpha.setDuration(100);

                ObjectAnimator splashScaleX = ObjectAnimator.ofFloat(splashView, View.SCALE_X, 1f, 0.90f);
                ObjectAnimator splashScaleY = ObjectAnimator.ofFloat(splashView, View.SCALE_Y, 1f, 0.90f);
                splashScaleX.setInterpolator(oneEasingInterpolator);
                splashScaleY.setInterpolator(oneEasingInterpolator);
                splashScaleX.setDuration(500);
                splashScaleY.setDuration(500);

                AnimatorSet splashAnimSet = new AnimatorSet();
                splashAnimSet.playTogether(splashAlpha, splashScaleX, splashScaleY);
                splashAnimSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        splashScreenViewProvider.remove();
                    }
                });

                // --------------------------------------------------------
                // الحل الجذري هنا: إخفاء وتصغير الشاشة فوراً قبل بدء التأخير لمنع الوميض
                root.setAlpha(0f);
                root.setScaleX(0.90f);
                root.setScaleY(0.90f);
                // --------------------------------------------------------

                // --- 2. أنيميشن دخول محتوى التطبيق ---
                ObjectAnimator contentAlpha = ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f);
                contentAlpha.setInterpolator(linearInterpolator);
                contentAlpha.setDuration(200);

                ObjectAnimator contentScaleX = ObjectAnimator.ofFloat(root, View.SCALE_X, 0.90f, 1f);
                ObjectAnimator contentScaleY = ObjectAnimator.ofFloat(root, View.SCALE_Y, 0.90f, 1f);
                contentScaleX.setInterpolator(oneEasingInterpolator);
                contentScaleY.setInterpolator(oneEasingInterpolator);
                contentScaleX.setDuration(450);
                contentScaleY.setDuration(450);

                AnimatorSet contentAnimSet = new AnimatorSet();
                contentAnimSet.playTogether(contentAlpha, contentScaleX, contentScaleY);
                contentAnimSet.setStartDelay(100); // التأخير الزمني المطلوب

                splashAnimSet.start();
                contentAnimSet.start();
            }
        });
    }
}

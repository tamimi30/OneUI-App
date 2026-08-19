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

                // الانتظار حتى تنتهي أنيميشن أيقونة الـ Splash فعلياً على الشاشة قبل التعامل معها
                long elapsed = System.currentTimeMillis() - splashScreenViewProvider.getIconAnimationStartMillis();
                long remainingDelay = Math.max(0L, splashScreenViewProvider.getIconAnimationDurationMillis() - elapsed);

                root.postDelayed(() -> startExitAnimation(splashScreenViewProvider, root), remainingDelay);
            }
        });
    }

    private static void startExitAnimation(SplashScreenViewProvider splashScreenViewProvider, View root) {
        // 1. جلب الخلفية لتطبيق التلاشي عليها فقط
        View splashView = splashScreenViewProvider.getView();

        // 2. جلب الأيقونة لتطبيق التصغير عليها لمنع تقلص الخلفية وظهور الإطار
        View splashIconView = splashScreenViewProvider.getIconView();

        PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
        LinearInterpolator linearInterpolator = new LinearInterpolator();

        // --- 1. أنيميشن خروج أيقونة Splash ---

        // الشفافية تُطبق على الخلفية بالكامل
        ObjectAnimator splashAlpha = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f);
        splashAlpha.setInterpolator(linearInterpolator);
        splashAlpha.setDuration(300);

        // التصغير يُطبق على الأيقونة فقط
        ObjectAnimator iconScaleX = ObjectAnimator.ofFloat(splashIconView, View.SCALE_X, 1f, 1f);
        ObjectAnimator iconScaleY = ObjectAnimator.ofFloat(splashIconView, View.SCALE_Y, 1f, 1f);
        iconScaleX.setInterpolator(oneEasingInterpolator);
        iconScaleY.setInterpolator(oneEasingInterpolator);
        iconScaleX.setDuration(100);
        iconScaleY.setDuration(100);

        AnimatorSet splashAnimSet = new AnimatorSet();
        splashAnimSet.playTogether(splashAlpha, iconScaleX, iconScaleY);
        splashAnimSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                splashScreenViewProvider.remove();
            }
        });

        // --------------------------------------------------------
        // إخفاء وتصغير المحتوى فوراً قبل بدء أنيميشن الدخول لمنع الوميض
     /* root.setAlpha(0f);
        root.setScaleX(0.80f);
        root.setScaleY(0.80f);*//
        // --------------------------------------------------------

        // --- 2. أنيميشن دخول محتوى التطبيق ---
        ObjectAnimator contentAlpha = ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f);
        contentAlpha.setInterpolator(linearInterpolator);
        contentAlpha.setDuration(500);

        ObjectAnimator contentScaleX = ObjectAnimator.ofFloat(root, View.SCALE_X, 0.80f, 1f);
        ObjectAnimator contentScaleY = ObjectAnimator.ofFloat(root, View.SCALE_Y, 0.80f, 1f);
        contentScaleX.setInterpolator(oneEasingInterpolator);
        contentScaleY.setInterpolator(oneEasingInterpolator);
        contentScaleX.setDuration(500);
        contentScaleY.setDuration(500);

        AnimatorSet contentAnimSet = new AnimatorSet();
        contentAnimSet.playTogether(contentAlpha, contentScaleX, contentScaleY);
        contentAnimSet.setStartDelay(100); // التأخير الزمني

        splashAnimSet.start();
        contentAnimSet.start();
    }
}

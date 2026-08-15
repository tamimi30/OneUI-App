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
                
                // تعريف منحنى الحركة المخصص (one_easing_interpolator)
                PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
                LinearInterpolator linearInterpolator = new LinearInterpolator();

                // --- 1. أنيميشن خروج أيقونة Splash (يطابق note_style_fragment_exit) ---
                
                // الشفافية (Alpha)
                ObjectAnimator splashAlpha = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f);
                splashAlpha.setInterpolator(linearInterpolator);
                splashAlpha.setDuration(100);

                // الحجم (Scale) يصغر لـ 0.90
                ObjectAnimator splashScaleX = ObjectAnimator.ofFloat(splashView, View.SCALE_X, 1f, 0.90f);
                ObjectAnimator splashScaleY = ObjectAnimator.ofFloat(splashView, View.SCALE_Y, 1f, 0.90f);
                splashScaleX.setInterpolator(oneEasingInterpolator);
                splashScaleY.setInterpolator(oneEasingInterpolator);
                splashScaleX.setDuration(500);
                splashScaleY.setDuration(500);

                // دمج وتشغيل حركات الخروج معاً
                AnimatorSet splashAnimSet = new AnimatorSet();
                splashAnimSet.playTogether(splashAlpha, splashScaleX, splashScaleY);
                splashAnimSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        splashScreenViewProvider.remove();
                    }
                });

                // --- 2. أنيميشن دخول محتوى التطبيق (يطابق note_style_fragment_enter) ---
                
                // الشفافية (Alpha)
                ObjectAnimator contentAlpha = ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f);
                contentAlpha.setInterpolator(linearInterpolator);
                contentAlpha.setDuration(200);

                // الحجم (Scale) يكبر من 0.90 إلى 1
                ObjectAnimator contentScaleX = ObjectAnimator.ofFloat(root, View.SCALE_X, 0.90f, 1f);
                ObjectAnimator contentScaleY = ObjectAnimator.ofFloat(root, View.SCALE_Y, 0.90f, 1f);
                contentScaleX.setInterpolator(oneEasingInterpolator);
                contentScaleY.setInterpolator(oneEasingInterpolator);
                contentScaleX.setDuration(450);
                contentScaleY.setDuration(450);

                // دمج وتشغيل حركات الدخول معاً (مع إضافة التأخير)
                AnimatorSet contentAnimSet = new AnimatorSet();
                contentAnimSet.playTogether(contentAlpha, contentScaleX, contentScaleY);
                contentAnimSet.setStartDelay(100); // تأخير البداية (startOffset)

                // تشغيل الأنيميشن
                splashAnimSet.start();
                contentAnimSet.start();
            }
        });
    }
}

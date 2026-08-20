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
                // 1. جلب الخلفية لتطبيق التلاشي عليها فقط
                View splashView = splashScreenViewProvider.getView();
                
                // 2. جلب الأيقونة لتطبيق التصغير عليها
                View splashIconView = splashScreenViewProvider.getIconView();

                // --- السر الجذري لمنع الوميض وتغير اللون تحت الضغط ---
                // تحويل الواجهات إلى Hardware Layers لتسريع الأنيميشن عبر GPU وعزله عن ضغط الـ CPU
                splashView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                splashIconView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                root.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                
                PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
                LinearInterpolator linearInterpolator = new LinearInterpolator();

                // --- 1. أنيميشن خروج أيقونة Splash ---
                
                // الشفافية تُطبق على الخلفية بالكامل
                ObjectAnimator splashAlpha = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f);
                splashAlpha.setInterpolator(linearInterpolator);
                splashAlpha.setDuration(500);

                // التصغير يُطبق على الأيقونة
                ObjectAnimator iconScaleX = ObjectAnimator.ofFloat(splashIconView, View.SCALE_X, 1f, 0.80f);
                ObjectAnimator iconScaleY = ObjectAnimator.ofFloat(splashIconView, View.SCALE_Y, 1f, 0.80f);
                iconScaleX.setInterpolator(oneEasingInterpolator);
                iconScaleY.setInterpolator(oneEasingInterpolator);
                iconScaleX.setDuration(500);
                iconScaleY.setDuration(500);

                AnimatorSet splashAnimSet = new AnimatorSet();
                // قمنا بإزالة شفافية الأيقونة المزدوجة التي كانت تزيد من مشكلة اللون
                splashAnimSet.playTogether(splashAlpha, iconScaleX, iconScaleY);
                splashAnimSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        splashScreenViewProvider.remove();
                    }
                });

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
                contentAnimSet.setStartDelay(200); 

                // إعادة LayerType إلى طبيعته بعد انتهاء الحركة لتحرير ذاكرة الـ GPU
                contentAnimSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        root.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                });

                // بدء الأنيميشن فوراً
                splashAnimSet.start();
                contentAnimSet.start();
            }
        });
    }
}

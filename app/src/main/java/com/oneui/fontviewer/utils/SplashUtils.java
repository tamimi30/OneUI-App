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
                
                // 2. جلب الأيقونة لتطبيق التصغير عليها لمنع تقلص الخلفية وظهور الإطار
                View splashIconView = splashScreenViewProvider.getIconView();
                
                PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
                LinearInterpolator linearInterpolator = new LinearInterpolator();

                // --- 1. أنيميشن خروج أيقونة Splash ---
                
                // الشفافية تُطبق على الخلفية بالكامل
                ObjectAnimator splashAlpha = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f);
                splashAlpha.setInterpolator(linearInterpolator);
                splashAlpha.setDuration(500);

                // الشفافية تُطبق صراحةً على الأيقونة أيضاً (هذا هو السر لمنع الوميض)
                ObjectAnimator iconAlpha = ObjectAnimator.ofFloat(splashIconView, View.ALPHA, 1f, 0f);
                iconAlpha.setInterpolator(linearInterpolator);
                iconAlpha.setDuration(500);

                // التصغير يُطبق على الأيقونة (تم تصحيح القيم من 1f إلى 0.80f لكي تعمل بشكل صحيح)
                ObjectAnimator iconScaleX = ObjectAnimator.ofFloat(splashIconView, View.SCALE_X, 1f, 0.80f);
                ObjectAnimator iconScaleY = ObjectAnimator.ofFloat(splashIconView, View.SCALE_Y, 1f, 0.80f);
                iconScaleX.setInterpolator(oneEasingInterpolator);
                iconScaleY.setInterpolator(oneEasingInterpolator);
                iconScaleX.setDuration(500);
                iconScaleY.setDuration(500);

                AnimatorSet splashAnimSet = new AnimatorSet();
                // دمج أنيميشن الشفافية للأيقونة مع باقي الحركات
                splashAnimSet.playTogether(splashAlpha, iconAlpha, iconScaleX, iconScaleY);
                splashAnimSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        splashScreenViewProvider.remove();
                    }
                });

                // تم نقل إخفاء الشاشة إلى MainActivity لمنع الوميض المبكر

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
                contentAnimSet.setStartDelay(200); // التأخير الزمني

                // بدء الأنيميشن فوراً دون انتظار النظام لتجنب الوميض تحت الضغط
                splashAnimSet.start();
                contentAnimSet.start();
            }
        });
    }
}

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
                
                PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
                LinearInterpolator linearInterpolator = new LinearInterpolator();

                // الحل الجذري لمنع الوميض تحت الضغط: 
                // نقل الأنيميشن بالكامل من مسار المعالج (UI Thread) إلى معالج الرسوميات (RenderThread)
                // باستخدام ViewPropertyAnimator بدلاً من ObjectAnimator و AnimatorSet المعقدة.

                // إيقاف أي حركة افتراضية مدمجة في الأيقونة (إذا كانت AnimatedVector)
                // وإجبارها على القفز للحالة النهائية فوراً لمنع وميض إعادة التشغيل.
                if (splashIconView instanceof android.widget.ImageView) {
                    android.graphics.drawable.Drawable drawable = ((android.widget.ImageView) splashIconView).getDrawable();
                    if (drawable != null) {
                        if (drawable instanceof android.graphics.drawable.Animatable) {
                            ((android.graphics.drawable.Animatable) drawable).stop();
                        }
                        drawable.jumpToCurrentState();
                    }
                }

                // --- 1. أنيميشن خروج أيقونة Splash ---
                // تمت إزالة withLayer() لمنع التأخير الزمني (Frame Drop) لحظة إنشاء الطبقة
                splashView.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .setInterpolator(linearInterpolator)
                        .withEndAction(() -> splashScreenViewProvider.remove())
                        .start();

                splashIconView.animate()
                        .scaleX(0.80f)
                        .scaleY(0.80f)
                        .setDuration(500)
                        .setInterpolator(oneEasingInterpolator)
                        .start();

                // --- 2. أنيميشن دخول محتوى التطبيق ---
                root.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500)
                        .setStartDelay(200)
                        .setInterpolator(oneEasingInterpolator)
                        .start();
            }
        });
    }
}

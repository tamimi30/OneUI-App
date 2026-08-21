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
        final boolean[] alreadyStarted = {false};

        splashScreen.setOnExitAnimationListener(new SplashScreen.OnExitAnimationListener() {
            @Override
            public void onSplashScreenExit(SplashScreenViewProvider splashScreenViewProvider) {

                if (alreadyStarted[0]) {
                    splashScreenViewProvider.remove();
                    return;
                }
                alreadyStarted[0] = true;

                View splashView = splashScreenViewProvider.getView();
                View splashIconView = splashScreenViewProvider.getIconView();

                // إلغاء أي أنيميشن سابق ما زال يعمل على هذه العناصر
                splashView.animate().cancel();
                splashIconView.animate().cancel();
                root.animate().cancel();

                // إعادة ضبط نقطة البداية قبل تشغيل الأنيميشن
                splashView.setAlpha(1f);
                splashIconView.setScaleX(1f);
                splashIconView.setScaleY(1f);

                PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
                LinearInterpolator linearInterpolator = new LinearInterpolator();

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

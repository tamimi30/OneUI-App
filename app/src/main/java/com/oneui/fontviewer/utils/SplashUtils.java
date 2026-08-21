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
        final boolean[] alreadyHandled = {false};

        splashScreen.setOnExitAnimationListener(new SplashScreen.OnExitAnimationListener() {
            @Override
            public void onSplashScreenExit(SplashScreenViewProvider splashScreenViewProvider) {

                // إذا نادى النظام هذه الدالة أكثر من مرة، تجاهل أي نداء إضافي
                // تماماً بدون أي تدخل (بدون remove، بدون أنيميشن جديد)
                if (alreadyHandled[0]) {
                    return;
                }
                alreadyHandled[0] = true;

                View splashView = splashScreenViewProvider.getView();
                View splashIconView = splashScreenViewProvider.getIconView();

                PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
                LinearInterpolator linearInterpolator = new LinearInterpolator();

                splashView.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .setInterpolator(linearInterpolator)
                        .withEndAction(() -> {
                            try {
                                splashScreenViewProvider.remove();
                            } catch (Exception ignored) {
                            }
                        })
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

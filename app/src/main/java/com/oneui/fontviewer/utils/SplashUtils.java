package com.oneui.fontviewer.utils;

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

                long iconStart = splashScreenViewProvider.getIconAnimationStartMillis();
                long iconDuration = splashScreenViewProvider.getIconAnimationDurationMillis();
                long remaining = 0L;
                if (iconStart > 0 && iconDuration > 0) {
                    long elapsed = System.currentTimeMillis() - iconStart;
                    remaining = Math.min(400L, Math.max(0L, iconDuration - elapsed));
                }

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    View splashView = splashScreenViewProvider.getView();
                    View splashIconView = splashScreenViewProvider.getIconView();

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
                }, remaining);
            }
        });
    }
}

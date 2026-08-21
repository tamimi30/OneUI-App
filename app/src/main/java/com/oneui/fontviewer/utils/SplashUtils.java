package com.oneui.fontviewer.utils;

import android.view.View;

import androidx.core.splashscreen.SplashScreen;
import androidx.core.splashscreen.SplashScreenViewProvider;

public class SplashUtils {

    public static void configureSplashScreen(SplashScreen splashScreen, View root) {
        splashScreen.setOnExitAnimationListener(splashScreenViewProvider -> {
            root.setAlpha(1f);
            root.setScaleX(1f);
            root.setScaleY(1f);
            try {
                splashScreenViewProvider.remove();
            } catch (Exception ignored) {
            }
        });
    }
}

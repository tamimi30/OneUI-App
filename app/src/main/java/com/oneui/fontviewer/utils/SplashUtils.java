package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;

public final class SplashUtils {

    // نفس أرقام note_style_fragment_exit.xml (اختفاء أيقونة شاشة البداية)
    private static final long EXIT_ALPHA_DURATION = 100L;
    private static final long EXIT_SCALE_DURATION = 500L;

    // نفس أرقام note_style_fragment_enter.xml (ظهور محتوى التطبيق)
    private static final long ENTER_START_OFFSET = 100L;
    private static final long ENTER_ALPHA_DURATION = 200L;
    private static final long ENTER_SCALE_DURATION = 450L;

    private static final float SCALE_MIN = 0.90f;

    private SplashUtils() {
    }

    public static void configureSplashScreen(@NonNull SplashScreen splashScreen, @NonNull View contentRoot) {

        contentRoot.setAlpha(0f);
        contentRoot.setScaleX(SCALE_MIN);
        contentRoot.setScaleY(SCALE_MIN);

        splashScreen.setOnExitAnimationListener(splashScreenView -> {

            PathInterpolator easing = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
            View splashView = splashScreenView.getView();

            ObjectAnimator splashAlpha = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f);
            splashAlpha.setDuration(EXIT_ALPHA_DURATION);
            splashAlpha.setInterpolator(new LinearInterpolator());

            ObjectAnimator splashScaleX = ObjectAnimator.ofFloat(splashView, View.SCALE_X, 1f, SCALE_MIN);
            ObjectAnimator splashScaleY = ObjectAnimator.ofFloat(splashView, View.SCALE_Y, 1f, SCALE_MIN);
            splashScaleX.setDuration(EXIT_SCALE_DURATION);
            splashScaleY.setDuration(EXIT_SCALE_DURATION);
            splashScaleX.setInterpolator(easing);
            splashScaleY.setInterpolator(easing);

            AnimatorSet exitSet = new AnimatorSet();
            exitSet.playTogether(splashAlpha, splashScaleX, splashScaleY);
            exitSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    splashScreenView.remove();
                }
            });

            ObjectAnimator contentAlpha = ObjectAnimator.ofFloat(contentRoot, View.ALPHA, 0f, 1f);
            contentAlpha.setDuration(ENTER_ALPHA_DURATION);
            contentAlpha.setInterpolator(new LinearInterpolator());

            ObjectAnimator contentScaleX = ObjectAnimator.ofFloat(contentRoot, View.SCALE_X, SCALE_MIN, 1f);
            ObjectAnimator contentScaleY = ObjectAnimator.ofFloat(contentRoot, View.SCALE_Y, SCALE_MIN, 1f);
            contentScaleX.setDuration(ENTER_SCALE_DURATION);
            contentScaleY.setDuration(ENTER_SCALE_DURATION);
            contentScaleX.setInterpolator(easing);
            contentScaleY.setInterpolator(easing);

            AnimatorSet enterSet = new AnimatorSet();
            enterSet.playTogether(contentAlpha, contentScaleX, contentScaleY);
            enterSet.setStartDelay(ENTER_START_OFFSET);

            exitSet.start();
            enterSet.start();
        });
    }
}

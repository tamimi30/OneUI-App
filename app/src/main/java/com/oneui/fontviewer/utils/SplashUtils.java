package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.core.splashscreen.SplashScreen;

/**
 * يتحكم في طريقة اختفاء شاشة السبلاش وظهور أول شاشة في التطبيق،
 * باستخدام نفس حركة "note style" المستخدمة في تبديل الشاشات
 * (note_style_fragment_enter.xml / note_style_fragment_exit.xml)
 * لكن مكتوبة مباشرة بالكود بدل تحميل ملفات anim.
 */
public class SplashUtils {

    // نفس المدة الموجودة في note_style_fragment_enter.xml / note_style_fragment_exit.xml
    private static final long ANIM_DURATION_MS = 500L;

    // نفس نسبة التكبير/التصغير الموجودة في نفس الملفين
    private static final float SCALE_SMALL = 0.90f;
    private static final float SCALE_NORMAL = 1f;

    // نفس منحنى الحركة الموجود في one_easing_interpolator.xml
    private static final PathInterpolator NOTE_EASING_INTERPOLATOR =
            new PathInterpolator(0.22f, 0.25f, 0f, 1f);

    public static void configureSplashScreen(SplashScreen splashScreen, View root) {
        splashScreen.setOnExitAnimationListener(splashScreenView -> {
            View splashIcon = splashScreenView.getView();

            // ---- اختفاء أيقونة السبلاش (نفس note_style_fragment_exit) ----
            ObjectAnimator splashFade = ObjectAnimator.ofFloat(splashIcon, View.ALPHA, 1f, 0f);
            splashFade.setInterpolator(new LinearInterpolator());
            splashFade.setDuration(ANIM_DURATION_MS);

            ObjectAnimator splashShrink = ObjectAnimator.ofPropertyValuesHolder(
                    splashIcon,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, SCALE_NORMAL, SCALE_SMALL),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, SCALE_NORMAL, SCALE_SMALL)
            );
            splashShrink.setInterpolator(NOTE_EASING_INTERPOLATOR);
            splashShrink.setDuration(ANIM_DURATION_MS);
            splashShrink.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    splashScreenView.remove();
                }
            });

            // ---- ظهور شاشة التطبيق (نفس note_style_fragment_enter) ----
            ObjectAnimator contentFade = ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f);
            contentFade.setInterpolator(new LinearInterpolator());
            contentFade.setDuration(ANIM_DURATION_MS);

            ObjectAnimator contentGrow = ObjectAnimator.ofPropertyValuesHolder(
                    root,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, SCALE_SMALL, SCALE_NORMAL),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, SCALE_SMALL, SCALE_NORMAL)
            );
            contentGrow.setInterpolator(NOTE_EASING_INTERPOLATOR);
            contentGrow.setDuration(ANIM_DURATION_MS);

            // ننتظر انتهاء أنيميشن الأيقونة الأصلي أولاً (نفس أسلوب التطبيق المرجعي)
            long elapsedSinceIconStart = System.currentTimeMillis() - splashScreenView.getIconAnimationStartMillis();
            long remainingDelay = Math.max(0L, splashScreenView.getIconAnimationDurationMillis() - elapsedSinceIconStart);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                splashFade.start();
                splashShrink.start();
                contentFade.start();
                contentGrow.start();
            }, remainingDelay);
        });
    }
}

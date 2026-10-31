package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.ref.WeakReference;

/**
 * ThemeTransition
 *
 * أنيميشن Crossfade عند تغيير الثيم (فاتح ⇄ داكن)، مطابق للأنيميشن في الفيديو:
 *
 *   1) قبل تغيير الثيم نلتقط صورة (Snapshot) للشاشة الحالية.
 *   2) نُطبّق الثيم، فيُعاد إنشاء الـ Activity بالثيم الجديد.
 *   3) في أول إطار للـ Activity الجديدة نضع الصورة القديمة فوقها، ثم نُخفيها
 *      تدريجياً (alpha 1 → 0)، فيظهر انتقال سلس بين الثيمين بدل القفزة المفاجئة.
 *
 * المنحنى والمدة تم استخراجهما من الفيديو بالقياس:
 *   - المنحنى: cubic-bezier(0.22, 0.25, 0, 1)  (نفس one_easing_interpolator في المشروع)
 *   - المدة   : ~600ms
 *
 * الاستخدام:
 *   - مرة واحدة في Application.onCreate():  ThemeTransition.init(this);
 *   - عند تغيير الثيم:
 *       ThemeTransition.apply(activity, AppCompatDelegate.MODE_NIGHT_YES, () -> {
 *           AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
 *       });
 */
public final class ThemeTransition {

    private static final String TAG = "ThemeTransition";

    /** انتظار قصير ليكتمل تحريك زر الراديو قبل بدء تبديل الثيم. غيّره أو اجعله 0 كما تريد. */
    public static final long APPLY_DELAY_MS = 250L;

    /** مدة التلاشي (مقاسة من الفيديو). */
    public static final long FADE_DURATION_MS = 600L;

    /** إذا لم يحدث إعادة إنشاء للـ Activity خلال هذه المدة نتخلص من الصورة المحفوظة. */
    private static final long PENDING_TIMEOUT_MS = 3000L;

    /** لضمان أن الصورة فوق أي عناصر أخرى داخل الـ DecorView (مثل خلفيات أشرطة النظام). */
    private static final float OVERLAY_Z = 10000f;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable CLEAR_PENDING = ThemeTransition::releasePending;

    private static boolean sInitialized;

    @Nullable private static Bitmap sSnapshot;
    @Nullable private static WeakReference<Activity> sSource;

    private ThemeTransition() {}

    /** يُستدعى مرة واحدة من Application.onCreate(). */
    public static void init(@NonNull Application app) {
        if (sInitialized) return;
        sInitialized = true;

        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                onResumed(activity);
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    /**
     * ينفّذ {@code applyTheme} (الذي يجب أن يستدعي setDefaultNightMode) مع أنيميشن الانتقال.
     * إذا لم يكن هناك تغيير فعلي في الثيم، أو كانت الأنيميشنات معطّلة من النظام،
     * يُنفَّذ {@code applyTheme} بدون أنيميشن.
     *
     * @param targetNightMode إحدى قيم AppCompatDelegate.MODE_NIGHT_YES / NO / FOLLOW_SYSTEM
     */
    public static void apply(@NonNull Activity activity,
                             int targetNightMode,
                             @NonNull Runnable applyTheme) {
        HANDLER.postDelayed(() -> {
            if (canAnimate(activity, targetNightMode)) {
                captureSnapshot(activity);
            }
            applyTheme.run();
        }, APPLY_DELAY_MS);
    }

    // ─────────────────────────────────────────────────────────────────────
    // الخطوة 1: التقاط صورة للشاشة الحالية
    // ─────────────────────────────────────────────────────────────────────

    private static boolean canAnimate(Activity activity, int targetNightMode) {
        if (activity.isFinishing() || activity.isDestroyed()) return false;
        if (!ValueAnimator.areAnimatorsEnabled()) return false;
        return isNightNow(activity) != willBeNight(targetNightMode);
    }

    private static boolean isNightNow(Activity activity) {
        int ui = activity.getResources().getConfiguration().uiMode;
        return (ui & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean willBeNight(int targetNightMode) {
        switch (targetNightMode) {
            case AppCompatDelegate.MODE_NIGHT_YES:
                return true;
            case AppCompatDelegate.MODE_NIGHT_NO:
                return false;
            default: {
                int ui = Resources.getSystem().getConfiguration().uiMode;
                return (ui & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            }
        }
    }

    private static void captureSnapshot(Activity activity) {
        releasePending();

        View decor = activity.getWindow().getDecorView();
        int w = decor.getWidth();
        int h = decor.getHeight();
        if (w <= 0 || h <= 0) return;

        try {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(bitmap));

            sSnapshot = bitmap;
            sSource = new WeakReference<>(activity);

            HANDLER.removeCallbacks(CLEAR_PENDING);
            HANDLER.postDelayed(CLEAR_PENDING, PENDING_TIMEOUT_MS);
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "Snapshot failed, theme will change without animation", e);
            releasePending();
        }
    }

    private static void releasePending() {
        Bitmap bitmap = sSnapshot;
        sSnapshot = null;
        sSource = null;
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // الخطوة 2: أول Activity جديدة تُستأنف بعد إعادة الإنشاء تعرض التلاشي
    // ─────────────────────────────────────────────────────────────────────

    private static void onResumed(Activity activity) {
        Bitmap snapshot = sSnapshot;
        if (snapshot == null) return;

        Activity source = sSource != null ? sSource.get() : null;

        // نفس النسخة القديمة عادت للأمام => لم تحدث إعادة إنشاء، ننتظر (أو ينتهي المؤقت)
        if (activity == source) return;
        // شاشة مختلفة تماماً عن التي التقطنا منها الصورة
        if (source != null && activity.getClass() != source.getClass()) return;

        sSnapshot = null;
        sSource = null;
        HANDLER.removeCallbacks(CLEAR_PENDING);

        new FadeOverlay(activity, snapshot).show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // الطبقة التي تحمل الصورة القديمة وتُخفيها تدريجياً
    // ─────────────────────────────────────────────────────────────────────

    private static final class FadeOverlay {

        private final ViewGroup decor;
        private final ImageView view;
        private final Bitmap bitmap;
        private final ObjectAnimator animator;
        private boolean released;

        FadeOverlay(Activity activity, Bitmap bitmap) {
            this.decor = (ViewGroup) activity.getWindow().getDecorView();
            this.bitmap = bitmap;

            view = new ImageView(activity);
            view.setImageBitmap(bitmap);
            view.setScaleType(ImageView.ScaleType.FIT_XY);
            view.setTranslationZ(OVERLAY_Z);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

            animator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f);
            animator.setDuration(FADE_DURATION_MS);
            animator.setInterpolator(new PathInterpolator(0.22f, 0.25f, 0f, 1f));
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { release(); }
                @Override public void onAnimationCancel(Animator a) { release(); }
            });
        }

        void show() {
            decor.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // نبدأ العدّ بعد رسم أول إطار فعلي، حتى لا يضيع جزء من الأنيميشن أثناء تحميل الشاشة
            view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ViewTreeObserver vto = view.getViewTreeObserver();
                    if (vto.isAlive()) vto.removeOnPreDrawListener(this);
                    animator.start();
                    return true;
                }
            });

            // إذا أُغلقت الـ Activity أثناء الأنيميشن
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(@NonNull View v) {}
                @Override public void onViewDetachedFromWindow(@NonNull View v) {
                    animator.cancel();
                    release();
                }
            });
        }

        private void release() {
            if (released) return;
            released = true;

            decor.removeView(view);
            view.setImageDrawable(null);
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }
}

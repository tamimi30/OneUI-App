package com.oneui.fontviewer.ui.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import androidx.core.splashscreen.SplashScreenViewProvider;

public final class TransitionUtils {

    private static final String TAG = "TransitionUtils";

    public enum AnimationType {

        NONE(0L, 0f, 0f, 1f, 1f),

        FADE(600L, 0.22f, 0.25f, 0f, 1f),

        WIPE_TOP_TO_BOTTOM(600L, 0.33f, 0f, 0.3f, 1f);

        final long durationMs;
        final float x1;
        final float y1;
        final float x2;
        final float y2;

        AnimationType(long durationMs, float x1, float y1, float x2, float y2) {
            this.durationMs = durationMs;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        PathInterpolator createInterpolator() {
            return new PathInterpolator(x1, y1, x2, y2);
        }
    }

    // تغيير أنيميشن الثيم
    public static final AnimationType THEME_ANIMATION = AnimationType.WIPE_TOP_TO_BOTTOM;

    // مدة أنيميشن تغيير الثيم بالميلي ثانية (مستقلة عن مدة اللغة)
    public static final long THEME_DURATION_MS = 600L;

    // تغيير أنيميشن اللغة
    public static final AnimationType LANGUAGE_ANIMATION = AnimationType.FADE;

    // مدة أنيميشن تغيير اللغة بالميلي ثانية (مستقلة عن مدة الثيم)
    public static final long LANGUAGE_DURATION_MS = 600L;

    // أنيميشن تغيير المجلد (عند ظهور قائمة الخطوط بعد اختيار مجلد جديد)
    public static final AnimationType FOLDER_ANIMATION = AnimationType.FADE;

    // مدة أنيميشن تغيير المجلد بالميلي ثانية
    public static final long FOLDER_DURATION_MS = 600L;

    // أنيميشن خروج شاشة Splash (ظهور التطبيق)
    public static final AnimationType SPLASH_ANIMATION = AnimationType.FADE;

    // مدة أنيميشن خروج شاشة Splash بالميلي ثانية
    public static final long SPLASH_DURATION_MS = 1200L;

    private static final long PENDING_TIMEOUT_MS = 3000L;

    private static Bitmap sSnapshot;
    private static AnimationType sAnimation;
    private static long sDurationMs;
    private static String sOwnerActivityName;
    private static int sOldNightFlag;
    private static String sOldLanguage;
    private static long sCaptureTime;

    private TransitionUtils() {}

    public static void prepareForThemeChange(Activity activity) {
        capture(activity, THEME_ANIMATION, THEME_DURATION_MS);
    }

    public static void prepareForLanguageChange(Activity activity) {
        capture(activity, LANGUAGE_ANIMATION, LANGUAGE_DURATION_MS);
    }

    private static void capture(Activity activity, AnimationType animation, long durationMs) {
        discardPending();

        if (activity == null || animation == AnimationType.NONE) {
            return;
        }

        View decor = activity.getWindow().getDecorView();
        int width = decor.getWidth();
        int height = decor.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            finishTouchEffects(decor);

            Bitmap snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(snapshot));

            sSnapshot = snapshot;
            sAnimation = animation;
            sDurationMs = durationMs;
            sOwnerActivityName = activity.getClass().getName();
            sOldNightFlag = getNightFlag(activity);
            sOldLanguage = getLanguage(activity);
            sCaptureTime = SystemClock.elapsedRealtime();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (sSnapshot == snapshot) {
                    discardPending();
                }
            }, PENDING_TIMEOUT_MS);
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.e(TAG, "Failed to capture snapshot", e);
            discardPending();
        }
    }

    public static void playIfPending(Activity activity) {
        if (sSnapshot == null || activity == null) {
            return;
        }

        if (!activity.getClass().getName().equals(sOwnerActivityName)) {
            return;
        }

        Bitmap snapshot = sSnapshot;
        AnimationType type = sAnimation;
        long durationMs = sDurationMs;
        int oldNightFlag = sOldNightFlag;
        String oldLanguage = sOldLanguage;
        boolean expired = SystemClock.elapsedRealtime() - sCaptureTime > PENDING_TIMEOUT_MS;
        clearPending();

        boolean changed = getNightFlag(activity) != oldNightFlag
                || !getLanguage(activity).equals(oldLanguage);

        if (expired || !changed || type == AnimationType.NONE || activity.isFinishing()) {
            snapshot.recycle();
            return;
        }

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        SnapshotOverlayView overlay = new SnapshotOverlayView(activity, snapshot, type, durationMs);
        decor.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.start();
    }

    /**
     * يعرض صورة اللقطة المنتظرة ثابتة (بدون أنيميشن) فوق الشاشة الحالية، ولا يستهلكها: تبقى
     * منتظرة لتُعرض بالأنيميشن في الشاشة الجديدة بعد إعادة البناء (playIfPending).
     * الطبقة تُزال تلقائياً عندما يعيد النظام بناء الشاشة.
     */
    public static void freezeScreen(Activity activity) {
        if (sSnapshot == null || activity == null
                || !activity.getClass().getName().equals(sOwnerActivityName)
                || SystemClock.elapsedRealtime() - sCaptureTime > PENDING_TIMEOUT_MS) {
            return;
        }

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(new SnapshotOverlayView(activity, sSnapshot, sAnimation, sDurationMs),
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * ★ تغيير جزء من الواجهة بأنيميشن، داخل نفس الشاشة (بدون إعادة بناء) ★
     *
     * للتغيير المتزامن (يحدث فوراً داخل Runnable):
     *
     *     TransitionUtils.run(recyclerView, TransitionUtils.AnimationType.FADE, () -> {
     *         adapter.submitList(newList);
     *     });
     *
     * @param target    الجزء الذي سيتغير (قائمة، حاوية، أو decorView للشاشة كاملة)
     * @param animation نوع الأنيميشن
     * @param change    الكود الذي يغيّر الواجهة
     */
    public static void run(View target, AnimationType animation, Runnable change) {
        run(target, animation, animation.durationMs, change);
    }

    /** نفس run لكن بمدة محددة (بالميلي ثانية) بدل المدة الافتراضية للنوع. */
    public static void run(View target, AnimationType animation, long durationMs, Runnable change) {
        Handle handle = begin(target, animation, durationMs);
        change.run();
        handle.play();
    }

    /**
     * ★ نفس الفكرة للتغيير غير المتزامن (مثل تحميل بيانات) على مرحلتين ★
     *
     *     // 1) قبل بدء التغيير: نلتقط صورة للشكل القديم (الشاشة تبقى حيّة ومتفاعلة)
     *     mTransition = TransitionUtils.begin(container, TransitionUtils.AnimationType.FADE);
     *
     *     // 2) عندما تصبح الواجهة الجديدة جاهزة (بعد تحديث الواجهة مباشرة): نشغّل الأنيميشن
     *     mTransition.play();
     *
     * إذا فشلت العملية أو أُلغيت نستدعي cancel() (أو نترك الكائن، فلا يتسرب شيء).
     */
    public static Handle begin(View target, AnimationType animation) {
        return begin(target, animation, animation.durationMs);
    }

    /** نفس begin لكن بمدة محددة (بالميلي ثانية) بدل المدة الافتراضية للنوع. */
    public static Handle begin(View target, AnimationType animation, long durationMs) {
        return new Handle(target, animation, durationMs);
    }

    /**
     * يلتقط صورة للشكل الحالي لأنيميشن تغيير المجلد (FOLDER_ANIMATION بمدة FOLDER_DURATION_MS).
     * بعد أن تصبح القائمة الجديدة جاهزة نستدعي play() على الكائن المُعاد.
     */
    public static Handle beginForFolderChange(View target) {
        return begin(target, FOLDER_ANIMATION, FOLDER_DURATION_MS);
    }

    public static final class Handle {

        private final View mTarget;
        private final AnimationType mAnimation;
        private final long mDurationMs;
        private final int[] mLocation = new int[2];
        private Bitmap mSnapshot;

        private Handle(View target, AnimationType animation, long durationMs) {
            mTarget = target;
            mAnimation = animation;
            mDurationMs = durationMs;

            if (target == null || animation == AnimationType.NONE
                    || target.getWidth() <= 0 || target.getHeight() <= 0) {
                return;
            }

            try {
                finishTouchEffects(target);
                target.getLocationInWindow(mLocation);

                Bitmap snapshot = Bitmap.createBitmap(
                        target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);
                target.draw(new Canvas(snapshot));
                mSnapshot = snapshot;
            } catch (RuntimeException | OutOfMemoryError e) {
                Log.e(TAG, "Failed to capture snapshot", e);
            }
        }

        /** يعرض صورة الشكل القديم فوق مكانها ويحرّكها لتظهر الواجهة الجديدة (مرة واحدة فقط). */
        @SuppressLint("RtlHardcoded")
        public void play() {
            Bitmap snapshot = mSnapshot;
            mSnapshot = null;

            if (snapshot == null || mTarget == null || !mTarget.isAttachedToWindow()
                    || !(mTarget.getRootView() instanceof FrameLayout)) {
                return;
            }

            // Gravity.LEFT (مطلق) وليس START: حتى يكون المكان صحيحاً في الواجهات RTL أيضاً
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    snapshot.getWidth(), snapshot.getHeight(), Gravity.TOP | Gravity.LEFT);
            params.leftMargin = mLocation[0];
            params.topMargin = mLocation[1];

            SnapshotOverlayView overlay =
                    new SnapshotOverlayView(mTarget.getContext(), snapshot, mAnimation, mDurationMs);
            ((FrameLayout) mTarget.getRootView()).addView(overlay, params);
            overlay.start();
        }

        /** يتخلى عن اللقطة دون تشغيل أي أنيميشن. */
        public void cancel() {
            mSnapshot = null;
        }
    }

    /**
     * ★ أنيميشن خروج شاشة Splash ★
     *
     * يُمرَّر كمستمع خروج في MainActivity:
     *
     *     splashScreen.setOnExitAnimationListener(TransitionUtils::playSplashExit);
     *
     * شاشة الـ Splash هنا عنصر حقيقي يعطيه النظام (وليس لقطة)، فنحرّكه هو نفسه بالنوع SPLASH_ANIMATION
     * والمدة SPLASH_DURATION_MS ثم نزيله، فيظهر التطبيق الجاهز تحته.
     */
    public static void playSplashExit(SplashScreenViewProvider provider) {
        final View view = provider.getView();
        final AnimationType type = SPLASH_ANIMATION;

        if (type == AnimationType.NONE || SPLASH_DURATION_MS <= 0
                || view.getWidth() <= 0 || view.getHeight() <= 0) {
            provider.remove();
            return;
        }

        final int width = view.getWidth();
        final int height = view.getHeight();
        final Rect clip = new Rect();
        final boolean[] removed = {false};

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SPLASH_DURATION_MS);
        animator.setInterpolator(type.createInterpolator());
        animator.addUpdateListener(a -> {
            float progress = (float) a.getAnimatedValue();
            switch (type) {
                case FADE:
                    view.setAlpha(1f - progress);
                    break;

                case WIPE_TOP_TO_BOTTOM:
                    clip.set(0, Math.round(height * progress), width, height);
                    view.setClipBounds(clip);
                    break;

                case NONE:
                default:
                    break;
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (removed[0]) {
                    return;
                }
                removed[0] = true;
                view.setClipBounds(null);
                provider.remove();
            }
        });
        animator.start();
    }

    private static void finishTouchEffects(View view) {
        view.jumpDrawablesToCurrentState();

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                finishTouchEffects(group.getChildAt(i));
            }
        }
    }

    // الوسم الكامل (مثل ar أو ar-MA) وليس رمز اللغة فقط، حتى يُعدّ التبديل بين ar و ar-MA تغييراً
    // فيُشغَّل الأنيميشن
    private static String getLanguage(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
    }

    private static int getNightFlag(Context context) {
        return context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
    }

    private static void clearPending() {
        sSnapshot = null;
        sAnimation = null;
        sDurationMs = 0L;
        sOwnerActivityName = null;
    }

    private static void discardPending() {
        Bitmap snapshot = sSnapshot;
        clearPending();
        if (snapshot != null) {
            snapshot.recycle();
        }
    }

    private static final class SnapshotOverlayView extends View {

        private final Rect mDst = new Rect();
        private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final AnimationType mType;
        private final long mDurationMs;

        private Bitmap mSnapshot;
        private float mProgress = 0f;
        private boolean mReleased = false;

        SnapshotOverlayView(Context context, Bitmap snapshot, AnimationType type, long durationMs) {
            super(context);
            mSnapshot = snapshot;
            mType = type;
            mDurationMs = durationMs;

            setWillNotDraw(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setZ(1000f);
        }

        void start() {
            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(mDurationMs);
            animator.setInterpolator(mType.createInterpolator());
            animator.addUpdateListener(a -> {
                mProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    release();
                }
            });

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {}

                @Override
                public void onViewDetachedFromWindow(View v) {
                    mReleased = true;
                    mSnapshot = null;
                    animator.cancel();
                }
            });

            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    if (!mReleased) {
                        animator.start();
                    }
                    return true;
                }
            });
        }

        private void release() {
            if (mReleased) {
                return;
            }
            mReleased = true;

            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                parent.removeView(this);
            }
            mSnapshot = null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mSnapshot == null) {
                return;
            }

            final int width = getWidth();
            final int height = getHeight();
            mDst.set(0, 0, width, height);

            switch (mType) {
                case FADE:
                    mPaint.setAlpha(Math.round(255f * (1f - mProgress)));
                    canvas.drawBitmap(mSnapshot, null, mDst, mPaint);
                    break;

                case WIPE_TOP_TO_BOTTOM:
                    mPaint.setAlpha(255);
                    canvas.save();
                    canvas.clipRect(0f, height * mProgress, width, height);
                    canvas.drawBitmap(mSnapshot, null, mDst, mPaint);
                    canvas.restore();
                    break;

                case NONE:
                default:
                    break;
            }
        }
    }
}

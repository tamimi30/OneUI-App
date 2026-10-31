package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/**
 * ThemeTransitionAnimator - أنيميشن تغيير الثيم (فاتح ⇄ داكن)
 *
 * ═══════════════════════════════════════════════════════════════════
 * ★ كيف تغيّر الأنيميشن؟ ★
 * ═══════════════════════════════════════════════════════════════════
 *
 * غيّر قيمة SELECTED_ANIMATION أدناه فقط (سطر واحد):
 *
 *   AnimationType.FADE               ← تلاشي متقاطع (الفيديو 1003358501.mp4)
 *   AnimationType.WIPE_TOP_TO_BOTTOM ← كشف الثيم الجديد من الأعلى للأسفل (الفيديو 1003358526.mp4)
 *   AnimationType.NONE               ← بدون أنيميشن (تبديل مباشر)
 *
 * ولتعديل مدة أي أنيميشن أو منحنى الحركة (Interpolator) عدّل قيمه داخل enum AnimationType.
 *
 * ═══════════════════════════════════════════════════════════════════
 * ★ فكرة العمل ★
 * ═══════════════════════════════════════════════════════════════════
 *
 * تغيير الثيم يعيد بناء الـ Activity (recreate)، لذلك:
 *   1) prepare()        : نلتقط صورة للشاشة بالثيم القديم قبل تغيير الثيم.
 *   2) playIfPending()  : بعد إعادة البناء نضع الصورة فوق الواجهة الجديدة ونحرّكها
 *                         (تلاشي / كشف) حتى تختفي وتظهر الواجهة الجديدة تحتها.
 */
public final class ThemeTransitionAnimator {

    private static final String TAG = "ThemeTransition";

    /**
     * أنواع الأنيميشن المتاحة.
     * كل نوع له: المدة بالمللي ثانية + منحنى الحركة (PathInterpolator: x1, y1, x2, y2).
     */
    public enum AnimationType {

        /** بدون أنيميشن */
        NONE(0L, 0f, 0f, 1f, 1f),

        /** تلاشي متقاطع بين الثيم القديم والجديد (منحنى One UI 0.22, 0.25, 0, 1) */
        FADE(600L, 0.22f, 0.25f, 0f, 1f),

        /** الثيم الجديد ينكشف من أعلى الشاشة إلى أسفلها بحافة حادة (منحنى Sine In-Out 70) */
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

    // ═══════════════════════════════════════════════════════════════════
    // ★★★ غيّر هذا السطر فقط لاختيار الأنيميشن ★★★
    // ═══════════════════════════════════════════════════════════════════
    public static final AnimationType SELECTED_ANIMATION = AnimationType.WIPE_TOP_TO_BOTTOM;

    /** أقصى وقت ننتظره بين التقاط الصورة وإعادة بناء الشاشة، بعده نتجاهل الصورة */
    private static final long PENDING_TIMEOUT_MS = 3000L;

    // حالة الصورة المنتظرة (تُستهلك مرة واحدة فقط)
    private static Bitmap sSnapshot;
    private static String sOwnerActivityName;
    private static int sOldNightFlag;
    private static long sCaptureTime;

    private ThemeTransitionAnimator() {}

    /**
     * يُستدعى قبل تغيير الثيم مباشرة (قبل AppCompatDelegate.setDefaultNightMode).
     * يلتقط صورة للشاشة الحالية بالثيم القديم.
     */
    public static void prepare(Activity activity) {
        discardPending();

        if (activity == null || SELECTED_ANIMATION == AnimationType.NONE) {
            return;
        }

        View decor = activity.getWindow().getDecorView();
        int width = decor.getWidth();
        int height = decor.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            Bitmap snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(snapshot));

            sSnapshot = snapshot;
            sOwnerActivityName = activity.getClass().getName();
            sOldNightFlag = getNightFlag(activity);
            sCaptureTime = SystemClock.elapsedRealtime();

            // تنظيف الصورة إذا لم تُستخدم (مثلاً: الثيم لم يتغير فعلياً فلم تُعاد الشاشة)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (sSnapshot == snapshot) {
                    discardPending();
                }
            }, PENDING_TIMEOUT_MS);
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.e(TAG, "Failed to capture theme snapshot", e);
            discardPending();
        }
    }

    /**
     * يُستدعى من BaseActivity.onPostCreate().
     * إذا كانت هناك صورة منتظرة لهذه الشاشة وتغيّر الثيم فعلاً، يشغّل الأنيميشن.
     */
    public static void playIfPending(Activity activity) {
        if (sSnapshot == null || activity == null) {
            return;
        }

        // الشاشات الأخرى في الخلفية (مثل MainActivity) تُعاد بناؤها أيضاً، نتجاهلها
        // ونترك الصورة للشاشة التي التُقطت منها (SettingsActivity)
        if (!activity.getClass().getName().equals(sOwnerActivityName)) {
            return;
        }

        Bitmap snapshot = sSnapshot;
        int oldNightFlag = sOldNightFlag;
        boolean expired = SystemClock.elapsedRealtime() - sCaptureTime > PENDING_TIMEOUT_MS;
        clearPending();

        AnimationType type = SELECTED_ANIMATION;
        boolean themeChanged = getNightFlag(activity) != oldNightFlag;

        if (expired || !themeChanged || type == AnimationType.NONE) {
            snapshot.recycle();
            return;
        }

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        SnapshotOverlayView overlay = new SnapshotOverlayView(activity, snapshot, type);
        decor.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.start();
    }

    private static int getNightFlag(Context context) {
        return context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
    }

    /** مسح الحالة المنتظرة (بدون recycle، تُستخدم عندما انتقلت الصورة لمرحلة أخرى) */
    private static void clearPending() {
        sSnapshot = null;
        sOwnerActivityName = null;
    }

    /** مسح الحالة المنتظرة وتحرير الصورة التي لم تُعرض أبداً */
    private static void discardPending() {
        Bitmap snapshot = sSnapshot;
        clearPending();
        if (snapshot != null) {
            snapshot.recycle();
        }
    }

    /**
     * طبقة تعرض صورة الثيم القديم فوق الواجهة الجديدة، وتحرّكها حسب نوع الأنيميشن.
     *
     * progress: 0 → 1  (0 = الثيم القديم ظاهر بالكامل، 1 = الثيم الجديد ظاهر بالكامل)
     */
    private static final class SnapshotOverlayView extends View {

        private final Rect mDst = new Rect();
        private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final AnimationType mType;

        private Bitmap mSnapshot;
        private float mProgress = 0f;
        private boolean mReleased = false;

        SnapshotOverlayView(Context context, Bitmap snapshot, AnimationType type) {
            super(context);
            mSnapshot = snapshot;
            mType = type;

            setWillNotDraw(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            // نضمن أن الطبقة فوق كل شيء (حتى فوق خلفيات الـ Status Bar / Navigation Bar)
            setZ(1000f);
        }

        void start() {
            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(mType.durationMs);
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

            // إذا أُغلقت الشاشة أثناء الأنيميشن نوقفه
            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {}

                @Override
                public void onViewDetachedFromWindow(View v) {
                    animator.cancel();
                }
            });

            // نبدأ الأنيميشن بعد رسم أول إطار (الصورة القديمة كاملة) حتى لا نفقد جزءاً من
            // المدة أثناء إعادة بناء الشاشة
            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    animator.start();
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
            // لا نستدعي recycle() هنا لأن آخر إطار قد يكون ما زال قيد الرسم
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
                    // الصورة القديمة تتلاشى تدريجياً فتظهر الواجهة الجديدة تحتها
                    mPaint.setAlpha(Math.round(255f * (1f - mProgress)));
                    canvas.drawBitmap(mSnapshot, null, mDst, mPaint);
                    break;

                case WIPE_TOP_TO_BOTTOM:
                    // حافة الكشف تنزل من الأعلى للأسفل، ويبقى من الصورة القديمة ما تحت الحافة فقط
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

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

public final class ThemeTransition {

    private static final String TAG = "ThemeTransition";

    public enum AnimationType {

        NONE(0L, 0f, 0f, 1f, 1f),

        FADE(800L, 0.22f, 0.25f, 0f, 1f),

        WIPE_TOP_TO_BOTTOM(800L, 0.33f, 0f, 0.3f, 1f);

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

    public static final AnimationType SELECTED_ANIMATION = AnimationType.FADE;

    private static final long PENDING_TIMEOUT_MS = 3000L;

    private static Bitmap sSnapshot;
    private static String sOwnerActivityName;
    private static int sOldNightFlag;
    private static long sCaptureTime;

    private ThemeTransition() {}

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
            finishTouchEffects(decor);

            Bitmap snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(snapshot));

            sSnapshot = snapshot;
            sOwnerActivityName = activity.getClass().getName();
            sOldNightFlag = getNightFlag(activity);
            sCaptureTime = SystemClock.elapsedRealtime();

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

    public static void playIfPending(Activity activity) {
        if (sSnapshot == null || activity == null) {
            return;
        }

        if (!activity.getClass().getName().equals(sOwnerActivityName)) {
            return;
        }

        Bitmap snapshot = sSnapshot;
        int oldNightFlag = sOldNightFlag;
        boolean expired = SystemClock.elapsedRealtime() - sCaptureTime > PENDING_TIMEOUT_MS;
        clearPending();

        AnimationType type = SELECTED_ANIMATION;
        boolean themeChanged = getNightFlag(activity) != oldNightFlag;

        if (expired || !themeChanged || type == AnimationType.NONE || activity.isFinishing()) {
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

    private static void finishTouchEffects(View view) {
        view.jumpDrawablesToCurrentState();

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                finishTouchEffects(group.getChildAt(i));
            }
        }
    }

    private static int getNightFlag(Context context) {
        return context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
    }

    private static void clearPending() {
        sSnapshot = null;
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

        private Bitmap mSnapshot;
        private float mProgress = 0f;
        private boolean mReleased = false;

        SnapshotOverlayView(Context context, Bitmap snapshot, AnimationType type) {
            super(context);
            mSnapshot = snapshot;
            mType = type;

            setWillNotDraw(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
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

package com.oneui.fontviewer.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

public class SelectionModeAnimator implements View.OnAttachStateChangeListener {

    public static final long SLIDE_DURATION = 350L;
    public static final long FADE_DURATION  = 350L;

    private static final long TOTAL_DURATION = Math.max(SLIDE_DURATION, FADE_DURATION);

    private static final TimeInterpolator SLIDE_INTERPOLATOR = new DecelerateInterpolator();
    private static final TimeInterpolator FADE_INTERPOLATOR  = new AccelerateDecelerateInterpolator();

    private static final int MAX_LAYOUT_WAIT_PASSES = 3;

    private final ViewGroup container;
    private final View checkBox;

    private PendingStart pendingStart;
    private ValueAnimator animator;

    private BitmapDrawable ghost;

    private float currentFade = 1f;

    private View[] activeMovers;
    private float[] activeDeltas;
    private int activeCount;
    private boolean activeEntering;
    private float fadeFrom;
    private float fadeTo;

    public SelectionModeAnimator(@NonNull ViewGroup container, @NonNull View checkBox) {
        this.container = container;
        this.checkBox = checkBox;
        container.addOnAttachStateChangeListener(this);
    }

    public void start(boolean entering) {
        if (!canAnimate()) {
            cancel();
            checkBox.setVisibility(entering ? View.VISIBLE : View.GONE);
            return;
        }

        final boolean busy = isBusy();
        final float startFade = busy ? currentFade : (entering ? 0f : 1f);

        final int childCount = container.getChildCount();
        final View[] movers = new View[childCount];
        final float[] firstLeft = new float[childCount];
        final float[] firstRight = new float[childCount];
        int moverCount = 0;
        for (int i = 0; i < childCount; i++) {
            final View child = container.getChildAt(i);
            if (child == checkBox || child.getVisibility() == View.GONE) continue;
            movers[moverCount] = child;
            firstLeft[moverCount] = child.getLeft() + child.getTranslationX();
            firstRight[moverCount] = child.getRight() + child.getTranslationX();
            moverCount++;
        }

        cancel();

        if (entering) {
            checkBox.setAlpha(startFade);
            checkBox.setVisibility(View.VISIBLE);
        } else {
            addGhost(startFade);
            checkBox.setVisibility(View.GONE);
        }
        currentFade = startFade;

        pendingStart = new PendingStart(entering, movers, firstLeft, firstRight, moverCount, startFade);
        container.getViewTreeObserver().addOnPreDrawListener(pendingStart);
    }

    public void cancel() {
        if (!isBusy()) return;

        if (pendingStart != null) {
            pendingStart.detach();
            pendingStart = null;
        }
        if (animator != null) {
            final ValueAnimator running = animator;
            animator = null;
            running.removeAllUpdateListeners();
            running.removeAllListeners();
            running.cancel();
        }
        resetVisuals();
    }

    public boolean isBusy() {
        return animator != null || pendingStart != null;
    }

    private boolean canAnimate() {
        return container.isAttachedToWindow()
                && container.getWidth() > 0
                && container.getHeight() > 0
                && container.getViewTreeObserver().isAlive();
    }

    private void begin(@NonNull PendingStart p) {
        final float[] deltas = new float[p.count];
        for (int i = 0; i < p.count; i++) {
            final View child = p.movers[i];
            final float dLeft = p.firstLeft[i] - child.getLeft();
            final float dRight = p.firstRight[i] - child.getRight();
            deltas[i] = Math.abs(dLeft) >= Math.abs(dRight) ? dLeft : dRight;
        }

        activeMovers = p.movers;
        activeDeltas = deltas;
        activeCount = p.count;
        activeEntering = p.entering;
        fadeFrom = p.startFade;
        fadeTo = p.entering ? 1f : 0f;

        final ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(TOTAL_DURATION);
        a.setInterpolator(new LinearInterpolator());
        animator = a;

        apply(0f);

        a.addUpdateListener(va -> {
            if (animator == va) apply(va.getAnimatedFraction());
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                if (animator == an) {
                    animator = null;
                    resetVisuals();
                }
            }
        });
        a.start();
    }

    private void apply(float fraction) {
        final float elapsed = fraction * TOTAL_DURATION;
        final float slide = SLIDE_INTERPOLATOR.getInterpolation(Math.min(1f, elapsed / SLIDE_DURATION));
        final float fade = FADE_INTERPOLATOR.getInterpolation(Math.min(1f, elapsed / FADE_DURATION));

        for (int i = 0; i < activeCount; i++) {
            activeMovers[i].setTranslationX(activeDeltas[i] * (1f - slide));
        }

        currentFade = fadeFrom + (fadeTo - fadeFrom) * fade;
        if (activeEntering) {
            checkBox.setAlpha(currentFade);
        } else if (ghost != null) {
            ghost.setAlpha(Math.round(currentFade * 255f));
        }
    }

    private void addGhost(float fade) {
        final int w = checkBox.getWidth();
        final int h = checkBox.getHeight();
        if (checkBox.getVisibility() != View.VISIBLE || w <= 0 || h <= 0) return;

        final Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        checkBox.draw(new Canvas(bitmap));

        ghost = new BitmapDrawable(container.getResources(), bitmap);
        ghost.setBounds(checkBox.getLeft(), checkBox.getTop(), checkBox.getRight(), checkBox.getBottom());
        ghost.setAlpha(Math.round(fade * 255f));
        container.getOverlay().add(ghost);
    }

    private void removeGhost() {
        if (ghost != null) {
            container.getOverlay().remove(ghost);
            ghost = null;
        }
    }

    private void resetVisuals() {
        for (int i = 0, n = container.getChildCount(); i < n; i++) {
            container.getChildAt(i).setTranslationX(0f);
        }
        checkBox.setAlpha(1f);
        removeGhost();
        activeMovers = null;
        activeDeltas = null;
        activeCount = 0;
    }

    @Override
    public void onViewAttachedToWindow(View v) {
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        cancel();
    }

    private final class PendingStart implements ViewTreeObserver.OnPreDrawListener {
        final boolean entering;
        final View[] movers;
        final float[] firstLeft;
        final float[] firstRight;
        final int count;
        final float startFade;
        private int waitedPasses = 0;

        PendingStart(boolean entering, View[] movers, float[] firstLeft, float[] firstRight,
                     int count, float startFade) {
            this.entering = entering;
            this.movers = movers;
            this.firstLeft = firstLeft;
            this.firstRight = firstRight;
            this.count = count;
            this.startFade = startFade;
        }

        @Override
        public boolean onPreDraw() {
            if (pendingStart != this) {
                detach();
                return true;
            }
            if (container.isLayoutRequested() && ++waitedPasses < MAX_LAYOUT_WAIT_PASSES) {
                return true;
            }
            detach();
            pendingStart = null;
            begin(this);
            return true;
        }

        void detach() {
            final ViewTreeObserver observer = container.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(this);
        }
    }
}

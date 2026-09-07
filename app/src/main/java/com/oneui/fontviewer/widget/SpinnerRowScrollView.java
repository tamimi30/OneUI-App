package com.oneui.fontviewer.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;

public class SpinnerRowScrollView extends HorizontalScrollView {

    private static final float MICRO_SLOP_DP = 3.5f;
    private static final float VERTICAL_RELEASE_SLOP_DP = 3.5f;
    // معامل الحسم الاتجاهي: يجب أن تكون الحركة العمودية أكبر من الأفقية
    // بهذا المعامل (وليس فقط أكبر بقليل كما كان سابقاً عند dy>=dx وهو 45
    // درجة) لتُعتبر "عمودية بوضوح". كلما زاد الرقم، كلما اقترب خط الفصل
    // من الخط العمودي المستقيم (90 درجة)، فيتطلب ميلاً أقرب للعمودي التام،
    // مما يمنع سحب أفقي سريع منحرف قليلاً من تفعيل السكرول العمودي بالخطأ.
    // 2.0 يعني: يجب أن تكون الزاوية أكبر من ~63 درجة عن الأفقي (بدل 45).
    private static final float VERTICAL_DOMINANCE_RATIO = 2.0f;

    private final float microSlopPx;
    private final float verticalReleaseSlopPx;
    private float downX;
    private float downY;
    private boolean verticalReleased;

    public SpinnerRowScrollView(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        microSlopPx = MICRO_SLOP_DP * density;
        verticalReleaseSlopPx = VERTICAL_RELEASE_SLOP_DP * density;
    }

    public SpinnerRowScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        microSlopPx = MICRO_SLOP_DP * density;
        verticalReleaseSlopPx = VERTICAL_RELEASE_SLOP_DP * density;
    }

    public SpinnerRowScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        microSlopPx = MICRO_SLOP_DP * density;
        verticalReleaseSlopPx = VERTICAL_RELEASE_SLOP_DP * density;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = ev.getX();
                downY = ev.getY();
                verticalReleased = false;
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);
                if (dx > microSlopPx && dx > dy) {
                    return true;
                }
                checkVerticalRelease(ev);
                break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            checkVerticalRelease(ev);
        }
        return super.onTouchEvent(ev);
    }

    private void checkVerticalRelease(MotionEvent ev) {
        if (verticalReleased) return;
        float dx = Math.abs(ev.getX() - downX);
        float dy = Math.abs(ev.getY() - downY);
        if (dy > verticalReleaseSlopPx && dy > dx * VERTICAL_DOMINANCE_RATIO) {
            verticalReleased = true;
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(false);
            }
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // نتجاهل طلب الـ Spinner الداخلي بعدم الاعتراض، حتى تبقى هذه الحاوية
        // قادرة على التقاط السحب الأفقي حتى لو بدأ فوق الـ Spinner نفسه.
    }
}

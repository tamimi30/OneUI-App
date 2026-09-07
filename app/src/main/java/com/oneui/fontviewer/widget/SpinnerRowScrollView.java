package com.oneui.fontviewer.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;

public class SpinnerRowScrollView extends HorizontalScrollView {

    private static final float MICRO_SLOP_DP = 3.5f;
    // خُفّضت من 16f الى 4f: كلما كانت هذه العتبة أصغر، كلما اعتبرنا الحركة
    // "عمودية" وحررنا السيطرة لـ NestedScrollView بشكل أسرع، مما يقلل فرصة
    // فوز مؤقت الـ Spinner الداخلي بالسباق ويمنع فتح القائمة أصلاً بدل أن
    // تُفتح ثم تُغلق.
    private static final float VERTICAL_RELEASE_SLOP_DP = 4f;

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
        // dy >= dx (وليس dy > dx فقط): عند التساوي التقريبي نميل لصالح
        // اعتبارها عمودية، لأن التحرير المبكر آمن دائماً كما شُرح أعلاه.
        if (dy > verticalReleaseSlopPx && dy >= dx) {
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

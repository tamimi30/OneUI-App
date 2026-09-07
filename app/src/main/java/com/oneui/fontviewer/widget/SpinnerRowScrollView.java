package com.oneui.fontviewer.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;

public class SpinnerRowScrollView extends HorizontalScrollView {

    private static final float MICRO_SLOP_DP = 3.5f;
    private static final float VERTICAL_RELEASE_SLOP_DP = 40f;

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
                if (dx > microSlopPx && dx > Math.abs(ev.getY() - downY)) {
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
        // تُستدعى دائماً حتى عندما لا يستهلك أي عنصر داخلي اللمسة (نص، فاصل،
        // خلفية)، وفي هذه الحالة تحديداً لا تُستدعى onInterceptTouchEvent مع
        // كل حركة، لذلك نكرر نفس فحص "تحرير السكرول العمودي" هنا أيضاً.
        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            checkVerticalRelease(ev);
        }
        return super.onTouchEvent(ev);
    }

    private void checkVerticalRelease(MotionEvent ev) {
        if (verticalReleased) return;
        float dx = Math.abs(ev.getX() - downX);
        float dy = Math.abs(ev.getY() - downY);
        if (dy > verticalReleaseSlopPx && dy > dx) {
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

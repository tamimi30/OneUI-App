package com.oneui.fontviewer.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

public class SpinnerRowScrollView extends HorizontalScrollView {

    private static final float MICRO_SLOP_DP = 3.5f;

    private final float microSlopPx;
    private float downX;
    private float downY;

    public SpinnerRowScrollView(Context context) {
        super(context);
        microSlopPx = MICRO_SLOP_DP * context.getResources().getDisplayMetrics().density;
    }

    public SpinnerRowScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        microSlopPx = MICRO_SLOP_DP * context.getResources().getDisplayMetrics().density;
    }

    public SpinnerRowScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        microSlopPx = MICRO_SLOP_DP * context.getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);
                if (dx > microSlopPx && dx > dy) {
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // نتجاهل هذا الطلب عمداً — راجع الشرح أعلاه.
    }
}

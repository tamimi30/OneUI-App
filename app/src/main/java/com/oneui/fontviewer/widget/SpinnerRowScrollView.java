package com.oneui.fontviewer.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;

public class SpinnerRowScrollView extends HorizontalScrollView {

    // عتبة صغيرة جداً لالتقاط السحب الأفقي فوراً (حتى لو بطيء) قبل فتح الـ Spinner
    private static final float MICRO_SLOP_DP = 3.5f;
    // عتبة أكبر: لا نُحرر السكرول العمودي إلا بعد سحب عمودي واضح فعلاً
    // (هذا هو "تخفيف الحساسية" الذي طلبته، وليس إلغاء للسكرول العمودي)
    private static final float VERTICAL_RELEASE_SLOP_DP = 16f;

    private final float microSlopPx;
    private final float verticalReleaseSlopPx;
    private float downX;
    private float downY;

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
                // نقول لكل الحاويات الأب (بما فيها NestedScrollView العمودي):
                // لا تقرروا بأنفسكم إن كانت هذه اللمسة أفقية أو عمودية الآن،
                // نحن سنقرر أولاً.
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
                    // سحب أفقي واضح: نستولي عليه نحن
                    return true;
                }

                if (dy > verticalReleaseSlopPx && dy > dx) {
                    // سحب عمودي واضح (تجاوز عتبة أكبر عمداً): نُحرر اللمسة
                    // ليتصرف الـ NestedScrollView بشكل طبيعي تماماً من الآن
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(false);
                    }
                }
                break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // نتجاهل طلب الـ Spinner الداخلي بعدم الاعتراض، حتى تبقى هذه الحاوية
        // قادرة على التقاط السحب الأفقي حتى لو بدأ فوق الـ Spinner نفسه.
    }
}

package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;

public class OneUiHorizontalScrollView extends HorizontalScrollView {

    private final Rect mTempFocusRect = new Rect();

    public OneUiHorizontalScrollView(Context context) {
        super(context);
    }

    public OneUiHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OneUiHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // نتجاهل طلب الـ Spinner بمنعنا من الاعتراض،
        // حتى نبقى قادرين على اكتشاف السكرول البطيء وسرقته منه.
    }

    // ★ الإصلاح: نعيد تنفيذ سلوك "أظهر العنصر المُركَّز بالكامل" يدوياً (تمرير أفقي محلي فقط)،
    // لكن دون تمرير طلب التركيز إلى الأب (super.requestChildFocus). تمريره للأعلى هو ما كان
    // يجعل الـ CoordinatorLayout يُبلغ سلوك الـ AppBarLayout بطلب إظهار هذا العنصر،
    // فينطوي الـ CollapsingToolbar لإفساح المجال رأسياً رغم أن الأمر يخص سكرول أفقي فقط.
    @Override
    public void requestChildFocus(View child, View focused) {
        if (focused != null) {
            focused.getDrawingRect(mTempFocusRect);
            offsetDescendantRectToMyCoords(focused, mTempFocusRect);
            int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempFocusRect);
            if (scrollDelta != 0) {
                scrollBy(scrollDelta, 0);
            }
        }
        // تعمّداً: لا نستدعي super.requestChildFocus(child, focused) هنا.
    }
}

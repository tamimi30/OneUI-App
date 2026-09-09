package com.oneui.fontviewer.widget; // ⚠️ استبدل هذا باسم الـ package الفعلي في مشروعك

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;

public class OneUiHorizontalScrollView extends HorizontalScrollView {

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

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        // ننفذ السكرول الافقي الطبيعي (اظهار الـ Spinner بالكامل) كما يحدث دائماً
        boolean scrolled = super.requestChildRectangleOnScreen(child, rectangle, immediate);

        // نمنع هذا الطلب من "تلويث" الآباء الاعلى (NestedScrollView ثم CoordinatorLayout)
        // بمستطيل قد يجعلهم يعتقدون ان الهدف غير ظاهر بالكامل فيطوون CollapsingToolbar.
        rectangle.set(0, 0, 1, 1);

        return scrolled;
    }
}

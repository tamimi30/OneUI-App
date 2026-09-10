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
        // اسمح للسكرول الأفقي يعمل شغله الطبيعي (يحرك نفسه لإظهار العنصر كامل).
        boolean scrolled = super.requestChildRectangleOnScreen(child, rectangle, immediate);

        // بعدها امنع الطلب من الإيحاء للأعلى (نحو الـ CollapsingToolbar) بأن
        // في جزء غير ظاهر، بتصغير المستطيل لنقطة صغيرة قريبة من الأعلى تكون
        // ظاهرة دايماً، فما يعود في مبرر للطي.
        rectangle.set(0, 0, 1, 1);

        return scrolled;
    }
}

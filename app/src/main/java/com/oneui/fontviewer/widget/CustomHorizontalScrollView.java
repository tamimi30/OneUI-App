package com.oneui.fontviewer.widget; // ⚠️ استبدل هذا باسم الـ package الفعلي في مشروعك

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

public class CustomHorizontalScrollView extends HorizontalScrollView {

    public CustomHorizontalScrollView(Context context) {
        super(context);
    }

    public CustomHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // نتجاهل طلب الـ Spinner بمنعنا من الاعتراض،
        // حتى نبقى قادرين على اكتشاف السكرول البطيء وسرقته منه.
    }
}

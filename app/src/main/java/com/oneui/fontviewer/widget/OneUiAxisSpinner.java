package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;

import androidx.appcompat.widget.AppCompatSpinner;

public class OneUiAxisSpinner extends AppCompatSpinner {

    public OneUiAxisSpinner(Context context) {
        super(context);
    }

    public OneUiAxisSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OneUiAxisSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // نعيد بناء نفس آلية Android الافتراضية في "أظهرني على الشاشة"،
    // لكن نوقفها فور وصولها إلى OneUiHorizontalScrollView (الذي يمرر
    // السكرول الأفقي لإظهارنا كاملين - وهذا سلوك نريد الإبقاء عليه)،
    // بدل تركها تتابع صعودها لتصل إلى CoordinatorLayout وتطوي الـ Toolbar.
    @Override
    public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) {
        ViewParent parent = getParent();
        if (parent == null) {
            return false;
        }

        View child = this;
        boolean scrolled = false;

        while (parent != null) {
            scrolled |= parent.requestChildRectangleOnScreen(child, rectangle, immediate);

            if (parent instanceof OneUiHorizontalScrollView || !(parent instanceof View)) {
                break;
            }

            View parentView = (View) parent;
            rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
            child = parentView;
            parent = parentView.getParent();
        }

        return scrolled;
    }
}

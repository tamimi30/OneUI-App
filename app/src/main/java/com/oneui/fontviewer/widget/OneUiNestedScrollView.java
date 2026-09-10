package com.oneui.fontviewer.widget; // ⚠️ استخدم نفس الـ package الموجود في CustomHorizontalScrollView.java بالضبط

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.widget.NestedScrollView;

public class OneUiNestedScrollView extends NestedScrollView {

    private float mDownX;
    private float mDownY;
    private final int mTouchSlop;

    public OneUiNestedScrollView(Context context) {
        this(context, null);
    }

    public OneUiNestedScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public OneUiNestedScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getX() - mDownX);
                float dy = Math.abs(ev.getY() - mDownY);

                // إذا كانت الحركة أفقية بوضوح، لا تعترض اللمسة إطلاقاً
                // ودع الأبناء (CustomHorizontalScrollView أو الـ Spinner) يتصرفون بها
                if (dx > mTouchSlop && dx > dy) {
                    return false;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        boolean scrolled = super.requestChildRectangleOnScreen(child, rectangle, immediate);
        rectangle.set(0, 0, 1, 1);
        return scrolled;
    }
}

package com.oneui.fontviewer.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

public class OneUiTextView extends AppCompatTextView {

    public OneUiTextView(Context context) {
        super(context);
        init(context, null);
    }

    public OneUiTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public OneUiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        OneUiTextHelper.applyFontLevel(this, context, attrs);
    }
}

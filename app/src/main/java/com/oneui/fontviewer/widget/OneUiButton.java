package com.oneui.fontviewer.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatButton;

import com.oneui.fontviewer.R;

public class OneUiButton extends AppCompatButton {

    public OneUiButton(Context context) {
        super(context);
        init(context, null);
    }

    public OneUiButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public OneUiButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        OneUiTextHelper.applyFontLevel(this, context, attrs);
    }
}

package com.oneui.fontviewer.ui.core.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;
import com.oneui.fontviewer.ui.core.widget.textview.OneUiTextHelper;


public class OneUiEditText extends AppCompatEditText {

    public OneUiEditText(Context context) {
        super(context);
        init(context, null);
    }

    public OneUiEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public OneUiEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        OneUiTextHelper.applyFontLevel(this, context, attrs);
    }
}

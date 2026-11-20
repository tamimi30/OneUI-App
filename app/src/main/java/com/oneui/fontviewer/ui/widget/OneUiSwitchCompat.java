package com.oneui.fontviewer.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.SwitchCompat;
import com.oneui.fontviewer.ui.widget.textview.OneUiTextHelper;


public class OneUiSwitchCompat extends SwitchCompat {

    public OneUiSwitchCompat(Context context) {
        super(context);
        init(context, null);
    }

    public OneUiSwitchCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public OneUiSwitchCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        OneUiTextHelper.applyFontLevel(this, context, attrs);
    }
}

package com.example.oneuiapp.widget; // تأكد من مسار البكج

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.appcompat.widget.AppCompatButton;
import com.example.oneuiapp.R; // استدعاء ملف R الخاص بمشروعك

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

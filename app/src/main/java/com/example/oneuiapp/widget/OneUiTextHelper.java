package com.example.oneuiapp.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;
import com.example.oneuiapp.R;

public class OneUiTextHelper {

    public static void applyFontLevel(TextView textView, Context context, AttributeSet attrs) {
        // 1. استخراج الرقم المجرد لحجم الخط من XML
        int[] textSizeAttr = new int[]{android.R.attr.textSize};
        TypedArray aText = context.obtainStyledAttributes(attrs, textSizeAttr);
        float rawTextSize = -1f;
        TypedValue peekValue = aText.peekValue(0);
        if (peekValue != null) {
            rawTextSize = TypedValue.complexToFloat(peekValue.data);
        }
        aText.recycle();

        // 2. استخراج حد التكبير والحد الأدنى من الخواص المخصصة
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.OneUiTextView);
        int maxLevel = a.getInt(R.styleable.OneUiTextView_maxFontLevel, -1);
        int minLevel = a.getInt(R.styleable.OneUiTextView_minFontLevel, -1);
        a.recycle();

        if (rawTextSize != -1f) {
            float fontScale = context.getResources().getConfiguration().fontScale;
            
            // جلب النسب المئوية بناءً على القيم المدخلة
            float maxScale = maxLevel != -1 ? getFontScale(maxLevel) : -1f;
            float minScale = minLevel != -1 ? getFontScale(minLevel) : -1f;

            float finalScale = fontScale;

            // 3. تطبيق الحد الأدنى (يمنع الخط من أن يصغر أكثر من اللازم)
            if (minScale != -1f && finalScale < minScale) {
                finalScale = minScale;
            }

            // 4. تطبيق الحد الأقصى (يمنع الخط من أن يكبر أكثر من اللازم)
            if (maxScale != -1f && finalScale > maxScale) {
                finalScale = maxScale;
            }

            // 5. تطبيق الخط بوحدة DIP مع المقياس النهائي المضبوط
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, rawTextSize * finalScale);
        }
    }

    private static float getFontScale(int level) {
        switch (level) {
            case 1: return 0.8f;  // tiny
            case 2: return 0.9f;  // extra_small
            case 3: return 1.0f;  // small
            case 4: return 1.1f;  // medium
            case 5: return 1.3f;  // large
            case 6: return 1.5f;  // extra_large
            case 7: return 1.7f;  // huge
            case 8: return 2.0f;  // extra_huge
            default: return 1.0f;
        }
    }
}

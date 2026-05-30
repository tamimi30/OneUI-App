package com.example.oneuiapp.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// ★ التعديل الأول: الوراثة من OneUiTextView بدلاً من AppCompatTextView ★
public class FontPreviewTextView extends OneUiTextView {

    private float offsetX;

    public FontPreviewTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public FontPreviewTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FontPreviewTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // تحديد مسافة الأمان (8 بكسل متناسبة مع كثافة الشاشة)
        offsetX = 8f * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        
        // ★ التعديل الثاني: التحقق من اتجاه اللغة (عربي/إنجليزي) ★
        boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        
        if (isRtl) {
            // إذا كانت اللغة عربية (من اليمين لليسار)، نزيح الرسم لليسار لحماية الحافة اليمنى
            canvas.translate(-offsetX, 0f);
        } else {
            // إذا كانت اللغة إنجليزية (من اليسار لليمين)، نزيح الرسم لليمين لحماية الحافة اليسرى
            canvas.translate(offsetX, 0f);
        }
        
        super.onDraw(canvas);
        
        canvas.restore();
    }
}

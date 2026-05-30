package com.example.oneuiapp.utils; // ضع مسار البكج الخاص بك

import android.graphics.Paint;
import android.text.style.LineHeightSpan;

public class ExactLineHeightSpan implements LineHeightSpan {

    private final int heightPx;

    /**
     * @param heightPx الارتفاع المطلوب للنص بوحدة البكسل
     */
    public ExactLineHeightSpan(int heightPx) {
        this.heightPx = heightPx;
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v, Paint.FontMetricsInt fm) {
        // حساب الارتفاع الأصلي للخط (المسافة بين أسفل الحرف وأعلاه)
        int originHeight = fm.descent - fm.ascent;

        // إذا كان الارتفاع الأصلي صفراً أو أقل، فلا نفعل شيئاً
        if (originHeight <= 0) {
            return;
        }

        // حساب الفارق بين الارتفاع المطلوب والارتفاع الأصلي للخط
        int diff = heightPx - originHeight;

        // توزيع الفارق بالتساوي على الأعلى والأسفل لضمان بقاء النص في المنتصف
        fm.descent += diff / 2;
        fm.ascent -= diff / 2;

        // إجبار النظام على تجاهل أي هوامش إضافية مخفية في ملف الخط
        fm.bottom = fm.descent;
        fm.top = fm.ascent;
    }
}

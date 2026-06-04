package com.example.oneuiapp.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TextDrawable extends Drawable {
    private final String text;
    private final Paint paint;

    public TextDrawable(String text, float textSize, int textColor) {
        this.text = text;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setColor(textColor);
        this.paint.setTextSize(textSize);
        // هذا السطر هو المسؤول عن التوسيط الأفقي التام
        this.paint.setTextAlign(Paint.Align.CENTER); 
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        // إعطاء نقطة المنتصف الأفقية للرسم
        float x = bounds.centerX(); 
        // معادلة التوسيط العمودي
        float y = bounds.centerY() - ((paint.descent() + paint.ascent()) / 2);
        canvas.drawText(text, x, y, paint);
    }

    @Override
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TextDrawable extends Drawable {
    private final String text;
    private final Paint paint;
   
    private final int intrinsicSize;

    private static final float REFERENCE_TEXT_SIZE_DP = 24f;

    public TextDrawable(Context context, String text, float textSizeInDp, int textColor) {
        this.text = text;
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setColor(textColor);
        this.paint.setTextAlign(Paint.Align.CENTER);

        float textSizeInPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                textSizeInDp,
                context.getResources().getDisplayMetrics()
        );
        this.paint.setTextSize(textSizeInPx);

        float referenceSizeInPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                REFERENCE_TEXT_SIZE_DP,
                context.getResources().getDisplayMetrics()
        );
        this.intrinsicSize = Math.round(referenceSizeInPx);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float x = bounds.centerX();
        float y = bounds.centerY() - ((paint.descent() + paint.ascent()) / 2);
        canvas.drawText(text, x, y, paint);
    }

    @Override
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override
    public int getIntrinsicWidth() { return intrinsicSize; }

    @Override
    public int getIntrinsicHeight() { return intrinsicSize; }
}

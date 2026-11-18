package com.oneui.fontviewer.ui.widget.drawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dev.oneuiproject.oneui.design.R;

/**
 * نقطة صغيرة (بدون نص) فوق أيقونة زر التنقل، مثل النقطة التي تظهر في تطبيقات Samsung عند وجود تحديث.
 * موضعها هو نفس موضع شارة الرقم/N في مكتبة OneUI، لكن بحجم أصغر.
 */
public class NavigationDotBadgeIcon extends Drawable {

    private static final float BADGE_ICON_X = 0.8125f;
    private static final float BADGE_ICON_Y = 0.1458f;
    private static final float Y_DIFF = 4.f;

    // نصف قطر النقطة (القطر 6dp مثل sesl_dot_badge)
    private static final float DOT_RADIUS_DP = 3.f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mRadiusPx;
    private boolean mIsLandscape = false;

    public NavigationDotBadgeIcon(@NonNull Context context) {
        mPaint.setColor(context.getColor(R.color.oui_n_badge_background_color));
        mRadiusPx = DOT_RADIUS_DP * context.getResources().getDisplayMetrics().density;
    }

    public void setOrientation(boolean isLandscape) {
        mIsLandscape = isLandscape;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        final float x = bounds.width() * BADGE_ICON_X;
        final float y = mIsLandscape
                ? (bounds.height() * BADGE_ICON_Y) - Y_DIFF
                : -Y_DIFF;
        canvas.drawCircle(x, y, mRadiusPx, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

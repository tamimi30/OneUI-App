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
 * موضعها عند الزاوية العلوية اليمنى لخطوط الأيقونة.
 */
public class NavigationDotBadgeIcon extends Drawable {

    // مركز النقطة كنسبة من أبعاد الأيقونة (24dp): عند الزاوية العلوية اليمنى لخطوط أيقونة القائمة
    // وليس فوقها. لتحريكها عدّل القيمتين: زيادة X تحرّكها لليمين، وزيادة Y تحرّكها للأسفل.
    private static final float DOT_CENTER_X = 0.83f;
    private static final float DOT_CENTER_Y = 0.20f;

    // نصف قطر النقطة (القطر 6dp مثل sesl_dot_badge)
    private static final float DOT_RADIUS_DP = 3.f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mRadiusPx;
    private boolean mIsLandscape = false;

    // في الواجهة من اليمين لليسار (العربية) تنعكس الأيقونة فتنتقل النقطة إلى زاويتها العلوية اليسرى
    private boolean mIsRtl = false;

    public NavigationDotBadgeIcon(@NonNull Context context) {
        mPaint.setColor(context.getColor(R.color.oui_n_badge_background_color));
        mRadiusPx = DOT_RADIUS_DP * context.getResources().getDisplayMetrics().density;
    }

    // الموضع صار نفسه في الاتجاهين، وتبقى الدالة ليستدعيها OneUiToolBarLayout مثل شارة المكتبة
    public void setOrientation(boolean isLandscape) {
        mIsLandscape = isLandscape;
    }

    public void setRtl(boolean isRtl) {
        mIsRtl = isRtl;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        final float centerX = mIsRtl ? 1f - DOT_CENTER_X : DOT_CENTER_X;
        canvas.drawCircle(
                bounds.left + bounds.width() * centerX,
                bounds.top + bounds.height() * DOT_CENTER_Y,
                mRadiusPx, mPaint);
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

package com.oneui.fontviewer.ui.widget.drawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * نقطة صغيرة (بدون نص) في أعلى جانب أيقونة التنقل، مثل نقطة التحديث في تطبيقات Samsung.
 * تُستخدم كطبقة فوق الأيقونة داخل LayerDrawable (انظر OneUiToolBarLayout.setNavigationButtonBadge).
 */
public class DotBadgeDrawable extends Drawable {

    // نفس موضع مركز شارة N في NavigationBadgeIcon (أعلى الأيقونة من جهة النهاية)
    private static final float CENTER_X_RATIO = 0.8125f;
    private static final float CENTER_Y_RATIO = 0.1458f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mRadius;

    public DotBadgeDrawable(@NonNull Context context) {
        mPaint.setColor(context.getColor(dev.oneuiproject.oneui.design.R.color.oui_n_badge_background_color));
        mRadius = context.getResources().getDimension(com.oneui.fontviewer.R.dimen.update_badge_dot_size) / 2f;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();

        // في الواجهات من اليمين لليسار تنتقل النقطة إلى الجهة المقابلة مع الأيقونة
        boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float xRatio = isRtl ? 1f - CENTER_X_RATIO : CENTER_X_RATIO;

        canvas.drawCircle(
                bounds.left + bounds.width() * xRatio,
                bounds.top + bounds.height() * CENTER_Y_RATIO,
                mRadius,
                mPaint);
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

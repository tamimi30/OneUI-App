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
    // الحجم الجوهري (intrinsic size) للرسم. FloatingActionButtonImpl يحتاجه لحساب
    // مصفوفة تحجيم/تموضع الأيقونة (image matrix) أثناء أنيميشن show()/hide() الخاص بالـ FAB.
    // بدونه كانت القيمة الافتراضية -1x-1 تكسر هذا الحساب، فيظهر رقم حجم الخط مشوّهًا
    // أو بمكان خاطئ أثناء ظهور الزر بدل الأنيميشن الجميل المعتاد.
    private final int intrinsicSize;

    // ★ إصلاح: حجم مرجعي ثابت يُستخدم فقط لحساب الحجم الجوهري (intrinsicSize)، بمعزل عن
    // textSizeInDp الفعلي الذي يُرسم به النص. السبب: FloatingActionButtonImpl يعيد تحجيم أي
    // Drawable ليملأ maxImageSize بالكامل اعتمادًا على نسبة drawable.getIntrinsicWidth/Height
    // (ScaleToFit.CENTER) — فلو كان الحجم الجوهري يساوي textSizeInPx دائمًا (كما كان سابقًا)،
    // فإن "الصندوق" الذي يُقاس عليه النص يصغر بنفس نسبة تصغير الخط، فيُلغي إعادة التحجيم
    // التلقائي أثر التصغير عمليًا، ويخرج النص بنفس الحجم المرئي تقريبًا سواء كان 19dp أو 24dp.
    // بتثبيت الحجم الجوهري على REFERENCE_TEXT_SIZE_DP دائمًا، يصبح النص الأصغر (19dp عند 3
    // خانات) يشغل مساحة أصغر فعليًا من نفس الصندوق الثابت، فيظهر أصغر بالفعل بعد إعادة
    // التحجيم النهائية الى maxImageSize. لاحظ أن REFERENCE_TEXT_SIZE_DP تساوي قيمة الحالة غير
    // المصغّرة (24dp) عمدًا، حتى يبقى شكل الأرقام ذات الخانتين مطابقًا تمامًا لما كان عليه سابقًا،
    // ويتغيّر فقط شكل الأرقام ذات الثلاث خانات (وهي الحالة المقصودة بالتصغير أصلاً).
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

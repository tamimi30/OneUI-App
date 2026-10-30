package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * TextView لا يقصّ الأجزاء البارزة من الحروف المائلة (مثل ذيل حرف j).
 *
 * السبب: TextView يقصّ الرسم داخل مستطيل المحتوى (بعد الـ padding)، والميل الاصطناعي
 * setTextSkewX يزيح ذيل الحرف يسارًا وقمّته يمينًا خارج هذا المستطيل.
 *
 * الحل (نفس فكرتك): نحجز مسافة في بداية السطر ونهايته داخل الـ layout نفسه، ثم نوسّع
 * مستطيل القص بنفس المقدار عبر shadow شفاف (TextView يوسّع القص تلقائيًا عند وجود shadow)،
 * فيظهر الجزء البارز داخل المسافة المحجوزة.
 */
public class OverhangTextView extends AppCompatTextView {

    /** الميل الاصطناعي المستخدم عند تفعيل زر Italic. */
    public static final float FAKE_ITALIC_SKEW = -0.25f;

    /** مسافة دنيا (كنسبة من حجم الخط) تُحجز دائمًا، تفيد أيضًا مع الخطوط المائلة الحقيقية (محور ital). */
    private static final float DEFAULT_OVERHANG_EM = 0.12f;

    private float overhangEm = DEFAULT_OVERHANG_EM;
    private int insetLeft;
    private int insetRight;
    private int appliedRadius = -1;

    public OverhangTextView(Context context) {
        super(context);
    }

    public OverhangTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OverhangTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** يفعّل/يلغي الميل الاصطناعي ويحدّث المسافة المحجوزة. */
    public void setFakeItalic(boolean italic) {
        getPaint().setTextSkewX(italic ? FAKE_ITALIC_SKEW : 0f);
        if (refreshOverhang()) {
            requestLayout();
        }
        invalidate();
    }

    /** يغيّر المسافة الدنيا المحجوزة (كنسبة من حجم الخط). 0 لإلغائها. */
    public void setOverhangEm(float em) {
        overhangEm = Math.max(0f, em);
        if (refreshOverhang()) {
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // نحسب المسافة قبل القياس حتى تدخل في عرض الـ layout (يشمل تغيّر حجم الخط).
        refreshOverhang();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public int getCompoundPaddingLeft() {
        return super.getCompoundPaddingLeft() + insetLeft;
    }

    @Override
    public int getCompoundPaddingRight() {
        return super.getCompoundPaddingRight() + insetRight;
    }

    /** @return true إذا تغيّرت المسافة المحجوزة. */
    private boolean refreshOverhang() {
        TextPaint paint = getPaint();
        float skew = paint.getTextSkewX();
        Paint.FontMetrics fm = paint.getFontMetrics();

        // الإزاحة الأفقية عند ارتفاع y عن خط الأساس = skew * y (ascent سالب، descent موجب)
        float shiftAtTop = skew * fm.ascent;
        float shiftAtBottom = skew * fm.descent;
        float leftBySkew = -Math.min(0f, Math.min(shiftAtTop, shiftAtBottom));
        float rightBySkew = Math.max(0f, Math.max(shiftAtTop, shiftAtBottom));

        float base = overhangEm * paint.getTextSize();
        int left = (int) Math.ceil(Math.max(base, leftBySkew));
        int right = (int) Math.ceil(Math.max(base, rightBySkew));

        boolean changed = left != insetLeft || right != insetRight;
        insetLeft = left;
        insetRight = right;

        // توسيع مستطيل القص: TextView يزيد القص بمقدار نصف قطر الـ shadow في كل الاتجاهات.
        int radius = Math.max(left, right);
        if (radius != appliedRadius) {
            appliedRadius = radius;
            setShadowLayer(radius, 0f, 0f, Color.TRANSPARENT);
        }
        return changed;
    }
}

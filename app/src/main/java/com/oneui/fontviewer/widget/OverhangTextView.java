package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * TextView لا يقصّ الأجزاء البارزة من الحروف (مثل ذيل حرف j).
 *
 * السبب: TextView يقصّ الرسم داخل مستطيل المحتوى (بعد الـ padding)، والحرف قد يبرز
 * يسار نقطة بدايته (تصميم الحرف نفسه + الميل الاصطناعي setTextSkewX).
 *
 * الحل: نقدّر البروز من حجم الخط والميل (دون قياس الحروف، فلا يتغير أثناء أنيميشن المحاور)،
 * ونوسّع مستطيل القص بـ shadow شفاف (TextView يوسّع القص تلقائيًا عند وجود shadow).
 *
 * "سماحية الخروج" (setOverflowAllowance): مقدار البروز الذي يُسمح له بالخروج خارج حدود
 * الـ TextView إلى padding الأب (يلزم clipChildren=false و clipToPadding=false عند الأب).
 * البروز الأكبر منها فقط يُحجز له مسافة داخل الـ layout (إزاحة النص إلى الداخل).
 * فمع الأحجام العادية لا تتغير أي مسافة، وتظهر الإزاحة فقط عند الأحجام الكبيرة.
 */
public class OverhangTextView extends AppCompatTextView {

    /** الميل الاصطناعي المستخدم عند تفعيل زر Italic. */
    public static final float FAKE_ITALIC_SKEW = -0.25f;

    /**
     * أدنى بروز يُفترض (كنسبة من حجم الخط) ويغطي ذيل الـ j في الأوزان الخفيفة أيضًا.
     * ارفعه إذا بقي قص بسيط مع خطوط أخف أو أكثر بروزًا، وخفّضه لتقليل الإزاحة عند الأحجام الكبيرة.
     */
    private static final float DEFAULT_OVERHANG_EM = 0.16f;

    private float overhangEm = DEFAULT_OVERHANG_EM;
    private float allowancePx = 0f;
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

    /** يغيّر أدنى بروز مفترض (كنسبة من حجم الخط). */
    public void setOverhangEm(float em) {
        overhangEm = Math.max(0f, em);
        if (refreshOverhang()) {
            requestLayout();
        }
    }

    /**
     * مقدار البروز (بالبكسل) المسموح له بالخروج خارج حدود الـ TextView دون إزاحة النص.
     * يلزم أن يكون عند الأب مساحة بهذا القدر وأن يكون clipChildren و clipToPadding = false.
     */
    public void setOverflowAllowance(float px) {
        allowancePx = Math.max(0f, px);
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

        // البروز الكلي المتوقع: أدنى بروز للحرف + أثر الميل
        float base = overhangEm * paint.getTextSize();
        float totalLeft = Math.max(base, leftBySkew);
        float totalRight = Math.max(base, rightBySkew);

        // نحجز في الـ layout فقط ما يزيد على السماحية
        int left = (int) Math.ceil(Math.max(0f, totalLeft - allowancePx));
        int right = (int) Math.ceil(Math.max(0f, totalRight - allowancePx));

        boolean changed = left != insetLeft || right != insetRight;
        insetLeft = left;
        insetRight = right;

        // توسيع مستطيل القص ليغطي البروز كاملًا: TextView يزيد القص بمقدار نصف قطر الـ shadow.
        int radius = (int) Math.ceil(Math.max(totalLeft, totalRight));
        if (radius != appliedRadius) {
            appliedRadius = radius;
            setShadowLayer(radius, 0f, 0f, Color.TRANSPARENT);
        }
        return changed;
    }
}

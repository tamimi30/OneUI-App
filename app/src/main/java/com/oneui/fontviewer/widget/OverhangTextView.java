package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TextView لا يقصّ الأجزاء البارزة من الحروف (مثل ذيل حرف j).
 *
 * السبب: TextView يقصّ الرسم داخل مستطيل المحتوى (بعد الـ padding)، والحرف قد يبرز
 * يسار نقطة بدايته (تصميم الحرف نفسه + الميل الاصطناعي setTextSkewX).
 *
 * الحل: نقدّر البروز من حجم الخط وحالة الميل (دون قياس الحروف، فلا يتغير أثناء أنيميشن المحاور)،
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
     * أدنى بروز يُفترض (كنسبة من حجم الخط) للخط القائم، ويغطي ذيل الـ j في الأوزان الخفيفة أيضًا.
     * ارفعه إذا بقي قص بسيط مع خطوط قائمة أخف أو أكثر بروزًا، وخفّضه لتقليل الإزاحة عند الأحجام الكبيرة.
     */
    private static final float DEFAULT_UPRIGHT_OVERHANG_EM = 0.09f;

    /** أدنى بروز يُفترض (كنسبة من حجم الخط) للخط المائل (محور ital أو خط مائل حقيقي). */
    private static final float DEFAULT_ITALIC_OVERHANG_EM = 0.16f;

    // قراءة قيمة محور الميل من نص إعدادات المحاور، مثل: 'wght' 400, 'ital' 1
    private static final Pattern ITAL_AXIS = Pattern.compile("'ital'\\s+(-?[0-9.]+)");
    private static final Pattern SLNT_AXIS = Pattern.compile("'slnt'\\s+(-?[0-9.]+)");

    private float uprightEm = DEFAULT_UPRIGHT_OVERHANG_EM;
    private float italicEm = DEFAULT_ITALIC_OVERHANG_EM;
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

    /** يغيّر أدنى بروز مفترض (كنسبة من حجم الخط) للخط القائم والمائل. */
    public void setOverhangEm(float upright, float italic) {
        uprightEm = Math.max(0f, upright);
        italicEm = Math.max(0f, italic);
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
        // نحسب المسافة قبل القياس حتى تدخل في عرض الـ layout (يشمل تغيّر حجم الخط ومحور الميل).
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

        // البروز الكلي المتوقع: بروز الحرف نفسه (قائم/مائل) + أثر الميل الاصطناعي إن وُجد
        float base = (isItalicFont(paint) ? italicEm : uprightEm) * paint.getTextSize();
        float totalLeft = base + leftBySkew;
        float totalRight = base + rightBySkew;

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

    /** هل الخط الحالي مائل فعلًا (محور ital أو slnt، أو خط مائل حقيقي)؟ */
    private boolean isItalicFont(TextPaint paint) {
        String variation = paint.getFontVariationSettings();
        if (variation != null) {
            Float ital = readAxis(ITAL_AXIS, variation);
            if (ital != null) {
                return ital > 0.5f;
            }
            Float slnt = readAxis(SLNT_AXIS, variation);
            if (slnt != null) {
                return Math.abs(slnt) > 0.5f;
            }
        }
        Typeface typeface = paint.getTypeface();
        return typeface != null && typeface.isItalic();
    }

    private static Float readAxis(Pattern pattern, String variation) {
        Matcher matcher = pattern.matcher(variation);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Float.parseFloat(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

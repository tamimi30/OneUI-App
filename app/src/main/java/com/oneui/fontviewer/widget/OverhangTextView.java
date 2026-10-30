package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import java.util.HashSet;
import java.util.Set;

/**
 * TextView لا يقصّ الأجزاء البارزة من الحروف المائلة (مثل ذيل حرف j).
 *
 * السبب: TextView يقصّ الرسم داخل مستطيل المحتوى (بعد الـ padding)، والميل الاصطناعي
 * setTextSkewX يزيح ذيل الحرف يسارًا وقمّته يمينًا خارج هذا المستطيل.
 *
 * الحل (نفس فكرتك): نحجز مسافة في بداية السطر ونهايته داخل الـ layout نفسه، ثم نوسّع
 * مستطيل القص بنفس المقدار عبر shadow شفاف (TextView يوسّع القص تلقائيًا عند وجود shadow)،
 * فيظهر الجزء البارز داخل المسافة المحجوزة.
 *
 * المسافة تُقاس الآن من الحبر الفعلي لحروف النص (وليس رقمًا ثابتًا)، فتتكيّف مع
 * أي وزن أو خط (Light وغيره) ومع الميل الاصطناعي والحقيقي.
 */
public class OverhangTextView extends AppCompatTextView {

    /** الميل الاصطناعي المستخدم عند تفعيل زر Italic. */
    public static final float FAKE_ITALIC_SKEW = -0.25f;

    /** هامش أمان إضافي (كنسبة من حجم الخط) فوق المقاس. 0 = ملغى، ويمكن تفعيله عبر setOverhangEm(). */
    private static final float DEFAULT_OVERHANG_EM = 0f;

    private float overhangEm = DEFAULT_OVERHANG_EM;
    private int insetLeft;
    private int insetRight;
    private int appliedRadius = -1;

    private final Rect tmpBounds = new Rect();

    // مفاتيح الكاش: لا نعيد قياس الحبر إلا إذا تغيّر أحدها
    private String cachedText;
    private float cachedSize = -1f;
    private float cachedSkew = Float.NaN;
    private boolean cachedFakeBold;
    private Typeface cachedTypeface;
    private String cachedVariation;
    private float inkLeft;
    private float inkRight;

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

    /** يغيّر هامش الأمان الإضافي (كنسبة من حجم الخط). 0 لإلغائه. */
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
        measureInkIfNeeded(paint);

        float base = overhangEm * paint.getTextSize();
        int left = (int) Math.ceil(inkLeft + base);
        int right = (int) Math.ceil(inkRight + base);

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

    /**
     * يقيس أقصى بروز للحبر خارج حدود الحرف (يسارًا ويمينًا) بين كل حروف النص المختلفة.
     * أي حرف قد يبدأ سطرًا أو ينهيه، فنأخذ الأسوأ.
     */
    private void measureInkIfNeeded(TextPaint paint) {
        CharSequence cs = getText();
        String text = cs == null ? "" : cs.toString();
        float size = paint.getTextSize();
        float skew = paint.getTextSkewX();
        boolean fakeBold = paint.isFakeBoldText();
        Typeface typeface = paint.getTypeface();
        String variation = paint.getFontVariationSettings();

        boolean same = text.equals(cachedText)
                && size == cachedSize
                && skew == cachedSkew
                && fakeBold == cachedFakeBold
                && typeface == cachedTypeface
                && (variation == null ? cachedVariation == null : variation.equals(cachedVariation));
        if (same) {
            return;
        }

        cachedText = text;
        cachedSize = size;
        cachedSkew = skew;
        cachedFakeBold = fakeBold;
        cachedTypeface = typeface;
        cachedVariation = variation;

        if (text.isEmpty()) {
            inkLeft = 0f;
            inkRight = 0f;
            return;
        }

        float[] withSkew = scanInk(paint, text);
        float left = withSkew[0];
        float right = withSkew[1];

        // هل يحسب getTextBounds الميل الاصطناعي؟ نقارن النتيجة مع ميل = 0.
        // إذا لم يحسبه نضيف أثر الميل يدويًا حتى لا نقصّ.
        if (skew != 0f) {
            paint.setTextSkewX(0f);
            float[] noSkew = scanInk(paint, text);
            paint.setTextSkewX(skew);

            boolean boundsIncludeSkew =
                    Math.abs(withSkew[0] - noSkew[0]) + Math.abs(withSkew[1] - noSkew[1]) > 0.5f;
            if (!boundsIncludeSkew) {
                Paint.FontMetrics fm = paint.getFontMetrics();
                float shiftAtTop = skew * fm.ascent;      // ascent سالب
                float shiftAtBottom = skew * fm.descent;  // descent موجب
                left += -Math.min(0f, Math.min(shiftAtTop, shiftAtBottom));
                right += Math.max(0f, Math.max(shiftAtTop, shiftAtBottom));
            }
        }

        // الخط العريض الاصطناعي يوسّع حواف الحروف قليلًا
        if (fakeBold) {
            float extra = size / 64f;
            left += extra;
            right += extra;
        }

        inkLeft = left;
        inkRight = right;
    }

    /** @return {أقصى بروز يسار، أقصى بروز يمين} بوحدة البكسل، لكل حرف مختلف في النص. */
    private float[] scanInk(TextPaint paint, String text) {
        float left = 0f;
        float right = 0f;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || !seen.add(cp)) {
                continue;
            }
            String ch = new String(Character.toChars(cp));
            paint.getTextBounds(ch, 0, ch.length(), tmpBounds);
            float advance = paint.measureText(ch);
            left = Math.max(left, -tmpBounds.left);
            right = Math.max(right, tmpBounds.right - advance);
        }
        return new float[]{left, right};
    }
}

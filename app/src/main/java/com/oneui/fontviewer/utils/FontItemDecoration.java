package com.oneui.fontviewer.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.recyclerview.widget.RecyclerView;


/**
 * FontItemDecoration - فئة لرسم الفواصل بين عناصر القائمة
 * تستخدم في قوائم الخطوط لإضافة خطوط فاصلة بين العناصر
 *
 * وترسم أيضاً زوايا البطاقة (أول وآخر وعنصر الخط الوحيد)، ولا تحتاج العناصر ولا الـ Adapter
 * إلى أي كود للزوايا أو الفواصل.
 *
 * شريط الفرز (أول عنصر في الـ Adapter) و Bottom space (آخر عنصر في الـ Adapter)
 * لا يُعتبران عناصر خطوط، لذلك لا يُرسم لهما فاصل.
 *
 * شريط الفرز و Bottom space شفافان دائماً (بدون أي لون خلفية)، ولا يؤثر فيهما الأنيميشن إطلاقاً.
 * لون الصفحة (oui_round_and_bgcolor) يُرسم هنا خلفهما، ولون البطاقة (خلفية الـ RecyclerView)
 * يبقى فقط بين أسفل شريط الفرز وأسفل آخر عنصر خط، لذلك يتعامل الـ RecyclerView مع عناصر الخطوط فقط.
 *
 * حواف البطاقة:
 * - الحافة العلوية ثابتة أسفل شريط الفرز مباشرة، فلا يظهر لون الصفحة بينه وبين العناصر
 *   حتى لو كانت العناصر تتحرك أثناء الأنيميشن.
 * - الحافة السفلية تتبع آخر عنصر خط، فلا تتأثر بحركة Bottom space.
 * - العنصر المحذوف (يبقى في مكانه ويتلاشى) يُرسم خلفه لون البطاقة بنفس شفافيته.
 *
 * ولهذا لا نستخدم ملء الأسفل الخاص بالـ RecyclerView
 * (seslSetFillBottomEnabled / seslSetLastRoundedCorner).
 */
public class FontItemDecoration extends RecyclerView.ItemDecoration {

    // عدد العناصر التي ليست خطوطاً في بداية القائمة (شريط الفرز)
    private static final int HEADER_COUNT = 1;

    // عدد العناصر التي ليست خطوطاً في نهاية القائمة (Bottom space)
    private static final int FOOTER_COUNT = 1;

    private final Drawable divider;
    private final SeslRoundedCorner roundedCorner;
    private final Paint pagePaint = new Paint();
    private final Paint cardPaint = new Paint();

    // حواف البطاقة: تُحسب في onDraw ثم تُستخدم في seslOnDispatchDraw (نفس الإطار، onDraw أولاً)
    private boolean hasCardTop;
    private boolean hasCardBottom;
    private int cardTop;
    private int cardBottom;
    
    public FontItemDecoration(@NonNull Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(
            dev.oneuiproject.oneui.design.R.attr.isLightTheme, 
            typedValue, 
            true
        );
        
        int dividerResId = typedValue.data == 0 ? 
            dev.oneuiproject.oneui.design.R.drawable.sesl_list_divider_dark :
            dev.oneuiproject.oneui.design.R.drawable.sesl_list_divider_light;
        
        Drawable dividerDrawable = context.getDrawable(dividerResId);
        divider = (dividerDrawable != null) ? dividerDrawable.mutate() : null;

        // لون الزوايا الافتراضي هو نفس لون الصفحة (round and bgcolor)
        roundedCorner = new SeslRoundedCorner(context, true);

        pagePaint.setStyle(Paint.Style.FILL);
        pagePaint.setColor(roundedCorner.getRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT));

        cardPaint.setStyle(Paint.Style.FILL);
    }
    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);
        
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) {
            return;
        }

        int width = parent.getWidth();
        int height = parent.getHeight();

        // لون البطاقة هو لون خلفية الـ RecyclerView نفسه
        boolean hasCardColor = parent.getBackground() instanceof ColorDrawable;
        if (hasCardColor) {
            cardPaint.setColor(((ColorDrawable) parent.getBackground()).getColor());
        }

        int headerPosition = HEADER_COUNT - 1;
        int firstFontPosition = HEADER_COUNT;
        int lastFontPosition = adapter.getItemCount() - FOOTER_COUNT - 1;

        // العنصر الأول: حافة البطاقة العلوية ثابتة أسفل شريط الفرز
        // العنصر الأخير: حافة البطاقة السفلية عند أسفل آخر عنصر خط (بعد حركته)
        // إذا لم يكن أحدهما ظاهراً (قائمة ممرّرة) فالبطاقة تمتد خارج الشاشة من تلك الجهة
        hasCardTop = false;
        hasCardBottom = false;
        cardTop = 0;
        cardBottom = height;

        boolean hasFontItem = false;
        int lowestFontBottom = Integer.MIN_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (position == headerPosition) {
                hasCardTop = true;
                cardTop = Math.round(child.getY()) + child.getHeight();
            } else if (position >= firstFontPosition && position <= lastFontPosition) {
                hasFontItem = true;
                lowestFontBottom = Math.max(lowestFontBottom, Math.round(child.getY()) + child.getHeight());
                if (position == lastFontPosition) {
                    hasCardBottom = true;
                }
            }
        }

        if (hasCardBottom) {
            cardBottom = lowestFontBottom;
        } else if (!hasFontItem && hasCardTop) {
            // لا توجد عناصر خطوط: لا توجد بطاقة أصلاً
            hasCardBottom = true;
            cardBottom = cardTop;
        }

        if (hasCardBottom && hasCardTop && cardBottom < cardTop) {
            cardBottom = cardTop;
        }

        // لون الصفحة خلف شريط الفرز (فوق البطاقة) وخلف Bottom space (تحتها)
        if (hasCardTop && cardTop > 0) {
            canvas.drawRect(0, 0, width, Math.min(cardTop, height), pagePaint);
        }
        if (hasCardBottom && cardBottom < height) {
            canvas.drawRect(0, Math.max(cardBottom, 0), width, height, pagePaint);
        }

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            // العنصر المحذوف يبقى في مكانه ويتلاشى: يُرسم خلفه لون البطاقة بنفس شفافيته
            // (داخل البطاقة لا يظهر فرق لأن اللون نفسه)
            if (hasCardColor && position == RecyclerView.NO_POSITION) {
                int alpha = Math.round(child.getAlpha() * 255);
                if (alpha > 0) {
                    cardPaint.setAlpha(alpha);
                    int top = Math.round(child.getY());
                    canvas.drawRect(0, top, width, top + child.getHeight(), cardPaint);
                }
            }
        }

        if (divider == null) {
            return;
        }

        int dividerHeight = Math.max(divider.getIntrinsicHeight(), 1);
        
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            // الفاصل يُرسم أسفل كل عنصر خط ما عدا آخر عنصر خط
            // (لا فاصل أسفل شريط الفرز، ولا أسفل آخر خط، ولا أسفل Bottom space)
            if (position == RecyclerView.NO_POSITION
                    || position < firstFontPosition
                    || position >= lastFontPosition) {
                continue;
            }

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            
            int left = child.getLeft() + Math.round(child.getTranslationX());
            int right = child.getRight() + Math.round(child.getTranslationX());
            int top = child.getBottom() + params.bottomMargin + Math.round(child.getTranslationY());
            int bottom = top + dividerHeight;
            
            divider.setBounds(left, top, right, bottom);
            // يتبع شفافية العنصر أثناء حركة الإضافة والحذف
            divider.setAlpha(Math.round(child.getAlpha() * 255));
            divider.draw(canvas);
        }

        divider.setAlpha(255);
    }

    @Override
    public void seslOnDispatchDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        // الحواف تُحسب في onDraw (قبل رسم العناصر مباشرة في نفس الإطار)
        if (!hasCardTop && !hasCardBottom) {
            return;
        }

        int corners = SeslRoundedCorner.ROUNDED_CORNER_NONE;
        if (hasCardTop) {
            // الزوايا العلوية للبطاقة: أسفل شريط الفرز مباشرة
            corners |= SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT;
        }
        if (hasCardBottom) {
            // الزوايا السفلية للبطاقة: عند أسفل آخر عنصر خط
            corners |= SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT;
        }

        int top = hasCardTop ? cardTop : 0;
        int bottom = hasCardBottom ? cardBottom : parent.getHeight();

        // drawRoundedCorner(Canvas) ترسم الزوايا داخل حدود القص (clip) الحالية
        canvas.save();
        canvas.clipRect(0, top, parent.getWidth(), bottom);
        roundedCorner.setRoundedCorners(corners);
        roundedCorner.drawRoundedCorner(canvas);
        canvas.restore();
    }
}

package com.oneui.fontviewer.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
 * وترسم أيضاً زوايا عنصر الخط الأول والأخير والوحيد، ولا تحتاج العناصر ولا الـ Adapter
 * إلى أي كود للزوايا أو الفواصل.
 *
 * شريط الفرز (أول عنصر في الـ Adapter) و Bottom space (آخر عنصر في الـ Adapter)
 * لا يُعتبران عناصر خطوط، لذلك لا يُرسم لهما فاصل ولا زوايا،
 * ويُحسب العنصر الأول والأخير من عناصر الخطوط الفعلية فقط.
 *
 * خلفية الـ RecyclerView بلون البطاقة، لذلك يُطلى ما فوق أول عنصر خط وما تحت آخر عنصر خط
 * بلون الصفحة، حتى لا تظهر البطاقة بحواف حادة أثناء حركة العناصر (Default item animator).
 */
public class FontItemDecoration extends RecyclerView.ItemDecoration {

    // عدد العناصر التي ليست خطوطاً في بداية القائمة (شريط الفرز)
    private static final int HEADER_COUNT = 1;

    // عدد العناصر التي ليست خطوطاً في نهاية القائمة (Bottom space)
    private static final int FOOTER_COUNT = 1;

    private final Drawable divider;
    private final SeslRoundedCorner roundedCorner;
    private final Paint pagePaint = new Paint();
    
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

        // نفس لون الصفحة الذي تُرسم به الزوايا، فيندمج الطلاء مع شريط الفرز و Bottom space
        pagePaint.setStyle(Paint.Style.FILL);
        pagePaint.setColor(roundedCorner.getRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT));
    }
    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);
        
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) {
            return;
        }

        int firstFontPosition = HEADER_COUNT;
        int lastFontPosition = adapter.getItemCount() - FOOTER_COUNT - 1;

        drawPageColorOutsideCard(canvas, parent, firstFontPosition, lastFontPosition);

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

    /**
     * لون خلفية الـ RecyclerView هو لون البطاقة، فعندما يتحرك أول أو آخر عنصر خط أثناء الأنيميشن
     * يظهر لون البطاقة في الفراغ الذي تركه العنصر. لذلك تُطلى المنطقة التي فوق أول عنصر خط
     * وتحت آخر عنصر خط بلون الصفحة، ويبقى لون البطاقة بينهما فقط.
     */
    private void drawPageColorOutsideCard(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                                          int firstFontPosition, int lastFontPosition) {
        int width = parent.getWidth();
        int height = parent.getHeight();

        // لا توجد عناصر خطوط: لا توجد بطاقة أصلاً
        if (lastFontPosition < firstFontPosition) {
            canvas.drawRect(0, 0, width, height, pagePaint);
            return;
        }

        // إذا لم يكن العنصر الأول (أو الأخير) ظاهراً فالبطاقة تمتد خارج الشاشة من جهته ولا يُطلى شيء
        int cardTop = Integer.MIN_VALUE;
        int cardBottom = Integer.MAX_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (position == firstFontPosition) {
                cardTop = Math.round(child.getY());
            }
            if (position == lastFontPosition) {
                cardBottom = Math.round(child.getY()) + child.getHeight();
            }
        }

        if (cardTop > 0) {
            canvas.drawRect(0, 0, width, Math.min(cardTop, height), pagePaint);
        }
        if (cardBottom < height) {
            canvas.drawRect(0, Math.max(cardBottom, 0), width, height, pagePaint);
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDrawOver(canvas, parent, state);

        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) {
            return;
        }

        int firstFontPosition = HEADER_COUNT;
        int lastFontPosition = adapter.getItemCount() - FOOTER_COUNT - 1;

        // لا توجد عناصر خطوط (شريط الفرز و Bottom space فقط)
        if (lastFontPosition < firstFontPosition) {
            return;
        }

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (position == RecyclerView.NO_POSITION) {
                continue;
            }

            boolean isFirst = (position == firstFontPosition);
            boolean isLast = (position == lastFontPosition);

            int corners;
            if (isFirst && isLast) {
                // العنصر الوحيد: زوايا الأربع
                corners = SeslRoundedCorner.ROUNDED_CORNER_ALL;
            } else if (isFirst) {
                // العنصر الأول: زوايا الأعلى فقط
                corners = SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT
                        | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT;
            } else if (isLast) {
                // العنصر الأخير: زوايا الأسفل فقط
                corners = SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT
                        | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT;
            } else {
                // العنصر الذي في المنتصف: بدون زوايا
                continue;
            }

            roundedCorner.setRoundedCorners(corners);

            // drawRoundedCorner(View, Canvas) قد يحرّك الـ Canvas ولا يعيده
            canvas.save();
            roundedCorner.drawRoundedCorner(child, canvas);
            canvas.restore();
        }
    }
}

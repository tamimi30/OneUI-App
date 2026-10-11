package com.oneui.fontviewer.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.appcompat.util.SeslSubheaderRoundedCorner;
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
 * البطاقة (لون خلفية الـ RecyclerView) هي المنطقة بين شريط الفرز و Bottom space.
 * الزوايا العلوية تُرسم أسفل شريط الفرز مباشرة، والزوايا السفلية أعلى Bottom space مباشرة،
 * وتتبعهما أثناء حركة العناصر (Default item animator)، فلا تظهر خلفية سوداء
 * ولا حواف حادة مهما تحركت العناصر بينهما.
 */
public class FontItemDecoration extends RecyclerView.ItemDecoration {

    // عدد العناصر التي ليست خطوطاً في بداية القائمة (شريط الفرز)
    private static final int HEADER_COUNT = 1;

    // عدد العناصر التي ليست خطوطاً في نهاية القائمة (Bottom space)
    private static final int FOOTER_COUNT = 1;

    private final Drawable divider;
    private final SeslSubheaderRoundedCorner roundedCorner;
    
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
        roundedCorner = new SeslSubheaderRoundedCorner(context);
    }
    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);
        
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (divider == null || adapter == null) {
            return;
        }

        int firstFontPosition = HEADER_COUNT;
        int lastFontPosition = adapter.getItemCount() - FOOTER_COUNT - 1;
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
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDrawOver(canvas, parent, state);

        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) {
            return;
        }

        int headerPosition = HEADER_COUNT - 1;
        int footerPosition = adapter.getItemCount() - FOOTER_COUNT;

        View header = null;
        View footer = null;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (position == headerPosition) {
                header = child;
            } else if (position == footerPosition) {
                footer = child;
            }
        }

        // الزوايا العلوية للبطاقة: تُرسم أسفل شريط الفرز مباشرة
        // (إذا لم يكن شريط الفرز ظاهراً فالبطاقة تمتد خارج الشاشة من الأعلى ولا زوايا)
        if (header != null) {
            roundedCorner.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT
                    | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT);
            roundedCorner.drawRoundedCorner(header, canvas);
        }

        // الزوايا السفلية للبطاقة: تُرسم أعلى Bottom space مباشرة
        // (إذا لم يكن Bottom space ظاهراً فالبطاقة تمتد خارج الشاشة من الأسفل ولا زوايا)
        if (footer != null) {
            roundedCorner.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT
                    | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT);
            roundedCorner.drawRoundedCorner(footer, canvas);
        }
    }
}

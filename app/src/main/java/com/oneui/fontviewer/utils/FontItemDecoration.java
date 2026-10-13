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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;

/**
 * FontItemDecoration - فئة لرسم الفواصل بين عناصر القائمة
 * تستخدم في قوائم الخطوط لإضافة خطوط فاصلة بين العناصر
 *
 * كما تتولى رسم زوايا القائمة بنفس أسلوب مكتبة RecyclerView (SeslSubheaderRoundedCorner)،
 * وذلك بمعاملة شريط الفرز / رأس القائمة والـ Bottom space كـ Separator:
 *   - الفاصل يرسم الزاويتين العلويتين للعنصر الذي تحته، والزاويتين السفليتين للعنصر الذي فوقه.
 *   - لذلك يحصل أول خط على زوايا علوية مدورة (شريط الفرز فوقه)،
 *     وآخر خط على زوايا سفلية مدورة (الـ Bottom space تحته)،
 *     والخط الوحيد على الأربع، وعناصر المنتصف بلا زوايا، دون أي كود داخل المحوّل.
 *
 * الاستخدام: القائمة تحتاج android:background بلون oui_background_color على الـ RecyclerView،
 * وعناصر الخطوط بلا خلفية (شفافة)، وأن يكون الرأس والـ Bottom space بلون oui_round_and_bgcolor.
 */
public class FontItemDecoration extends RecyclerView.ItemDecoration {

    private final Drawable divider;
    private final SeslSubheaderRoundedCorner roundedCorner;

    // نوع العرض (viewType) الخاص بعناصر الخطوط الفعلية، وكل ما سواه (الرأس والـ Bottom space) يُعامل كـ Separator
    private final int fontViewType;

    public FontItemDecoration(@NonNull Context context, int fontViewType) {
        this.fontViewType = fontViewType;

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
        // mutate حتى لا يؤثر تغيير الشفافية أثناء الأنيميشن على بقية الأماكن التي تستخدم نفس الـ Drawable
        divider = dividerDrawable != null ? dividerDrawable.mutate() : null;

        roundedCorner = new SeslSubheaderRoundedCorner(context);
        roundedCorner.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);
        // نفس لون خلفية شريط الفرز والـ Bottom space، ليبدو القص سلساً
        roundedCorner.setRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_ALL,
            ContextCompat.getColor(context, R.color.oui_round_and_bgcolor));
    }
    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);
        
        if (divider == null) {
            return;
        }

        final int left = parent.getPaddingLeft();
        final int right = parent.getWidth() - parent.getPaddingRight();
        
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            RecyclerView.ViewHolder holder = parent.getChildViewHolder(child);

            // لا فاصل تحت الرأس ولا تحت الـ Bottom space (الـ Separators)
            if (holder.getItemViewType() != fontViewType) {
                continue;
            }

            // لا فاصل تحت آخر عنصر خط: الـ Bottom space يليه مباشرةً ويغطي مكان الفاصل أصلًا،
            // لكن أثناء الأنيميشن يختلف موضع الفاصل (مقرَّب إلى int) عن موضع الـ Bottom space (float)
            // فكان يظهر الفاصل فوقه لفترة قصيرة ثم يختفي
            if (isLastFontItem(parent, holder)) {
                continue;
            }

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            
            // translationY وalpha للعنصر، لكي يتحرك الفاصل ويتلاشى مع العنصر أثناء الأنيميشن
            int top = child.getBottom() + params.bottomMargin + Math.round(child.getTranslationY());
            int bottom = top + divider.getIntrinsicHeight();
            
            divider.setBounds(left, top, right, bottom);
            divider.setAlpha(Math.round(child.getAlpha() * 255));
            divider.draw(canvas);
        }
    }

    // العنصر الذي يليه (حسب ترتيب المحوّل النهائي) ليس عنصر خط، أي الـ Bottom space، فهو آخر عنصر خط.
    // العناصر التي تختفي بالأنيميشن ليس لها موضع (NO_POSITION)، فتُرسم فواصلها وتتلاشى معها
    private boolean isLastFontItem(@NonNull RecyclerView parent, @NonNull RecyclerView.ViewHolder holder) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        int position = holder.getBindingAdapterPosition();

        if (adapter == null || position == RecyclerView.NO_POSITION) {
            return false;
        }

        int next = position + 1;
        return next >= adapter.getItemCount() || adapter.getItemViewType(next) != fontViewType;
    }

    // تُستدعى في dispatchDraw، أي بعد رسم العناصر، فتظهر الزوايا فوقها
    @Override
    public void seslOnDispatchDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.seslOnDispatchDraw(canvas, parent, state);

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            if (parent.getChildViewHolder(child).getItemViewType() != fontViewType) {
                roundedCorner.drawRoundedCorner(child, canvas);
            }
        }
    }
}

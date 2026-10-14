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
 * كما تملأ المنطقة التي تحت الـ Bottom space بلون الخلفية الخارجية (oui_round_and_bgcolor) في كل إطار،
 * متتبعةً موضعه الفعلي أثناء الأنيميشن، حتى لا تظهر خلفية القائمة الرمادية تحته (انظر seslOnDispatchDraw).
 *
 * الاستخدام: القائمة تحتاج android:background بلون oui_background_color على الـ RecyclerView،
 * وعناصر الخطوط بلا خلفية (شفافة)، وأن يكون الرأس والـ Bottom space بلون oui_round_and_bgcolor.
 */
public class FontItemDecoration extends RecyclerView.ItemDecoration {

    private final Drawable divider;
    private final SeslSubheaderRoundedCorner roundedCorner;

    // لون المنطقة تحت الـ Bottom space (نفس لون الـ Bottom space نفسه)
    private final Paint bottomFillPaint = new Paint();

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

        bottomFillPaint.setColor(ContextCompat.getColor(context, R.color.oui_round_and_bgcolor));
        bottomFillPaint.setStyle(Paint.Style.FILL);
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

            // لا فاصل تحت الرأس ولا تحت الـ Bottom space (الـ Separators)
            if (parent.getChildViewHolder(child).getItemViewType() != fontViewType) {
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

    // تُستدعى في dispatchDraw، أي بعد رسم العناصر، فتظهر الزوايا فوقها
    @Override
    public void seslOnDispatchDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.seslOnDispatchDraw(canvas, parent, state);

        final RecyclerView.Adapter<?> adapter = parent.getAdapter();
        final int lastPosition = adapter != null ? adapter.getItemCount() - 1 : -1;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            RecyclerView.ViewHolder holder = parent.getChildViewHolder(child);

            if (holder.getItemViewType() != fontViewType) {
                // الـ Bottom space هو آخر عنصر: أي مساحة تحته تبقى بلون الخلفية الخارجية دائماً.
                // مكتبة RecyclerView ترسم هذه المساحة بمستطيل (seslSetFillBottomEnabled) يتحرك بأنيميشن مستقل
                // عن حركة العناصر، فيتأخر عن الـ Bottom space ويكشف خلفية القائمة الرمادية تحته لأجزاء من الثانية.
                // هنا نرسمها من الحافة السفلية الفعلية للعنصر (مع translationY) فلا تظهر أي فجوة.
                // تُرسم قبل مستطيل المكتبة (الذي يأتي بعد الديكورات في dispatchDraw) وبنفس لونه، فلا تعارض بينهما.
                if (holder.getBindingAdapterPosition() == lastPosition) {
                    final float bottom = child.getBottom() + child.getTranslationY();
                    if (bottom < parent.getHeight()) {
                        canvas.drawRect(0f, bottom, parent.getWidth(), parent.getHeight(), bottomFillPaint);
                    }
                }

                roundedCorner.drawRoundedCorner(child, canvas);
            }
        }
    }
}

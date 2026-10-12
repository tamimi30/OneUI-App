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
 * كما ترسم زوايا القائمة المدورة: أي عنصر نوعه ليس contentViewType (شريط الفرز / Bottom space)
 * يُعتبر فاصلاً، فتُرسم الزوايا العلوية أسفله (أول عنصر خط) والزوايا السفلية أعلاه (آخر عنصر خط).
 */
public class FontItemDecoration extends RecyclerView.ItemDecoration {
    
    private final Drawable divider;
    private final SeslSubheaderRoundedCorner roundedCorner;
    private final int contentViewType;
    
    /**
     * @param contentViewType نوع العنصر الذي يمثل الخط (VIEW_TYPE_FONT / VIEW_TYPE_ITEM)،
     *                        وكل ما سواه يُعتبر عنصراً فاصلاً.
     */
    public FontItemDecoration(@NonNull Context context, int contentViewType) {
        this.contentViewType = contentViewType;

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
        // mutate حتى لا يؤثر تغيير الشفافية أثناء الأنيميشن على بقية الأماكن التي تستخدم نفس الـ drawable
        divider = dividerDrawable != null ? dividerDrawable.mutate() : null;

        roundedCorner = new SeslSubheaderRoundedCorner(context);
        roundedCorner.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);
    }
    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);
        
        if (divider == null) {
            return;
        }
        
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            if (isSeparator(parent, child)) {
                continue;
            }

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            
            int top = child.getBottom() + params.bottomMargin + Math.round(child.getTranslationY());
            int bottom = top + divider.getIntrinsicHeight();
            
            divider.setAlpha(Math.round(child.getAlpha() * 255));
            divider.setBounds(0, top, parent.getWidth(), bottom);
            divider.draw(canvas);
        }
    }

    @Override
    public void seslOnDispatchDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            if (isSeparator(parent, child)) {
                roundedCorner.drawRoundedCorner(child, canvas);
            }
        }
    }

    private boolean isSeparator(@NonNull RecyclerView parent, @NonNull View child) {
        return parent.getChildViewHolder(child).getItemViewType() != contentViewType;
    }
        }

package com.oneui.fontviewer.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * FontItemDecoration - فئة لرسم الفواصل بين عناصر القائمة
 * تستخدم في قوائم الخطوط لإضافة خطوط فاصلة بين العناصر
 */
public class FontItemDecoration extends RecyclerView.ItemDecoration {
    
    private final Drawable divider;
    
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
        
        divider = context.getDrawable(dividerResId);
    }
    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);
        
        if (divider == null) {
            return;
        }

        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        int itemCount = adapter != null ? adapter.getItemCount() : 0;
        
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            // القوائم تتكون من: شريط (أول عنصر) + عناصر الخطوط + مساحة سفلية (آخر عنصر)
            // لذلك لا نرسم فاصلاً أسفل الشريط العلوي ولا أسفل آخر خط ولا أسفل المساحة السفلية
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION
                    || position < 1
                    || position > itemCount - 3) {
                continue;
            }

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            
            int top = child.getBottom() + params.bottomMargin;
            int bottom = top + divider.getIntrinsicHeight();
            
            divider.setBounds(parent.getLeft(), top, parent.getRight(), bottom);
            divider.draw(canvas);
        }
    }
}

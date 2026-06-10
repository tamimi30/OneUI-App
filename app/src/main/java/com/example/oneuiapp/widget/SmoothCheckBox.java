package com.example.oneuiapp.widget;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatCheckBox;
import java.lang.reflect.Field;

public class SmoothCheckBox extends AppCompatCheckBox {

    public SmoothCheckBox(Context context) {
        super(context);
    }

    public SmoothCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SmoothCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * دالة مخصصة لتحديث الحالة برمجياً 
     * مع إجبار محرك الرسوميات على تخطي الأنيميشن الانتقالي ومنع الوميض.
     */
    public void setCheckedSilent(boolean checked) {
        OnCheckedChangeListener currentListener = null;
        try {
            // فك المستمع مؤقتاً لتجنب التداخل
            Field listenerField = android.widget.CompoundButton.class.getDeclaredField("mOnCheckedChangeListener");
            listenerField.setAccessible(true);
            currentListener = (OnCheckedChangeListener) listenerField.get(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        setOnCheckedChangeListener(null);
        
        // تغيير الحالة
        setChecked(checked);
        
        // إنهاء الأنيميشن في نفس اللحظة (سر One UI 8)
        jumpDrawablesToCurrentState();
        if (getButtonDrawable() != null) {
            getButtonDrawable().jumpToCurrentState();
        }

        // إعادة المستمع الأصلي
        setOnCheckedChangeListener(currentListener);
    }
}

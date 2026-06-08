package com.example.oneuiapp.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckBox;
import com.example.oneuiapp.R;

public class SelectableLinearLayout extends LinearLayout {

    public SelectableLinearLayout(Context context) {
        super(context);
        init(context);
    }

    public SelectableLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SelectableLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // 1. إنشاء الشيك بوكس برمجياً (الحل السحري لمشكلة الأنيميشن في اللغة العربية)
        CheckBox checkBox = new AppCompatCheckBox(context);
        
        // 2. إعطاؤه نفس الـ ID القديم لكي تعمل ملفات الـ ViewHolders الموجودة لديك بدون أي تعديل!
        checkBox.setId(R.id.checkbox);
        
        // 3. ضبط المقاسات والهوامش
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        
        float density = context.getResources().getDisplayMetrics().density;
        // 17dp كما في تصميمك الأصلي
        lp.setMarginStart((int) (17 * density)); 
        
        checkBox.setLayoutParams(lp);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        checkBox.setVisibility(GONE);
        checkBox.setBackground(null); // لمنع تأثير التموج المزدوج
        
        // 4. إضافته كأول عنصر
        addView(checkBox, 0);
    }
}

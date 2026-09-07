package com.oneui.fontviewer.widget; // ★ استبدل هذا السطر باسم البكدج الخاص بمشروعك ★

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatSpinner;
import java.lang.reflect.Field;

public class ScrollableSpinner extends AppCompatSpinner {

    public ScrollableSpinner(Context context) {
        super(context);
        disableForwardingListener();
    }

    public ScrollableSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        disableForwardingListener();
    }

    public ScrollableSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        disableForwardingListener();
    }

    public ScrollableSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
        super(context, attrs, defStyleAttr, mode);
        disableForwardingListener();
    }

    // هذه الدالة تقوم بالبحث عن المتغير الخاص بتعطيل اللمس وتعطيله بالقوة
    private void disableForwardingListener() {
        try {
            // ابحث عن المتغير المخفي في الكلاس الأب
            Field field = AppCompatSpinner.class.getDeclaredField("mForwardingListener");
            // اكسر الحماية واجعله قابلاً للتعديل
            field.setAccessible(true);
            // اجعل قيمته فارغة (null) لكي لا يعمل
            field.set(this, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

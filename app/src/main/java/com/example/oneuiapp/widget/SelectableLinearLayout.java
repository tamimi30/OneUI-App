package com.example.oneuiapp.widget;

import android.animation.LayoutTransition;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import androidx.appcompat.widget.AppCompatCheckBox;
import com.example.oneuiapp.R;

public class SelectableLinearLayout extends LinearLayout {
    private CheckBox checkBox;
    private boolean isSelectionMode = false;

    public SelectableLinearLayout(Context context) { super(context); init(context); }
    public SelectableLinearLayout(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public SelectableLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(context); }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        
        // تفعيل حركة الانزلاق السلسة لهذا العنصر فقط
        setLayoutTransition(new LayoutTransition());

        // بناء الـ CheckBox برمجياً
        checkBox = new AppCompatCheckBox(context);
        checkBox.setId(R.id.checkbox);
        
        int marginStart = (int) (17 * getResources().getDisplayMetrics().density);
        int marginEnd = (int) (14 * getResources().getDisplayMetrics().density);
        
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        
        checkBox.setLayoutParams(lp);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        checkBox.setLongClickable(false);
        checkBox.setBackground(null);
        checkBox.setVisibility(View.GONE);
        
        addView(checkBox, 0);
    }

    public void setSelectionMode(boolean active) {
        if (isSelectionMode == active) return;
        this.isSelectionMode = active;
        if (checkBox != null) checkBox.setVisibility(active ? View.VISIBLE : View.GONE);
    }
    
    public void setChecked(boolean checked) {
        if (checkBox != null) checkBox.setChecked(checked);
    }
}

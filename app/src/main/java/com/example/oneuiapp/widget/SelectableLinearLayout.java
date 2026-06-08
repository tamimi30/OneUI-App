package com.example.oneuiapp.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckBox;
import com.example.oneuiapp.R;

public class SelectableLinearLayout extends LinearLayout {

    private AppCompatCheckBox checkBox;
    private ValueAnimator animator;
    private int targetWidth;
    private int startMargin;

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
        // 1. إيقاف أنيميشن النظام الافتراضي (الذي يسبب القفز والتشوه في اللغة العربية)
        setLayoutTransition(null);

        float density = context.getResources().getDisplayMetrics().density;
        targetWidth = (int) (24 * density); 
        startMargin = (int) (17 * density);

        // 2. إنشاء الشيك بوكس برمجياً واعتراض أمر النظام لإظهاره أو إخفائه
        checkBox = new AppCompatCheckBox(context) {
            private boolean isAnimating = false;

            @Override
            public void setVisibility(int visibility) {
                // إذا طُلب الإظهار وهو مخفي
                if (visibility == VISIBLE && getAlpha() < 1f) {
                    super.setVisibility(VISIBLE);
                    animateCheckBox(true);
                } 
                // إذا طُلب الإخفاء وهو ظاهر
                else if (visibility == GONE && getAlpha() > 0f) {
                    animateCheckBox(false); 
                } 
                // الحالات الأخرى العادية
                else if (!isAnimating) {
                    super.setVisibility(visibility);
                }
            }
            
            // 3. الأنيميشن الانسيابي المخصص بدلاً من أنيميشن النظام
            private void animateCheckBox(boolean show) {
                isAnimating = true;
                if (animator != null && animator.isRunning()) animator.cancel();
                
                float startVal = getAlpha();
                float endVal = show ? 1f : 0f;
                
                animator = ValueAnimator.ofFloat(startVal, endVal);
                animator.setDuration(250); 
                animator.addUpdateListener(animation -> {
                    float fraction = (float) animation.getAnimatedValue();
                    LayoutParams lp = (LayoutParams) getLayoutParams();
                    
                    // تحريك العرض والهامش تدريجياً لفتح مساحة للنص بسلاسة
                    lp.width = (int) (targetWidth * fraction);
                    lp.setMarginStart((int) (startMargin * fraction));
                    setLayoutParams(lp);
                    setAlpha(fraction);
                    
                    if (!show && fraction == 0f) {
                        isAnimating = false;
                        SelectableLinearLayout.this.post(() -> super.setVisibility(GONE));
                    }
                });
                animator.start();
            }
        };

        checkBox.setId(R.id.checkbox); // إبقاء نفس الـ ID القديم لتعمل ملفاتك بدون تعديل
        
        // يبدأ مخفياً (عرض وهامش = 0)
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMarginStart(0);
        
        checkBox.setLayoutParams(lp);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        checkBox.setAlpha(0f);
        checkBox.setBackground(null);
        
        addView(checkBox, 0);
    }
}

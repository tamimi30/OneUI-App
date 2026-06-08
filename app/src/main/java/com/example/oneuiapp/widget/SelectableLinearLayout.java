package com.example.oneuiapp.widget; // تأكد من أن مسار البكج يتطابق مع مشروعك

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewParent;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.recyclerview.widget.RecyclerView;

public class SelectableLinearLayout extends LinearLayout {

    private AppCompatCheckBox checkBox;
    private ValueAnimator animator;
    private int startMargin;
    private int checkWidth;

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
        setLayoutTransition(null);
        // منع قص أطراف العناصر أثناء حركتها
        setClipChildren(false); 
        setClipToPadding(false);

        float density = context.getResources().getDisplayMetrics().density;
        startMargin = (int) (17 * density);

        checkBox = new AppCompatCheckBox(context) {
            private boolean isAnimating = false;

            @Override
            public void setVisibility(int visibility) {
                // الحل السحري لمشكلة السكرول: إذا كانت القائمة تتحرك، أوقف الأنيميشن فوراً!
                if (isRecyclerViewScrolling()) {
                    if (animator != null) animator.cancel();
                    isAnimating = false;
                    if (visibility == VISIBLE) {
                        applyFraction(1f);
                        super.setVisibility(VISIBLE);
                    } else {
                        applyFraction(0f);
                        super.setVisibility(GONE);
                    }
                    return;
                }

                // الحالات العادية عند الضغط على تحديد (مع أنيميشن)
                if (visibility == VISIBLE && getAlpha() < 1f) {
                    super.setVisibility(VISIBLE);
                    animateCheckBox(true);
                } else if (visibility == GONE && getAlpha() > 0f) {
                    animateCheckBox(false);
                } else if (!isAnimating) {
                    super.setVisibility(visibility);
                }
            }

            private void animateCheckBox(boolean show) {
                isAnimating = true;
                if (animator != null && animator.isRunning()) animator.cancel();

                float startVal = getAlpha();
                float endVal = show ? 1f : 0f;

                animator = ValueAnimator.ofFloat(startVal, endVal);
                animator.setDuration(250);
                animator.addUpdateListener(animation -> {
                    float fraction = (float) animation.getAnimatedValue();
                    applyFraction(fraction);

                    if (!show && fraction == 0f) {
                        isAnimating = false;
                        SelectableLinearLayout.this.post(() -> super.setVisibility(GONE));
                    }
                    if (show && fraction == 1f) {
                        isAnimating = false;
                    }
                });
                animator.start();
            }

            // تطبيق الحركة بدون المساس بعرض (Width) العنصر لمنع القص
            private void applyFraction(float fraction) {
                setAlpha(fraction);
                LayoutParams lp = (LayoutParams) getLayoutParams();
                
                // تحريك عنصر الاختيار إلى الداخل والخارج باستخدام الهوامش
                lp.setMarginStart((int) (startMargin * fraction));
                int offset = (int) (checkWidth * (1f - fraction));
                lp.setMarginEnd(-offset); // الهامش السالب هو الذي يفتح المساحة دون تضييق العنصر
                
                setLayoutParams(lp);
            }
        };

        // قياس العرض الحقيقي للـ CheckBox لكي نزيح العناصر بناءً عليه
        checkBox.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        checkWidth = checkBox.getMeasuredWidth();
        if (checkWidth == 0) checkWidth = (int) (32 * density); // رقم افتراضي كخطة بديلة

        // إبقاء الـ ID متطابقاً مع ملفاتك
        // استبدل R.id.checkbox بالمسار الصحيح إذا ظهر لك خطأ هنا
        checkBox.setId(dev.oneuiproject.oneui.R.id.checkbox);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        
        // نقطة البداية (مخفي تماماً ومسحوب للخلف)
        lp.setMarginStart(0);
        lp.setMarginEnd(-checkWidth);
        
        checkBox.setLayoutParams(lp);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        checkBox.setAlpha(0f);
        checkBox.setBackground(null);

        addView(checkBox, 0);
    }

    // دالة تقوم بفحص إذا كان هذا العنصر موجود داخل RecyclerView وهي في حالة سكرول حالياً
    private boolean isRecyclerViewScrolling() {
        ViewParent parent = getParent();
        while (parent != null) {
            if (parent instanceof RecyclerView) {
                int state = ((RecyclerView) parent).getScrollState();
                return state != RecyclerView.SCROLL_STATE_IDLE;
            }
            parent = parent.getParent();
        }
        return false;
    }
}

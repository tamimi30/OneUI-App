package com.example.oneuiapp.widget;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

public class SelectionAnimatorHelper {

    public static void animateSelection(final View checkBox, boolean isSelectionMode) {
        if (checkBox == null) return;

        // إيقاف أي أنيميشن يعمل حالياً لمنع التداخل والوميض
        if (checkBox.getTag() instanceof ValueAnimator) {
            ((ValueAnimator) checkBox.getTag()).cancel();
        }

        final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) checkBox.getLayoutParams();
        
        // حساب المسافات بدقة لتكون سلسة
        final int hiddenMargin = (int) (-40 * checkBox.getResources().getDisplayMetrics().density); // مسافة الإخفاء
        final int visibleMargin = (int) (-4 * checkBox.getResources().getDisplayMetrics().density); // المسافة الطبيعية الموجودة في كودك

        // إظهار العنصر فوراً إذا كنا في وضع التحديد لنبدأ تحريكه
        if (isSelectionMode) {
            checkBox.setVisibility(View.VISIBLE);
        }

        float startFraction = isSelectionMode ? 0f : 1f;
        float endFraction = isSelectionMode ? 1f : 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(startFraction, endFraction);
        animator.setDuration(250); // سرعة متناسقة مع واجهة One UI
        animator.setInterpolator(new PathInterpolator(0.22f, 0.25f, 0f, 1f)); // منحنى حركة سامسونج الرسمي

        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();

            // 1. تغيير الشفافية بسلاسة
            checkBox.setAlpha(fraction);

            // 2. تحريك العنصر عن طريق الهوامش (MarginStart يفهم العربي والانجليزي تلقائياً!)
            params.setMarginStart((int) (hiddenMargin + ((visibleMargin - hiddenMargin) * fraction)));
            checkBox.setLayoutParams(params);

            // 3. الإخفاء التام من الشاشة عند انتهاء الإلغاء
            if (fraction == 0f && !isSelectionMode) {
                checkBox.setVisibility(View.GONE);
            }
        });

        checkBox.setTag(animator);
        animator.start();
    }
}

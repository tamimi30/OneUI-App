package com.example.oneuiapp.widget;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

public class SelectionAnimatorHelper {

    public static void animateSelection(final View checkBox, final View mainContent, boolean isSelectionMode) {
        if (checkBox == null || mainContent == null) return;

        // إيقاف أي أنيميشن سابق لمنع التداخل
        if (checkBox.getTag() instanceof ValueAnimator) {
            ((ValueAnimator) checkBox.getTag()).cancel();
        }

        // 1. حساب مسافة الانزلاق المطلوبة (تُعادل حجم الـ Checkbox تقريباً)
        final float offsetDistance = 40f * checkBox.getResources().getDisplayMetrics().density;

        // 2. فحص لغة الجهاز لتحديد اتجاه الانزلاق
        boolean isRtl = mainContent.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        // إذا عربي: النصوص تنزلق لليسار (سالب) | إذا إنجليزي: النصوص تنزلق لليمين (موجب)
        final float translationTarget = isRtl ? -offsetDistance : offsetDistance;

        if (isSelectionMode) {
            checkBox.setVisibility(View.VISIBLE);
        }

        float startFraction = isSelectionMode ? 0f : 1f;
        float endFraction = isSelectionMode ? 1f : 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(startFraction, endFraction);
        animator.setDuration(250);
        animator.setInterpolator(new PathInterpolator(0.22f, 0.25f, 0f, 1f));

        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();

            // ★ 1. الـ CheckBox يظهر ويختفي فقط في مكانه (لا يتحرك) ★
            checkBox.setAlpha(fraction);

            // ★ 2. المحتوى (النصوص) هو الذي ينزلق للجانب ★
            mainContent.setTranslationX(translationTarget * fraction);

            // 3. إخفاء الـ CheckBox تماماً عند الإلغاء
            if (fraction == 0f && !isSelectionMode) {
                checkBox.setVisibility(View.GONE);
            }
        });

        checkBox.setTag(animator);
        animator.start();
    }
}

package com.example.oneuiapp.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

public class SelectionAnimatorHelper {

    public static void animateSelection(final View checkBox, final View mainContent, boolean isSelectionMode) {
        if (checkBox == null || mainContent == null) return;

        // إيقاف أي أنيميشن جاري لمنع التداخل
        if (mainContent.getTag() instanceof ValueAnimator) {
            ((ValueAnimator) mainContent.getTag()).cancel();
        }

        // 1. حل مشكلة المسافة الضخمة: تقليل الرقم إلى 32 ليكون مناسباً لحجم علامة الصح
        final float distance = 32f * checkBox.getResources().getDisplayMetrics().density;

        // 2. فحص لغة الجهاز
        boolean isRtl = mainContent.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        // تحديد مسار الإزاحة: النصوص تتحرك لليسار في العربي (سالب) ولليمين في الإنجليزي (موجب)
        final float targetTranslation = isRtl ? -distance : distance;

        // قراءة الموقع الحالي للنص لمنع أي قفزات مفاجئة إذا تم الضغط بسرعة
        float startTranslation = mainContent.getTranslationX();
        float endTranslation = isSelectionMode ? targetTranslation : 0f;

        if (isSelectionMode) {
            // إظهار الـ CheckBox ونترك للنظام تطبيق أنيميشن الظهور الافتراضي الخاص به دون تدخل منا
            checkBox.setVisibility(View.VISIBLE);
        }

        ValueAnimator animator = ValueAnimator.ofFloat(startTranslation, endTranslation);
        animator.setDuration(250);
        animator.setInterpolator(new PathInterpolator(0.22f, 0.25f, 0f, 1f));

        animator.addUpdateListener(animation -> {
            // 3. حل مشكلة اللاق: استخدام translationX ناعم جداً وسلس على المعالج
            mainContent.setTranslationX((float) animation.getAnimatedValue());
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isSelectionMode) {
                    // عند الخروج من وضع التحديد، نخفي الـ CheckBox بالوضع الافتراضي ونصفر المكان
                    checkBox.setVisibility(View.GONE);
                    mainContent.setTranslationX(0f);
                }
            }
        });

        mainContent.setTag(animator);
        animator.start();
    }
}

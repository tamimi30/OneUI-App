package com.oneui.fontviewer.utils;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.animation.MotionSpec;
import com.oneui.fontviewer.R;

/**
 * يعيد بناء حركة ظهور زر الـ FAB (opacity + scale + iconScale) يدويًا، لتطبيقها على أي View
 * لا يملك showMotionSpec خاصًا به (مثل MaterialCardView المستخدم في format_bar)، بحيث تبدو
 * حركة ظهوره مطابقة تمامًا لحركة ظهور FloatingActionButton القياسية.
 */
public class FormatBarAnimator {

    // كلاس أدوات ساكن فقط، لا داعي لإنشاء نسخة منه
    private FormatBarAnimator() {}

    /**
     * يشغّل حركة ظهور مطابقة لزر الـ FAB على container (مثل بطاقة format_bar)، مع حركة
     * iconScale متأخرة ومنفصلة على أيقونة أو أكثر بداخله (مثل أيقونتي bold/italic).
     *
     * ملاحظة إصلاح: كنا نستخدم spec.getAnimator(name, target, property)، لكن هذه الدالة
     * تعتمد على قيم from/to معرّفة داخل ملف الـ motion spec نفسه، وهي غير موجودة في
     * mtrl_fab_show_motion_spec.xml (الذي يحتوي فقط على توقيت الحركة: duration/interpolator).
     * لذلك كان format_bar يبقى عند alpha = 0 و scale = 0 بشكل دائم (أي غير ظاهر) رغم أن
     * visibility تصبح VISIBLE، فيستمر باستقبال لمسات المستخدم رغم عدم ظهوره.
     * الحل: ننشئ الأنيميترز يدويًا بقيمة from/to صريحة (بنفس أسلوب
     * FloatingActionButtonImpl.createAnimator())، ونطبّق عليها فقط توقيت الحركة
     * (spec.getTiming) القادم من ملف الـ motion spec.
     *
     * ملاحظة إضافية (مطابقة سلوك ظهور أيقونة الـ FAB):
     * في FloatingActionButtonImpl.createAnimator() هناك أنيميشن مستقل باسم "iconScale" يُطبَّق
     * فقط على مصفوفة رسم الأيقونة داخل الـ FAB (وليس على جسم الزر كاملاً)، وتوقيته
     * startOffset=90 / duration=240، بينما أنيميشن "scale" الخاص بجسم الزر نفسه يبدأ فوراً
     * (startOffset=0) ومدته 330. بما أن الأيقونة المرسومة هي حاصل ضرب (تراكب) بين تحويل الزر
     * الأب وتحويل الأيقونة الخاص بها، فإنها تبقى بحجم صفر (غير ظاهرة) خلال أول 90ms حتى لو كان
     * الزر نفسه قد بدأ بالتكبّر فعلاً، ثم تلحق بالتكبير لتصل الحجم الكامل في نفس لحظة انتهاء
     * أنيميشن الزر (330ms). هذا هو ما يجعل أيقونة الـ FAB "تظهر بعد الزر بقليل".
     *
     * لمحاكاة هذا هنا: نطبّق أنيميشن "scale" على container نفسه بدءًا من الصفر، ونضيف أنيميشن
     * "iconScale" منفصلاً ومتأخرًا على كل عنصر في icons (وليس على حاوياتها)، فتُصبح هذه
     * العناصر أيضًا بحجم صفر خلال أول 90ms بغض النظر عن نمو container نفسه، بفضل تراكب
     * (تضاعف) تحويلي القياس بين الأب والابن — تمامًا كما يحصل بين الزر ومصفوفة أيقونته في الـ FAB.
     *
     * @param context سياق لقراءة ملف mtrl_fab_show_motion_spec
     * @param container العنصر الذي يمثل جسم الشريط/البطاقة (مثل format_bar)
     * @param icons الأيقونات الداخلية التي يجب أن تظهر بنفس تأخير أيقونة الـ FAB (يمكن ألا تُمرَّر أي منها)
     */
    public static void showWithFabMotion(Context context, View container, View... icons) {
        container.setVisibility(View.VISIBLE);
        container.setAlpha(0f);
        container.setScaleX(0f);
        container.setScaleY(0f);

        // تصفير حجم الأيقونات قبل بدء الأنيميشن: أنيميشن iconScale أدناه يبدأ متأخراً
        // (startOffset=90) عن أنيميشن scale الخاص بـ container (startOffset=0)، فيجب أن تكون
        // بحجم صفر بمجرد ظهور container، تمامًا كأيقونة الـ FAB.
        for (View icon : icons) {
            if (icon != null) {
                icon.setScaleX(0f);
                icon.setScaleY(0f);
            }
        }

        MotionSpec spec = MotionSpec.createFromResource(context, R.animator.mtrl_fab_show_motion_spec);
        List<Animator> animators = new ArrayList<>();

        ObjectAnimator opacityAnimator = ObjectAnimator.ofFloat(container, View.ALPHA, 1f);
        spec.getTiming("opacity").apply(opacityAnimator);
        animators.add(opacityAnimator);

        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(container, View.SCALE_X, 1f);
        spec.getTiming("scale").apply(scaleXAnimator);
        animators.add(scaleXAnimator);

        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(container, View.SCALE_Y, 1f);
        spec.getTiming("scale").apply(scaleYAnimator);
        animators.add(scaleYAnimator);

        // أنيميشن ظهور كل أيقونة، مطابق لتوقيت "iconScale" في الـ FAB (متأخر ومنفصل عن
        // أنيميشن container نفسه) — راجع الشرح في التعليق أعلى الدالة.
        for (View icon : icons) {
            if (icon == null) {
                continue;
            }
            ObjectAnimator iconScaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f);
            spec.getTiming("iconScale").apply(iconScaleX);
            animators.add(iconScaleX);

            ObjectAnimator iconScaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f);
            spec.getTiming("iconScale").apply(iconScaleY);
            animators.add(iconScaleY);
        }

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);
        set.start();
    }
}

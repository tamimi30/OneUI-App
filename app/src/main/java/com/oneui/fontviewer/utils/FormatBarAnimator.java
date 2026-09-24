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

public class FormatBarAnimator {

    private FormatBarAnimator() {}

    public static void showWithFabMotion(Context context, View container, View... icons) {
        container.setVisibility(View.VISIBLE);
        container.setAlpha(0f);
        container.setScaleX(0f);
        container.setScaleY(0f);

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

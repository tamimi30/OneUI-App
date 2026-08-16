package com.oneui.fontviewer.widget;

import androidx.recyclerview.widget.DefaultItemAnimator;

/**
 * نسخة DefaultItemAnimator المستخدمة في المشروع (Sesl) تعمل Override للـ getters
 * وترجع قيمًا ثابتة متجاهلة الـ setters الموروثة. هذا الـ subclass يعيد ربط
 * الـ getters بالـ setters حتى تعمل القيم المخصصة فعليًا.
 */
public class FixedDurationItemAnimator extends DefaultItemAnimator {

    private long mAddDuration = 200;
    private long mRemoveDuration = 100;
    private long mMoveDuration = 400;
    private long mChangeDuration = 400;

    @Override
    public long getAddDuration() {
        return mAddDuration;
    }

    @Override
    public void setAddDuration(long duration) {
        mAddDuration = duration;
    }

    @Override
    public long getRemoveDuration() {
        return mRemoveDuration;
    }

    @Override
    public void setRemoveDuration(long duration) {
        mRemoveDuration = duration;
    }

    @Override
    public long getMoveDuration() {
        return mMoveDuration;
    }

    @Override
    public void setMoveDuration(long duration) {
        mMoveDuration = duration;
    }

    @Override
    public long getChangeDuration() {
        return mChangeDuration;
    }

    @Override
    public void setChangeDuration(long duration) {
        mChangeDuration = duration;
    }
}

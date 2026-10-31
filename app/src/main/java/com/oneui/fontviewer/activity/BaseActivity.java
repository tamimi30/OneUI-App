package com.oneui.fontviewer.activity;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;
import com.oneui.fontviewer.utils.ThemeTransitionAnimator;

public class BaseActivity extends AppCompatActivity {

    // اللغة التي تعرضها هذه الشاشة حالياً (نقارنها عند تغيّر الإعدادات لنعرف إن تغيّرت اللغة)
    private String mDisplayedLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        Context wrappedContext = SettingsHelper.wrapContext(newBase);
        mDisplayedLanguage = wrappedContext.getResources().getConfiguration().getLocales().get(0).getLanguage();
        
        super.attachBaseContext(wrappedContext);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // تشغيل أنيميشن تغيير الثيم أو اللغة (إن كان هناك تغيير قيد التنفيذ)
        ThemeTransitionAnimator.playIfPending(this);
    }

    /**
     * الشاشات تعلن في الـ manifest أنها تتولى تغيير اللغة بنفسها (locale|layoutDirection)،
     * أي أن النظام لا يعيد بناءها (وهو ما كان يسبب الشاشة السوداء)، بل نعيد نحن بناءها
     * بنفس مسار تغيير الثيم مع الأنيميشن.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mDisplayedLanguage != null
                && !mDisplayedLanguage.equals(SettingsHelper.getLocale(this).getLanguage())) {
            ThemeTransitionAnimator.prepareForLanguageChangeIfNeeded(this);
            recreate();
        }
    }

}

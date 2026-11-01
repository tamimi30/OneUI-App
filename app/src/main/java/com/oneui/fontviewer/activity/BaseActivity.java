package com.oneui.fontviewer.activity;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;
import com.oneui.fontviewer.utils.ThemeTransitionAnimator;

public class BaseActivity extends AppCompatActivity {

    // اللغة التي تعرضها هذه الشاشة حالياً (نقارنها عند تغيّر الإعدادات لنعرف إن تغيّرت اللغة)
    private String mDisplayedLanguage;

    // نمنع تكرار recreate(): النظام قد يرسل onConfigurationChanged أكثر من مرة لنفس تغيير اللغة،
    // وكل recreate() إضافي يقلب اتجاه الواجهة مرة أخرى (RTL/LTR) فتظهر الحركة المتكررة.
    private boolean mLanguageRecreateRequested = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        Context wrappedContext = SettingsHelper.wrapContext(newBase);
        mDisplayedLanguage = wrappedContext.getResources().getConfiguration().getLocales().get(0).getLanguage();
        
        super.attachBaseContext(wrappedContext);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // ★ عند إعادة بناء الشاشة بعد تغيير اللغة يحتفظ النظام بنافذة الشاشة القديمة، فيبقى
        // اتجاه الواجهة (RTL/LTR) على اللغة السابقة بينما النصوص تتغير. لذلك نحدّثه يدوياً
        // ليطابق لغة هذه الشاشة.
        getWindow().getDecorView().setLayoutDirection(
                getResources().getConfiguration().getLayoutDirection());

        // تشغيل أنيميشن تغيير الثيم أو اللغة (إن كان هناك تغيير قيد التنفيذ)
        ThemeTransitionAnimator.playIfPending(this);
    }

    /**
     * SettingsActivity تعلن في الـ manifest أنها تتولى تغيير اللغة بنفسها (locale|layoutDirection)،
     * أي أن النظام لا يعيد بناءها (وهو ما كان يسبب الشاشة السوداء)، بل نعيد نحن بناءها
     * بنفس مسار تغيير الثيم مع الأنيميشن. أما بقية الشاشات فيعيد النظام بناءها كالسابق
     * (وهي غير ظاهرة أثناء وجودك في الإعدادات).
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mLanguageRecreateRequested || mDisplayedLanguage == null) {
            return;
        }

        String newLanguage = SettingsHelper.getLocale(this).getLanguage();
        if (!mDisplayedLanguage.equals(newLanguage)) {
            mLanguageRecreateRequested = true;
            Log.d("BaseActivity", "Language changed " + mDisplayedLanguage + " -> " + newLanguage
                    + " : recreating " + getClass().getSimpleName());
            ThemeTransitionAnimator.prepareForLanguageChangeIfNeeded(this);
            recreate();
        }
    }

}

package com.oneui.fontviewer.fragment.settings;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.BaseActivity;
import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;
import com.oneui.fontviewer.utils.TransitionUtils;

import dev.oneuiproject.oneui.layout.ToolbarLayout;

public class SettingsActivity extends BaseActivity {

    private ToolbarLayout mToolbarLayout;

    // اللغة التي تعرضها هذه الشاشة حالياً
    // نخزّن الوسم الكامل (مثل ar أو ar-MA) وليس رمز اللغة فقط، لأن مقارنة رمز اللغة وحده تعتبر
    // ar و ar-MA لغة واحدة فلا تُعاد بناء الشاشة عند التبديل بينهما
    private String mDisplayedLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDisplayedLanguage = getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        setContentView(R.layout.activity_settings);

        initToolbar();
        
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
        }
    }

    private void initToolbar() {
        mToolbarLayout = findViewById(R.id.toolbar_layout);
        if (mToolbarLayout != null) {
            mToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_action_bar_up_description));
            mToolbarLayout.setNavigationButtonOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }


    /**
     * locale|layoutDirection في configChanges (المانيفيست) يمنع النظام من إعادة بناء هذه الشاشة
     * بنفسه (وهو ما كان يسبب الشاشة السوداء)، فنعيد بناءها نحن عند تغيّر اللغة.
     * اتجاه الواجهة بعد إعادة البناء يضمنه android:layoutDirection="locale" في activity_settings.xml.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!SettingsHelper.getLocale(this).toLanguageTag().equals(mDisplayedLanguage)) {
            // بين وصول التغيير وإعادة بناء الشاشة (~150ms) تتفاعل عناصر الشاشة الحيّة القديمة معه
            // (عناوين الأقسام تقفز، الـ App bar ينطوي ويتوسع...)، فنغطيها بصورة اللقطة حتى تبدأ
            // الشاشة الجديدة.
            TransitionUtils.freezeScreen(this);
            recreate();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}

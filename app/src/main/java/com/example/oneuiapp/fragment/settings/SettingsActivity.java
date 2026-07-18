package com.example.oneuiapp.fragment.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fragment.settings.SettingsFragment;
import com.example.oneuiapp.activity.BaseActivity;

import dev.oneuiproject.oneui.layout.ToolbarLayout;

/**
 * SettingsActivity - شاشة الإعدادات المستقلة
 * 
 * تعرض SettingsFragment في شاشة منفصلة مع شريط أدوات علوي
 * يمكن الوصول إليها من أيقونة الإعدادات في درج التنقل في MainActivity
 */
public class SettingsActivity extends BaseActivity {

    private ToolbarLayout mToolbarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initToolbar();
        
        // إضافة SettingsFragment فقط عند الإنشاء الأول
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
            mToolbarLayout.setNavigationButtonTooltip(getString(R.string.navigate_up));
            mToolbarLayout.setNavigationButtonOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // معالجة زر الرجوع في شريط الأدوات
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // السماح بالرجوع بالطريقة العادية
        super.onBackPressed();
    }
}

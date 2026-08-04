package com.oneui.fontviewer.fragment.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;


import dev.oneuiproject.oneui.layout.ToolbarLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.BaseActivity;
import com.oneui.fontviewer.fragment.settings.SettingsFragment;

public class SettingsActivity extends BaseActivity {

    private ToolbarLayout mToolbarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

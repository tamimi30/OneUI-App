package com.oneui.fontviewer.activity;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        Context wrappedContext = SettingsHelper.wrapContext(newBase);

        super.attachBaseContext(wrappedContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}

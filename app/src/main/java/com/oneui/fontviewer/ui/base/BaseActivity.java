package com.oneui.fontviewer.ui.base;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.oneui.fontviewer.ui.utils.LocaleHelper;
import com.oneui.fontviewer.ui.utils.TransitionUtils;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        Context wrappedContext = LocaleHelper.wrapContext(newBase);
        
        super.attachBaseContext(wrappedContext);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        TransitionUtils.playIfPending(this);
    }

}

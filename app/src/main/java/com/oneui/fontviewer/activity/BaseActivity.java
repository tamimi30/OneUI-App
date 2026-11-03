package com.oneui.fontviewer.activity;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;
import com.oneui.fontviewer.utils.DiagLog;
import com.oneui.fontviewer.utils.TransitionUtils;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // تشخيص مؤقت: اللغة التي سيطلبها wrapContext وما كان عليه السياق الأصلي قبل اللف
        DiagLog.log("ATTACH", DiagLog.idOf(this) + " | before wrap: " + DiagLog.describe(newBase)
                + " | appLocales(LocaleManager)=" + DiagLog.appLocales(newBase)
                + " | wrapContext will use=" + SettingsHelper.getLocale(newBase).toLanguageTag());

        Context wrappedContext = SettingsHelper.wrapContext(newBase);

        super.attachBaseContext(wrappedContext);

        DiagLog.log("ATTACH", DiagLog.idOf(this) + " | after wrap+AppCompat: " + DiagLog.describe(this));
    }

    /**
     * تشخيص مؤقت: يسجّل كل طلب recreate() ومن استدعاه (AppCompat بسبب الثيم؟ أم كودنا؟).
     */
    @Override
    public void recreate() {
        DiagLog.log("RECREATE", DiagLog.idOf(this) + " | requested by: " + DiagLog.callers(14));
        super.recreate();
    }

    /**
     * تشخيص مؤقت: يسجّل إعدادات جديدة تصل إلى النشاط دون إعادة بنائه
     * (يحدث فقط للأنشطة التي تعالج التغيير في configChanges، مثل SettingsActivity للغة).
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        DiagLog.log("ACT_CONFIG", DiagLog.idOf(this) + " | received onConfigurationChanged: "
                + DiagLog.describe(newConfig) + " | activityShows: " + DiagLog.describe(this));
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        TransitionUtils.playIfPending(this);
    }

}
